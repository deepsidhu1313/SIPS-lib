# Array ops: a small op set as a compute substrate

`in.co.s13.sips.lib.array` — 15 operations, lazy, shape-checked, fused,
accelerator-routed, provably splittable, and serialisable. The measured case
is the `ArrayProgramming` sample: 39× on MLP inference, 349× on a gram matrix,
against the hand-written loops. New to the ideas here? Start with the
[primer](PRIMER.md) — this document assumes its §2–§4 and §7.

## The concept, for someone starting from zero

Most numerical programs, written plainly, are loops. The array-programming
observation (APL 1962, BLAS 1979, NumPy, ONNX) is that nearly all such loops
are instances of a *tiny* set of whole-matrix operations — multiply, add,
reduce — and that a program expressed in those operations can be optimised
once, centrally, in ways no hand-written loop gets: sent to an accelerator,
fused into fewer memory passes, cut across machines. The user writes *what*
to compute; the evaluator owns *how*.

```java
Expr predicted = Expr.input("x", n, d)
        .matmul(Expr.input("w1", d, h)).addRow(Expr.input("b1", 1, h)).relu()
        .matmul(Expr.input("w2", h, c)).addRow(Expr.input("b2", 1, c))
        .rowArgMax();
Mat answers = ArrayCompute.eval(predicted, inputs);
```

Nothing computes until `eval`. That laziness is load-bearing: the evaluator
needs the whole graph to do any of its three jobs.

## Job 1: route by physics

Matmuls — the one op whose arithmetic intensity grows with size ([primer
§2–3](PRIMER.md)) — go through `MatrixCompute` to whatever device the node
has. Memory-bound ops stay on the CPU, where the memory actually is.

## Job 2: fuse

Chains of pointwise ops run as one sweep producing one array; a reduction
consumes its chain directly, so `x.relu().scale(2).rowSum()` allocates the
output column and nothing else. Pass counts are pinned by tests.

## Job 3: prove distribution sound — two shapes

**Row-local** (`RowSplit.plan`): row i of the result depends only on row i of
the sharded data. Shards evaluate independently; results concatenate,
bit-identical to the whole. The rules: pointwise ops and row-reductions are
row-local; a matmul is row-local iff its right operand is replicated; a
transpose of sharded data is not.

**Partials-that-add** (`RowSplit.planReduce`): results that *sum over* the
rows — `colSum`, `sumAll`, and the gram/cross-product shape
`transpose(A).matmul(B)` — split into per-shard partials that add, because
matrix multiplication distributes over row blocks: `X'X = Σ Xs'Xs`. This is
the MapReduce pattern expressed as one algebraic identity. Soundness is
**linearity**: `scale()` commutes with addition and is hoisted above the
combine; `exp()` and `plus()` do not commute and are refused by name —
`plus(1)` per shard would add 1 once per shard.

The two entries name each other in their refusals. Everything either refuses
would have *run* and returned plausible wrong numbers from every shard.

One caveat, stated loudly: partials combine in a different grouping than the
whole evaluates, and float addition is not associative ([primer
§9](PRIMER.md)), so the two agree to tolerance, never bit for bit.

## Expressions travel

`Expr.toJson()` / `Expr.fromJson()` — the graph a researcher builds on a
laptop is exactly what every node evaluates. Parsing rebuilds through the same
constructors that checked the shapes the first time, so a hostile manifest
fails exactly as the equivalent source line would; unknown ops are refused by
name. v1 serialises the tree, so a shared subtree is duplicated on the wire
and evaluated once per use after the round trip — bind large shared subtrees
as inputs. (Pinned by a test that fails if the codec ever learns references.)

## The residency experiment — a prediction refuted

The first release of this document said device-resident intermediates should
wait for "a measured workload showing back-to-back GEMMs dominated by
transfer", predicting the win would shrink with n (transfer O(n²), compute
O(n³)). Built and measured — chain of four square GEMMs, per-call round trips
vs `MatrixCompute.chain` keeping the running product on the device:

```
   n     round trips   resident   ratio
  256        15 ms        7 ms    2.14x
  512        39 ms       22 ms    1.77x
 1024       188 ms       99 ms    1.90x
```

The ratio *holds near 2×* instead of vanishing. The prediction missed that
each per-call multiply ends in a blocking read — a full pipeline stall per
link — while the chain enqueues every link and synchronises once. The cost
model was missing synchronisation, not bytes. Kept in the [negative-results
register](PRIMER.md) as a worked example of why this framework measures.

## The op set, and why it is closed

INPUT, MATMUL, TRANSPOSE, ADD, SUB, MUL, ADD_ROW (broadcast is explicit —
silent broadcasting is how array bugs hide), RELU, EXP, SCALE, PLUS, ROW_SUM,
ROW_MAX, ROW_ARG_MAX, COL_SUM, SUM_ALL.

Sorting, graphs, strings and sparse computation gain nothing here and stay in
ordinary Java or WASM chunks. No op will be added to pretend otherwise. The
claim is not "any problem"; it is "the problems where performance matters
spend their time here."

## Still open

- Wiring serialised expressions into the node executor as a manifest `TYPE`,
  so a cluster job can be *only* an expression and its inputs.
- Fusing pointwise epilogues into the GEMM kernel itself (bias+relu inside
  the OpenCL kernel) — the next step the residency numbers motivate.

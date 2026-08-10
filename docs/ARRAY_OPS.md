# Array ops: a small op set as a compute substrate

`in.co.s13.sips.lib.array` — 13 operations, lazy, shape-checked, fused,
accelerator-routed, and provably row-splittable. The measured case for it is in
the `ArrayProgramming` sample: 34× on MLP inference, 431× on a gram matrix,
against the hand-written loops.

## Why an expression graph

`x.matmul(w).relu()` builds a plan and computes nothing. The evaluator needs
the whole graph for all three of its jobs:

1. **Route by physics.** Matmuls — O(n³) work on O(n²) data — go through
   `MatrixCompute` and land on whatever device the node has. Memory-bound ops
   (~1 FLOP per 12 bytes moved) stay on the CPU: PCIe moves bytes slower than
   DRAM, so shipping an elementwise add to a GPU costs more in transfer than
   the CPU would spend doing it.
2. **Fuse.** Chains of pointwise ops evaluate as one sweep producing one
   array. A reduction consumes its fused chain directly:
   `x.relu().scale(2).rowSum()` allocates the output column and nothing else.
   The pass counts are asserted by tests, not trusted from comments.
3. **Prove splittability.** `RowSplit.plan(expr, shardedInputs)` decides from
   the graph whether row shards compute exactly what the whole would — and
   refuses, naming the op, when they would not. Everything it refuses would
   have run and returned plausible wrong numbers.

## Shape errors happen at the line that wrote them

Shapes are checked at construction. A mismatch caught while building the graph
is a stack trace; the same mistake at evaluation time is a failed chunk on a
remote node. One input name is one matrix: the same name at two shapes is
refused when the graph is built.

## The op set, and why it is closed

INPUT, MATMUL, TRANSPOSE, ADD, SUB, MUL, ADD_ROW (explicit broadcast — silent
broadcasting is how array bugs hide), RELU, EXP, SCALE, PLUS, ROW_SUM,
ROW_MAX, ROW_ARG_MAX.

Sorting, graphs, strings and sparse computation gain nothing here and stay in
ordinary Java or WASM chunks. No op will be added to pretend otherwise.

## Not built yet, with reasons

- **Full reductions over sharded rows** (grand totals, column means): need a
  combine step on the master — the MapReduce shape — and belong with the stage
  machinery, not hidden inside a splitter. Refused with a sentence today.
- **Device-resident intermediates** across matmuls: the executor releases its
  buffers per call, and the ops between GEMMs are exactly the ones not worth
  the wire. Revisit when a measured workload shows back-to-back GEMMs
  dominated by transfer.
- **A serialised graph format** so a manifest can carry an expression to a
  worker. The analysis and evaluator are ready for it; the wire format should
  wait for the first job that needs one.

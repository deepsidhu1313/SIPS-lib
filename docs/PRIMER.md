# A primer: the ideas this framework is built on

Everything in SIPS follows from a small number of facts about computers and
arithmetic. None of them require prior background — this document teaches them
from zero, and every claim links to the code that implements it and the sample
that measures it. If you read nothing else first, read this.

A note on method: SIPS is a research framework. Where a design rests on a
number, the number was **measured on real hardware**, and the negative results
— the things that turned out *not* to work — are kept and documented with the
same care as the wins. Several of the most useful facts below were discovered
by an experiment disagreeing with its own design note.

---

## 1. Why distribute at all — and why it is not free

If one machine takes time *T* to do a job, N machines take *T/N* plus
**coordination**: shipping the work out, shipping results back, and waiting for
the slowest machine. SIPS's own measured model:

```
speedup ≈ N / (1 + N·C/(E·T))
```

where *C* is the per-chunk overhead and *E·T* the useful work per chunk. Read
it as a warning: as N grows, the coordination term grows with it, and a job
whose chunks are too small gets *slower* by being distributed. Every design
decision in this framework is some form of "make C smaller or make E·T
bigger."

Measured honestly in [DistributedTraining](ML_TRAINING.md): 5.75× on 8 workers
— the missing 2.25× *is* the coordination term.

## 2. Memory-bound vs compute-bound: the most important distinction

Every operation has an **arithmetic intensity**: how many arithmetic
operations it performs per byte it moves. This single number decides where an
operation should run.

- **Elementwise add**: reads 8 bytes, writes 4, does 1 addition. Intensity
  ≈ 1/12 FLOP per byte. The memory system, not the arithmetic unit, is the
  bottleneck — the op is **memory-bound**. Making the arithmetic faster (a
  GPU!) changes nothing; the bytes still have to move.
- **Matrix multiply** of two n×n matrices: moves O(n²) bytes, does O(n³)
  operations. Intensity grows *linearly with n* — at n=1024 it is hundreds of
  FLOPs per byte. The arithmetic is the bottleneck — **compute-bound** — and
  faster arithmetic pays directly.

Now add a bus. A discrete GPU sits across PCIe, which on this machine moves
~8–16 GB/s while DRAM moves ~40 GB/s. Shipping a memory-bound op to the GPU
means paying a *slower* memory system plus a round trip: it is a strict loss,
always. Shipping a big matmul pays enormously: measured **19.8×** at 1024³
([ACCELERATORS.md](ACCELERATORS.md)), and **431×** against a naive hand loop
in the [ArrayProgramming sample](ARRAY_OPS.md).

That is the whole routing policy of [ArrayCompute](ARRAY_OPS.md), and it is
two sentences: *matmuls go to the accelerator; everything memory-bound stays
on the CPU.*

## 3. Why matrix multiply is the king of operations

Not because it is common — because it is the **only basic operation whose
intensity grows with problem size**. Everything O(n²)-work-on-O(n²)-data hits
the memory wall; matmul alone escapes it. This is why:

- BLAS (1979) organised its levels around it (level 3 = matmul-like);
- GPUs, TPUs and NPUs are, to first order, matmul machines;
- the [array op set](ARRAY_OPS.md) treats it as the one op worth a device,
  and the k-means sample gets its win by *rewriting a distance loop as a
  matmul* — the identity `argmin ‖x−c‖² = argmax (2x·c − ‖c‖²)` turns a
  memory-bound loop into a compute-bound one. Choosing the right expression
  is itself an optimisation, often the biggest one.

## 4. Fusion: don't make three passes when one will do

`relu(x).scale(2).plus(1)` computed naively is three loops over the data and
two temporary arrays. For memory-bound ops the passes *are* the cost (§2), so
the fix is to compute `max(0,x[i])*2+1` in **one** loop with no temporaries.
That is fusion, the core trick of every ML compiler (XLA, TVM), and the
[array evaluator](ARRAY_OPS.md) does it by composing per-element functions.
The pass counts are pinned by tests — a fusion that silently stopped fusing
would be invisible in a correctness test.

## 5. Barriers and stragglers: the tax on synchronisation

A **barrier** is a point where nobody proceeds until everybody arrives. A
round of federated averaging has one per round; a stage boundary in a
[task graph](TASK_GRAPHS.md) is one. The cost of a barrier is set by the
**slowest** participant — so variance, not mean speed, is what hurts. Three
consequences, all built and measured:

- **Weight shards by steadiness, not just speed**: `mean/(1+cv)` in
  [ShardPlan](ML_TRAINING.md). A sibling project measured a busy phone's p99
  at 122 ms against a 19 ms p50 — the tail is the barrier's real price.
- **Re-issue late shards** ([SpeculativeRound](ML_TRAINING.md)): a phone in a
  pocket must not hold the round open. First answer wins; accepting a shard
  twice would silently overweight its data.
- **Or remove the barrier**: [ensembles and population training](ML_TRAINING.md)
  train with zero or scalar-only synchronisation. The
  [ZeroBarrierTraining sample](../../SIPS-samples/ZeroBarrierTraining/Readme.md)
  prices the trade instead of asserting it: FedAvg wins loss 0.0695 vs 0.1501,
  loses barriers 8:1, and the accuracies differ by 0.2 points.

## 6. Three ways to split a model, and the two that don't survive Ethernet

- **Data parallel**: every worker holds the whole model, inputs are split.
  Nothing crosses the wire mid-computation. This is the split SIPS builds.
- **Tensor parallel**: every matmul is split; needs an all-reduce per layer at
  microsecond latency. A LAN's millisecond-scale round trips, ×24 layers,
  ×every token, is a budget nothing meets. NVLink exists because of this.
- **Pipeline parallel**: split by layer; pays only when the model does not fit
  in one machine's memory.

The rule generalises: **communication frequency must match link latency.** A
phone on WiFi (122 ms p99) can carry a *round* of training (seconds–minutes of
work per message) and never a *layer* (µs of work per message).
[MOBILE_WORKERS.md](MOBILE_WORKERS.md) is this rule applied.

## 7. Distribution soundness is a property of the *expression*

Cutting data into row shards is only correct if row i of the result depends
only on row i of the data. That is decidable from the computation graph — 
elementwise ops are row-local, a matmul is row-local iff its right operand is
replicated, a transpose of sharded data is not row-local at all — and
[RowSplit](ARRAY_OPS.md) decides it *before any data moves*. Results that
**sum over** rows (a gram matrix, a column total) split differently: each
shard computes a partial and the partials add, because matmul distributes
over row blocks (`X'X = Σ Xs'Xs`). That is MapReduce, in one identity.

The refusals matter more than the acceptances: everything RowSplit refuses
would have *run* and returned plausible wrong numbers from every shard.

## 8. When workers lie: breakdown points and robust statistics

The mean has **breakdown point zero**: one arbitrarily bad value moves it
arbitrarily far. On machines you own that is fine; the moment strangers'
devices join, one poisoned model ruins every round it touches — measured in
the ZeroBarrierTraining sample: one liar in eight took the loss from 0.07 to
**9.96**, and nothing about the run looked wrong.

The **trimmed mean** (drop the k largest and smallest per coordinate, average
the rest — Yin et al. 2018) tolerates k liars at a small cost in noise.
Trimming must be *per coordinate*: a targeted attacker moves only the weights
they care about and would hide inside any whole-model bound. See
[RobustAverage](ML_TRAINING.md). Four workers is the smallest round that
tolerates one liar — worth knowing before asking phones for help.

## 9. Floating point: close enough is a design decision

Two facts with system-wide consequences:

- **Float addition is not associative.** `(a+b)+c ≠ a+(b+c)` in general, so
  the *grouping* of a sum is part of its answer. Partials combined across
  shards agree with the whole to tolerance, never bit-for-bit; results meant
  to be reproducible must fix their summation order (WeightAverage sorts by
  chunk number for exactly this reason). A sibling project measured grouping
  differences amplifying to percent scale through 24 layers.
- **Precision is a budget you can spend.** [Quantized](ML_TRAINING.md) carries
  weights at 1 byte instead of 4 — SGD never earned seven digits of precision,
  so inference tolerates the rounding (measured: 1.14% of predictions changed).
  Training does *not*: averaging accumulates the rounding error round after
  round, which is why the training path stays float32.

## 10. The negative-results register

Kept deliberately, because a refuted assumption is worth more than an
unexamined one:

| Assumed | Measured | Consequence |
|---|---|---|
| GPU ≥ CPU | GPU **4× slower** for llama decode on the same machine where GEMM is 19.8× faster | capability metadata carries measured throughput per workload, never a feature flag |
| Idle probes predict loaded links | busy endpoint ≈ 2× idle p50, p99 3–20× worse | links calibrated under load; variance-aware shard weights |
| Phones can join hot paths | 122 ms p99, struck ~21×/token | phones do batch rounds, never per-layer work |
| GEMM-chain residency win shrinks with n (our own javadoc) | holds at ~1.9–2.1× from 256³ to 1024³ | per-call blocking reads stall the pipeline — the cost model missed synchronisation, not bytes |
| A uniform "garbage" model poisons a mean | softmax is shift-invariant; the attack was a no-op | attack demonstrations must be asymmetric to demonstrate anything |

## Where to go next

| To learn about | Read | Runnable evidence |
|---|---|---|
| The op substrate, fusion, splitting | [ARRAY_OPS.md](ARRAY_OPS.md) | `SIPS-samples/ArrayProgramming` |
| Distributed training end to end | [ML_TRAINING.md](ML_TRAINING.md) | `DistributedTraining`, `ZeroBarrierTraining` |
| Batch inference and model shipping | [ML_TRAINING.md](ML_TRAINING.md) | `DistributedInference` |
| Portable sandboxed chunks | [WASM_TASKS.md](WASM_TASKS.md) | ABI conformance vectors |
| Multi-stage jobs and data flow | [TASK_GRAPHS.md](TASK_GRAPHS.md) | — |
| Version negotiation between nodes | [PROTOCOL.md](PROTOCOL.md) | — |
| GPUs, devices, energy | [ACCELERATORS.md](ACCELERATORS.md) | `ImageFilter`, kernel benches |
| Phones as workers | [MOBILE_WORKERS.md](MOBILE_WORKERS.md) | — |
| Schema evolution | [MIGRATIONS.md](MIGRATIONS.md) | — |

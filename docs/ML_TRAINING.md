# Distributed training on SIPS

What it takes to train a model across the cluster and actually beat one machine
— which paradigms fit this framework, which one to build first, and in what
order the missing pieces go in.

**Status: phases 0–2 built, with accelerator support.** Everything above a
phase number is analysis; the phases say what is implemented.

## How training is distributed, and what each way costs

The field has settled on a small number of shapes. What separates them is not
cleverness but **how often the workers must talk**:

| Paradigm | Workers exchange | How often | Canonical work |
|---|---|---|---|
| Hyperparameter search | nothing | never | grid/random search, PBT |
| Ensemble training | nothing | never | bagging |
| **Local SGD / FedAvg** | model weights | every R local epochs | Zinkevich 2010 (one-shot averaging), McMahan 2017 (FedAvg) |
| Synchronous data parallel | gradients | **every minibatch** | Horovod-style all-reduce |
| Async parameter server | gradients | every minibatch, unsynchronised | Li 2014, Hogwild |
| Model/pipeline parallel | activations | every *layer*, every batch | GPipe, Megatron |

The right-hand rows need collectives over long-lived processes with microsecond
latencies. The left-hand rows need what a batch framework already has: hand out
work, wait, collect.

## What SIPS measurably is

The constants that decide which row SIPS can occupy, all measured in this
repository:

- **A Java chunk costs hundreds of milliseconds to start** (javac + JVM via
  Ant). A WASM chunk costs single-digit ms to parse once, then **<1 ms per
  call**.
- **Results under 256 KB ride home inside the finish message**
  (`ChunkResults.MAX_INLINE_BYTES`); larger ones take the file-server path.
- **Communication is per chunk boundary, not per step.** There is no channel
  between running chunks and no collective operation.
- **Stages sequence with a barrier**: `JobSequencer` releases a stage the moment
  its dependencies finish. That is exactly the bulk-synchronous-parallel shape.
- **WASM floats are bit-identical across nodes** (IEEE-754 pinned, no FMA
  contraction) where the OpenCL kernels had to retreat to integers. For
  reproducible training across heterogeneous machines, that is the deterministic
  path.

## The verdict

**Per-minibatch data parallelism does not fit and should not be built.** A
minibatch on a small model takes microseconds to milliseconds; a chunk boundary
costs milliseconds to seconds. Putting a chunk boundary inside the training loop
puts the framework's largest cost at the algorithm's highest frequency. Systems
that do this well (NCCL, Horovod) are long-lived-process collectives — a
different animal, and bolting one onto a batch framework produces a bad copy of
both.

**Local SGD / FedAvg fits exactly.** Each worker trains *E* full epochs on its
own shard — seconds to minutes of compute — then ships back only the weights,
which for models up to ~65k parameters fit the inline-result path. The
synchronisation point is a stage barrier, which SIPS already has. The dataset
crosses the wire once; the weights cross once per round.

The speedup model, with SIPS's own constants: for N nodes, R rounds, E local
epochs, epoch time T over the full data, and per-round overhead C (chunk
startup + weight transfer + barrier):

```
T_single = R · E · T
T_dist   = R · (E · T/N + C)
speedup  ≈ N / (1 + N·C / (E·T))
```

So the boost is real **iff E·T ≫ N·C**. With Java chunks C ≈ 0.5–1 s, so a
shard-epoch must take several seconds — true for any dataset worth
distributing. Raising E is the algorithmic lever: FedAvg with E=2–5 halves to
fifths the number of synchronisations, at a well-studied cost in convergence
that is negligible for convex models and mild for small networks.

Also fitting today with **no new machinery**: hyperparameter sweeps and
ensembles — both are `parallelFor` over configurations, communication-free,
and the place a user should start.

## Gap analysis

What FedAvg needs against what exists:

| Need | Exists? |
|---|---|
| split data into shards, one per worker | `parallelFor` + `Chunk` scheduler ✔ |
| train rounds in order, workers in parallel within a round | `Job`/`Stage`/`JobSequencer` ✔ |
| serialise weights as bytes | **missing** → phase 0 |
| aggregate: weighted average of worker models | **missing** (the known "no result-merge primitive" gap) → phase 0 |
| express R rounds as a graph | expressible by hand; **builder missing** → phase 0 |
| move a stage's outputs to the next stage's inputs *on the cluster* | **missing** → phase 1 |
| keep shard i on the same node every round | `NearestData` points at it; **affinity missing** → phase 1 |
| stop when converged | `breakAfter` semantics fit ✔; wiring → phase 2 |

## The plan

**Phase 0 — the math and the graph (built, this commit).**
`in.co.s13.sips.lib.ml`: `Tensors` (float[] ⇄ little-endian bytes, matching
WASM linear memory so a WASM training kernel reads the same layout),
`WeightAverage` (weighted mean, double accumulation, inputs ordered by chunk
number so the result is byte-identical whatever order results arrive),
`FedAvgPlan` (unrolls R rounds into the existing `Job` DAG: train₁ → avg₁ →
train₂ → …). Plus a runnable sample that drives real training through the real
`JobRunner` and measures single-machine against parallel.

**Phase 1 — the cluster path (built).** `StageOutputs` gathers a finished
stage's results — collected by **shard**, not chunk number, because chunk numbers
run across the whole job so round two's worker 0 is chunk 8 — and places them as
the next stage's inputs, prefixed by producer so two producers cannot overwrite
each other's shard 0. `ShardAffinity` sends a shard back to the node that already
holds its data, swapping only within the nodes the scheduler chose so its balance
survives; a remembered node that has died is ignored rather than waited for.
`ChunkSpec` carries what the manifest cannot, because the manifest is one file
for every chunk: the slice, the input names, the output name, and the shard index
(distinct from the chunk number). A Java chunk now returns its declared output
inline exactly as a WASM one does, so a stage written either way behaves the same.

Still open in this phase: **benchmark-weighted shard sizes**, so a heterogeneous
cluster reaches each barrier together rather than waiting on the slowest node.

**Phase 2 — convergence and scale (built).** `TrainingRun` and `StopWhen`
replace a fixed round count, which is a guess in both directions: too few and
the model is undertrained, too many and the cluster exchanges weights that no
longer move — neither of which shows up as a failure. `StopWhen` is one method,
so a criterion the built-ins do not cover (a validation metric, a wall-clock or
energy budget from the power monitors) needs no change to the runner. Divergence
ends a run outright rather than waiting for the criterion, because nothing
recovers from a NaN and every further round is wasted cluster time. Every round
is kept, and the best loss is reported separately from the last — FedAvg can
overshoot, and reporting the final loss would understate a run that had already
found something better.

`ShardPlan` divides the data by benchmark score, closing the last phase‑1 gap. A
round ends when its slowest worker ends, so equal shards on unequal machines
waste the difference *every round*. FedAvg already weights each model by its
sample count, so unequal shards cost nothing in accuracy — which is what makes
this free. Ordinary loop scheduling cannot do this job: GSS and its relatives
balance by handing out more batches to whoever returns first, and here each
worker gets exactly one shard for the whole round, so the division has to be
right the first time.

Models over the 256 KB inline cap are fetched rather than carried. The inline
path is right for what a call returns — a thumbnail, a checksum — and wrong for
a model: anything with a hidden layer is megabytes, so without this the sample
works and a real network does not. A result too large stays in the sandbox that
produced it and the master asks for it by name, which means the worker does not
have to guess which of its outputs anyone wants and an uncollected result is
removed with the sandbox rather than accumulating somewhere central. Neither
end holds the whole thing in memory: the sender digests in one pass and streams
in another.

The transfer verifies length and checksum before handing the bytes back, and
every read has a timeout. Both are for the same reason: a truncated model is
still a well-formed float array, so averaging one produces a wrong answer
rather than an error, and a node that died between finishing its chunk and
being asked for the result would otherwise leave the master blocked with the
whole job behind it. This is protocol version 2 — a version 1 node does not
recognise the command and answers nothing at all, so `FETCHED_RESULTS` is
declared `BREAKS_ON_OLDER` rather than left to degrade into a stage that
averages whichever shards happened to arrive.

**Phase 3 — research surface.** WASM training kernels (the bit-identical float
story makes cross-node reproducibility a genuine differentiator). Gradient
compression before the wire. Stale-tolerant averaging for stragglers, starting
from the duplicate-chunk machinery that already exists.

## Accelerators

Training's inner loop is dense float GEMM, which is the one thing a GPU is
unambiguously for — unlike the image kernels, which are memory-bound and where a
GPU mostly waits on transfers.

`MatrixKernel` is the type a researcher implements: one operation written twice,
as OpenCL C and as Java. It is the float counterpart of `ImageKernel`, and the
contract differs in one important way. Image kernels must agree **bit for bit**,
because neighbouring tiles of one picture land on different devices and any
difference is a visible seam — which is what forces them to integers. Nothing
about training works that way: it is stochastic already, models are judged by
loss rather than bit equality, and a weight differing in its last mantissa bit is
indistinguishable from a different shuffle seed. So matrix kernels agree to a
**declared relative tolerance**, and `MatrixKernelConformanceTest` holds every
registered kernel to the number it declares, on every device the machine has.

Shipped: `gemm`, `gemm_tiled`, `gemm_at_b` (the backward pass's shape, without
materialising the transpose) and `softmax_rows`.

### Measured

macOS, Intel i7-9750H against an AMD Radeon Pro 5300M, square GEMM, best of five
after warmup:

| n | CPU | GPU (tiled) | speedup |
|---|---|---|---|
| 256 | 7 ms (4.8 GF/s) | 2 ms (16.8 GF/s) | 3.5× |
| 512 | 62 ms (4.3 GF/s) | 9 ms (29.8 GF/s) | 6.9× |
| 768 | 290 ms (3.1 GF/s) | 16 ms (56.6 GF/s) | 18.1× |
| 1024 | 674 ms (3.2 GF/s) | 34 ms (63.2 GF/s) | 19.8× |

Two things that measurement caught, both of which would have made the feature
decorative:

- **An executor per call is slower than the CPU.** Compiling a kernel costs tens
  of milliseconds against a fraction of one to run it. The first version built
  one per multiply and lost to the CPU at every size; executors are now cached
  per thread.
- **The naive kernel is only at parity.** Every work item re-reading whole rows
  from global memory leaves the device waiting on memory — about 4 GF/s, the same
  as the CPU. `gemm_tiled` stages tiles through local memory and is what produces
  the numbers above.

### Choosing a device

`MatrixCompute` prefers the tiled kernel, falls back to the plain one on a device
whose work-group limit will not take a 16×16 group, and to the CPU otherwise —
identical arithmetic in every case. That fallback is not hypothetical: on this
machine the OpenCL **CPU device refuses the group** and takes the plain kernel,
which the conformance run reports.

Small problems stay on the CPU regardless. Below roughly a million
multiply-accumulates the transfer costs more than the arithmetic saves, and a
kernel says how its work scales — a row-wise softmax scales with the row, not
with `k`, so a narrow one is never worth sending.

**Determinism:** a caller needing bit-reproducibility should use
`multiplyOnCpu`, or run the job as WebAssembly, where floats *are* pinned across
nodes.

## What this deliberately is not

- **Not a deep-learning framework.** No autograd, no GPU training. The GPU
  kernels in this repo are integer-only for cross-device determinism, and that
  is the wrong trade for training. SIPS's job is scheduling, sharding, movement
  and aggregation; the model math belongs to the job.
- **Not per-batch synchronous SGD**, for the cost reasons above. If a model is
  large enough to need that, it needs NCCL, not this.
- **Not verified on a live cluster.** Phase 0 is exercised end-to-end through
  the real `JobRunner` in-process, with cores standing in for nodes. The
  cluster run needs phase 1 and stands behind the same caveat as every other
  cluster-side feature in this repository.

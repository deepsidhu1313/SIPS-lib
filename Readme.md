# SIPS-lib

[![build](https://github.com/deepsidhu1313/SIPS-lib/actions/workflows/build.yml/badge.svg)](https://github.com/deepsidhu1313/SIPS-lib/actions/workflows/build.yml)

The library a SIPS program links against, plus the types shared by every module.

## Start here

**New to distributed computing, GPUs, or federated learning?**
[docs/PRIMER.md](docs/PRIMER.md) teaches every concept this framework rests on
from zero — arithmetic intensity, barriers and stragglers, robust averaging,
float associativity — with each claim linked to the code that implements it
and the sample that measures it. The feature docs below each open with a
short "from zero" preamble and assume the primer for the rest.

This is a research framework: designs rest on numbers measured on real
hardware, and the negative results — the ideas that measurement refuted,
including our own predictions — are documented with the same care as the wins
(see the primer's negative-results register).

## What is in here

| Package | Role |
|---|---|
| `in.co.s13.sips.lib` | The `SIPS` class user programs call: `parallelFor()`, `simulateSection()`, `saveObject()`, `resolveObject()` |
| `in.co.s13.sips.lib.common.datastructure` | `Node`, `SIPSTask`, `ParallelForLoop`, `IPAddress` and friends |
| `in.co.s13.sips.lib.accelerator` | Compute device discovery (OpenCL, CPU, probing stubs for CUDA/ROCm/Metal/Vulkan/NPU), `ImageKernel` and `MatrixKernel` |
| `in.co.s13.sips.lib.image` | `TileGrid` and `Tile`: two-dimensional decomposition with halo |
| `in.co.s13.sips.lib.loop` | `EarlyExit`: what `breakAll` and `breakAfter` mean across machines |
| `in.co.s13.sips.lib.wasm` | `WasmTask`, `WasmRunner`, `WasmHost` — a chunk as a precompiled module |
| `in.co.s13.sips.lib.job` | `Job`, `Stage`, `JobSequencer`, `JobRunner` — pipelines, not just loops |
| `in.co.s13.sips.lib.lambda` | `ClusterCall` — one function, some bytes, a policy deciding where it runs |
| `in.co.s13.sips.lib.manifest` | `TaskType`: how a manifest says a chunk is Java or WebAssembly |
| `in.co.s13.sips.lib.db` | `Migrator` and `SettingsMigrator` — schema and settings changes that reach existing installations |
| `in.co.s13.sips.lib.protocol` | `Protocol` — what a peer can be asked to do, and how it says so |
| `in.co.s13.sips.lib.ml` | `Tensors`, `Quantized`, `WeightAverage`, `RobustAverage`, `FedAvgPlan`, `TrainingRun`, `ShardPlan`, `Population`, `Ensemble`, `WarmModels` — distributed training and inference |
| `in.co.s13.sips.lib.array` | `Expr`, `ArrayCompute`, `RowSplit` — a lazy, fused, provably-splittable array op set ([docs](docs/ARRAY_OPS.md)) |
| `in.co.s13.sips.scheduler` | `Scheduler`, plus the two policy SPIs: `LoopPolicy` and `PlacementPolicy` |

## Build

```bash
./mvnw verify
```

Requires **JDK 21**.

## Documentation

- [WebAssembly chunks](docs/WASM_TASKS.md) — the host interface, and why a
  microsecond start changes what a scheduler can do
- [Task graphs](docs/TASK_GRAPHS.md) — pipelines, placement policies, and what
  each is measured to be worth
- [Migrations](docs/MIGRATIONS.md) — how a schema or settings change reaches a
  node that already exists
- [Wire protocol](docs/PROTOCOL.md) — what nodes of different versions may ask
  of each other
- [Distributed training](docs/ML_TRAINING.md) — which ML paradigms fit this
  framework, the speedup model, and the phased plan
- [Accelerators and image processing](docs/ACCELERATORS.md)
- [Parallel loops](../SIPS-Node/docs/PARALLEL_LOOPS.md) — `break`, `continue`, early exit
- [Architecture](../SIPS-Node/docs/ARCHITECTURE.md)

## Two things you can write in one method

A **loop policy** decides how big the next batch is:

```java
public long nextBatchSize(long remaining, int nodes, int round) {
    return Math.max(1, (long) Math.ceil((double) remaining / nodes));   // GSS
}
```

A **placement policy** decides which of several ready tasks goes where:

```java
public Optional<String> place(ReadyTask task, ClusterState cluster) {
    return cluster.nodes().stream().min(Comparator.comparingDouble(node ->
            Math.max(cluster.availableAt(node), task.readyAt()) + task.costOn(node)));
}
```

Both can be compared against the classics offline, without a cluster —
`Evaluator` for loops, `DagEvaluator` for graphs.

## Quick look at your hardware

```java
System.out.println(AcceleratorRegistry.describe());
System.out.println(AcceleratorRegistry.describeUnavailable());
```

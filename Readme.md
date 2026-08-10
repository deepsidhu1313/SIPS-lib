# SIPS-lib

[![build](https://github.com/deepsidhu1313/SIPS-lib/actions/workflows/build.yml/badge.svg)](https://github.com/deepsidhu1313/SIPS-lib/actions/workflows/build.yml)

The library a SIPS program links against, plus the types shared by every module.

## What is in here

| Package | Role |
|---|---|
| `in.co.s13.sips.lib` | The `SIPS` class user programs call: `parallelFor()`, `simulateSection()`, `saveObject()`, `resolveObject()` |
| `in.co.s13.sips.lib.common.datastructure` | `Node`, `SIPSTask`, `ParallelForLoop`, `IPAddress` and friends |
| `in.co.s13.sips.lib.accelerator` | Compute device discovery: OpenCL, CPU, and probing stubs for CUDA/ROCm/Metal/Vulkan/NPU |
| `in.co.s13.sips.lib.image` | `TileGrid` and `Tile`: two-dimensional decomposition with halo |
| `in.co.s13.sips.lib.loop` | `EarlyExit`: what `breakAll` and `breakAfter` mean across machines |
| `in.co.s13.sips.lib.wasm` | `WasmTask`, `WasmRunner`, `WasmHost` — a chunk as a precompiled module |
| `in.co.s13.sips.lib.job` | `Job`, `Stage`, `JobSequencer`, `JobRunner` — pipelines, not just loops |
| `in.co.s13.sips.lib.lambda` | `ClusterCall` — one function, some bytes, a policy deciding where it runs |
| `in.co.s13.sips.lib.manifest` | `TaskType`: how a manifest says a chunk is Java or WebAssembly |
| `in.co.s13.sips.lib.db` | `Migrator` and `SettingsMigrator` — schema and settings changes that reach existing installations |
| `in.co.s13.sips.lib.protocol` | `Protocol` — what a peer can be asked to do, and how it says so |
| `in.co.s13.sips.lib.ml` | `Tensors`, `WeightAverage`, `FedAvgPlan` — distributed training as rounds of a task graph |
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

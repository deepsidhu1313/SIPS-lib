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
| `in.co.s13.sips.scheduler` | The `Scheduler` interface that SIPS-Schedulers implements |

## Build

```bash
./mvnw verify
```

Requires **JDK 21**.

## Documentation

- [Accelerators and image processing](docs/ACCELERATORS.md)
- [Architecture](../SIPS-Node/docs/ARCHITECTURE.md)

## Quick look at your hardware

```java
System.out.println(AcceleratorRegistry.describe());
System.out.println(AcceleratorRegistry.describeUnavailable());
```

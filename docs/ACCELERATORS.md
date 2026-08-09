# Accelerators and image processing

Two new capabilities in SIPS-lib: a device abstraction that lets a node discover
and advertise its compute hardware, and a two-dimensional tiling model for image
work.

## Discovering devices

```java
import in.co.s13.sips.lib.accelerator.*;

System.out.println(AcceleratorRegistry.describe());
Device best = AcceleratorRegistry.bestDevice().orElseThrow();
```

On a 2019 Intel MacBook Pro this prints:

```
Java:x86_64 host CPU (CPU, 12 CUs, 8192 MB)
OpenCL:Intel(R) Core(TM) i7-9750H CPU @ 2.60GHz (CPU, 12 CUs, 32768 MB)
OpenCL:Intel(R) UHD Graphics 630 (INTEGRATED_GPU, 24 CUs, 1536 MB)
OpenCL:AMD Radeon Pro 5300M Compute Engine (DISCRETE_GPU, 20 CUs, 4080 MB)
```

Enumeration happens once per JVM and is cached; it touches native libraries and
is far too expensive to repeat per task.

## Device selection

`AcceleratorType` carries a default preference: discrete GPU, then FPGA, then
integrated GPU and APU, then NPU, then CPU. Ties break on compute units times
memory.

This is a heuristic, not a rule. A small kernel over a small image is often
faster on the CPU than on a discrete GPU once transfer cost is counted, and the
scheduler is free to override the ranking.

The integrated/discrete distinction is derived from OpenCL's
`CL_DEVICE_HOST_UNIFIED_MEMORY`, because it changes transfer cost by roughly an
order of magnitude and therefore changes which tiles are worth sending where.

## Backend status

| Backend | Status | Notes |
|---|---|---|
| `JAVA_CPU` | **Implemented** | No native dependency. Correctness reference and universal fallback. |
| `OPENCL` | **Implemented, verified** | Covers CPU, integrated and discrete GPUs across vendors. Verified on Intel UHD 630 and AMD Radeon Pro 5300M. |
| `CUDA` | Probing stub | NVIDIA only. Needs JCuda or a Panama FFM binding. |
| `ROCM` | Probing stub | AMD, Linux only. Needs a HIP binding. Map APUs to `APU`, not `DISCRETE_GPU`. |
| `METAL` | Probing stub | Apple platforms. The only route to the Apple Neural Engine. |
| `VULKAN` | Probing stub | Cross-vendor, more setup than OpenCL. |
| `NPU_RUNTIME` | Probing stub | No common API; target ONNX Runtime rather than four vendor SDKs. |

The stubs are **not silent**. Each probes for its runtime and reports why it
cannot be used:

```
CUDA: NVIDIA has shipped no macOS driver since 10.13; CUDA cannot run on this host
ROCm: ROCm is supported on Linux only
NPU:  Intel Macs have no Neural Engine; NPU inference is unavailable on this host
```

That distinguishes "this hardware does not exist here" from "the binding was
never wired up", which otherwise look identical from the outside. Read them with
`AcceleratorRegistry.describeUnavailable()`.

**The unimplemented backends have never been run against real hardware.** They
were written on a machine with no NVIDIA, no ROCm and no NPU. Treat their
enumeration code as unverified until it is exercised on the target.

## Adding a backend

1. Implement `AcceleratorBackend`.
2. Register the class in
   `src/main/resources/META-INF/services/in.co.s13.sips.lib.accelerator.AcceleratorBackend`.
3. Make `AcceleratorRegistryTest` pass — it is the conformance suite.

The contract that matters: **never throw**, including when the native library is
absent. A node must boot on hardware where most backends are missing. An
unavailable backend returns an empty device list and a non-blank reason; an
available one returns at least one device it can genuinely execute on.

## Tiling an image

Where a parallel `for` splits an integer interval, `TileGrid` splits a raster.

```java
TileGrid grid = TileGrid.forChunks(1920, 1080, 8, 1);  // 8 chunks, 1-pixel halo

for (Tile tile : grid.tiles()) {
    // tile.x/y/width/height  -> pixels this tile must produce
    // tile.readX/readY/...   -> pixels it may read, halo included
}
```

Each tile carries two rectangles:

- the **write region**, the pixels it is responsible for producing. Write regions
  never overlap and together cover the image exactly, so results reassemble with
  no gaps and no double-counting.
- the **read region**, the write region grown by the halo and clamped to the
  image edges. A 3x3 convolution needs one pixel of context to compute correct
  values at its own borders; without it every tile boundary shows a seam.

Choose the halo from the kernel radius: 0 for pointwise operations such as
brightness or thresholding, 1 for a 3x3 kernel, 2 for 5x5, and so on.

`forChunks` prefers a squarish split over a strip split, because a squarish tile
has a shorter perimeter and therefore exchanges less halo data.

Remainders are absorbed one pixel at a time across the leading rows and columns,
so a 1001x799 image over a 4x4 grid still tiles exactly.

## Mapping workloads onto this

| Workload | Decomposition | Halo | Suggested device |
|---|---|---|---|
| Batch processing of many images | One image per chunk | none | Any; scales with node count |
| Pointwise filters | `TileGrid`, any split | 0 | Integrated GPU is usually enough |
| Convolution, morphology, edges | `TileGrid` | kernel radius | Discrete GPU |
| Resize, warp | `TileGrid` on the output | depends on the sampling kernel | Discrete GPU |
| ML inference | One image or batch per chunk | none | NPU when implemented, else GPU |

## What is not built yet

Being explicit, because these gap the path from "devices are visible" to
"kernels run on them":

1. **No kernel execution.** The registry discovers and ranks devices; it does not
   yet compile or run OpenCL kernels. That is the next piece of work.
2. **The task transport is still text-only.** `Distributor.upload()` puts file
   contents into a JSON string field, so binary image data cannot pass through
   the task path intact. Image payloads must be routed through the file server's
   streaming path, which is binary-safe and already checksums with SHA.
3. **Schedulers are not device-aware.** The `Scheduler` interface takes nodes and
   tasks; it has no notion of the devices a node offers. Extending
   `Node` to carry its `List<Device>` is the prerequisite for placing tiles on
   suitable hardware.
4. **No result-merge primitive.** `TileGrid` guarantees tiles reassemble, and
   `Tile.index()` says where each belongs, but nothing yet performs the
   reassembly.

Items 2 and 3 are prerequisites for real distributed image processing. Item 1 is
what makes the accelerators useful once the data can reach them.

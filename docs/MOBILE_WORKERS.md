# Mobile workers

> Background: [primer §5–6](PRIMER.md) (barriers, and why communication
> frequency must match link latency) explains every "no" in this document.

Phones as SIPS nodes: what they are good for, what they are not, and what a
worker on each platform actually has to implement.

**Status: the cluster side is built and tested; iOS has a real, tested
library and a verified end-to-end app; Android is unbuilt.** The transport
(`OutboundWorker`), the judgment (`WorkerEligibility` + `WorkerRoster`,
fail-closed, now including a discrete thermal-level path for platforms
without a Celsius API), the measurement (`WorkerBench`), the task format
(`ExprTask` — expression plus data, one self-contained frame), speculative
re-issue, robust averaging, and **two** conformance suites: the WASM ABI
vectors and `expr-conformance.json` for the fifteen-op evaluator. The
`MobileFleet` sample runs the entire session over real sockets — join,
announce, refuse the unfit, shard by measured speed, re-issue past deadline,
stitch bit-identical results.

[SIPS-iOS-Worker](https://github.com/deepsidhu1313/SIPS-iOS-Worker) is a real
Swift port, verified against this repository directly: 22 tests green
including the real conformance suite, and a live cross-language round trip
against a genuine `WorkerConnections` process (`MATCH true`) — now run all the
way through the real installed app, not just the standalone `DialCheck`
executable. See *Findings feeding back into this repository* below and that
project's `docs/FINDINGS.md` for the full, detailed account, including what a
live deadlock found by testing looked like before it was fixed, and how two
initially-unresolved packaging findings turned out to be a test-harness bug
and an environment limitation rather than app defects.
Android is unbuilt; the reasoning below is analysis, not a verified result.

## Why phones at all

Because federated averaging was invented for them. McMahan et al. (2017) is
about keyboards on Android: the data worth training on is on handsets, it is
private, and the point of the algorithm is that it never leaves. Only weights
cross the wire — a few hundred kilobytes per round, inside the fetched-result
path this framework already has.

Phones are bad computers and excellent *data holders*. That distinction decides
everything below.

## What they are not good for — measured, not assumed

A sibling project (`ai_framework`) ran an expert-parallel gather across a Mac
and an Android handset with a real routing trace and real expert compute. The
relevant numbers:

| Path | remote-layer p50 | p99 |
|---|---|---|
| loopback (no phone) | **4.83 ms** | 5.93 ms |
| USB via adb relay | 13.8 ms | 40.3 ms |
| WiFi direct | 19.4 ms | **122 ms** |

A 122 ms p99 struck ~21 times per token is not a latency budget. That project's
conclusion — *WiFi devices carry cold replicas, not hot-path experts* — is the
rule here too, and it was reached by measurement rather than by argument.

Two further findings that shaped this design:

- **The busy-endpoint law**, twice replicated: per-round cost on a busy endpoint
  runs about **2× the idle probe at p50, with p99 amplified 3–20×**. Idle probes
  systematically flatter a device that is about to be given work. This is why
  `ShardPlan.Measured` weights by `mean / (1 + cv)` rather than by mean: an
  erratic node loses share *before* its average degrades.
- **"Use the accelerator" is a hypothesis, not a default.** On the Intel Mac in
  that fleet the GPU measured 4× *slower* than the CPU for llama decode, while
  SIPS's own GEMM benchmark measured 19.8× *faster* on the same GPU for large
  matrix multiply. Both are true. Capability metadata has to carry measured
  throughput per workload, never a feature flag.

So: **phones do batch work, not latency work.** A round of federated averaging,
a shard of batch inference, a variant in a population — all fine. A layer of a
model mid-token — never.

## What a mobile worker has to implement

Three things, in order of difficulty.

### 1. Dial out (`OutboundWorker`)

Carrier NAT means nothing inbound reaches a handset and the address changes with
the cell, so the master cannot open a socket to a phone. The worker opens the
connection and keeps it; work travels down a channel the worker established. One
connection carries many chunks — a connect per chunk is a radio wake-up and a
handshake per chunk, which costs more than the work does.

The frame format is in `WorkerFrames`: length-prefixed JSON header, then a
length-prefixed payload. Four frame types — `hello`, `work`, `result`, `failed`.
That is the whole protocol, and it is `OUTBOUND_WORKERS`, protocol version 4.

The connection does not have to be forever. `OutboundWorker.idleTimeout` lets it
go once nothing has arrived for a while, and redials next time there is work —
see *What JPPF does, and what we took from it* below for why.

### 2. Announce fitness honestly

The phone is the only party that knows its battery, its temperature and whether
it is charging; it sends them in the `hello` frame and the master applies
`WorkerEligibility` through `WorkerRoster`. The rule that matters: **an
unreadable battery counts as unfit.** A device that cannot say how it is doing
is exactly the one whose battery should not be assumed healthy — and on Android
a broken reading is a real observed condition, not a hypothetical, with `-1`
reported instead of a failure.

A device can also announce `IN_USE` — whether its owner is holding it right
now. The default policy refuses a worker actively in use, mains or battery,
but treats the field's *absence* leniently: almost no platform can report it
at all, so unlike battery, silence here is not held against a worker.

### 3. Run the module (the ABI)

`src/test/resources/abi-conformance.json` is the contract as data: five cases,
each a base64 module, an input, and the exact output and status a conformant
runtime must produce. It is regenerated by `AbiConformanceTest`, which runs every
case through the real Java runner — so the published file cannot drift from what
the cluster actually implements.

Data rather than a Java interface on purpose: an interface would require the
other platform to depend on this library, which is the one thing it cannot do.

The ABI is six imports in namespace `sips`:

```wat
(import "sips" "input_size"   (func (result i32)))
(import "sips" "input_read"   (func (param i32 i32 i32) (result i32)))
(import "sips" "output_write" (func (param i32 i32)))
(import "sips" "log"          (func (param i32 i32)))
(import "sips" "break_all"    (func (param i64 i32 i32)))
(import "sips" "break_after"  (func (param i64)))
```

The module exports `run(i64 firstIndex, i64 lastIndexExclusive) -> i64 status`
and one page of `memory`. The capability list *is* the security model: a module
can do exactly these six things and nothing else.

## The two task formats, and which port is easier

A worker needs to run *something*. There are now two portable somethings:

1. **WASM modules** — arbitrary compiled code, sandboxed by the six-import
   ABI. Android gets this free (Chicory on ART); iOS needs a WASM runtime
   (WasmKit) plus the six imports.
2. **Serialised expressions** (`ExprTask`) — the fifteen array ops with data
   attached. No runtime to embed at all: an evaluator is a few hundred lines
   in any language, and `expr-conformance.json` proves a port produces the
   same bytes (exact except transcendentals, where the vectors declare
   tolerance because Swift's libm is not fdlibm — the Java side pins
   StrictMath for exactly this reason).

For iOS, the expression path is the recommended start: afternoon-sized,
conformance-provable, and it already covers the batch inference and partial-
reduction workloads phones are good for. WASM support can follow.

## The worker checklist (either platform)

1. Dial the master, speak `WorkerFrames` (length-prefixed JSON + payload).
2. Send `hello` with the announcement schema: `MAINS`, `BATTERY`,
   `TEMPERATURE_C`, `BENCH_MS` — the timings of `WorkerBench.standard()`,
   warm-up first — and optionally `IN_USE`. Claims are judged fail-closed and
   hostile timings are discounted, so there is no incentive to lie and no harm
   in honesty.
3. On a `work` frame: decode the `ExprTask`, validate (the document is input
   from the network even on the phone), evaluate, reply `result` with
   little-endian float bytes; on failure reply `failed` with a reason rather
   than going silent — silence holds the round open until the deadline.
4. Re-announce when circumstances change (unplugged, hot, picked up); stop
   accepting work below the floors *before* the master would refuse you.
5. If configured with an idle timeout, expect the connection to close itself
   after a quiet stretch, and redial when there is something to contribute
   again — this is normal, not an error.

## What JPPF does, and what we took from it

[JPPF](https://github.com/jppf-grid/JPPF) is a mature Java grid framework with
a real, working Android node — verified against its actual repository and
documentation, not assumed. Two of its choices are worth comparing against.

**Tasks travel as dynamically-loaded DEX bytecode.** JPPF's sample build step
(`ant dex.jar`) cross-converts a compiled Java jar to Android's DEX format; the
node downloads and dynamically loads it to run arbitrary task code on-device.
We do not do this, on purpose. `ExprTask` is pure data — an expression plus
matrices, fed to an already-installed evaluator — and WASM modules run inside
a sandboxed interpreter. Nothing SIPS ships is "installed" differently before
or after a task arrives, which sidesteps exactly the pattern Android's
security hardening (W^X enforcement, Play Integrity attestation) and Play
Store policy have spent years working against.

**JPPF's Android node disconnects before every task**, reconnecting only to
submit results and fetch the next one — stated explicitly for battery, since a
background socket is what Doze and App Standby throttle or kill. That
reasoning is sound and worth taking; the mechanism is not, for us:
reconnecting per task reintroduces the radio wake-up and handshake §1 exists
to avoid, and a SIPS shard is seconds to minutes of work, not a JPPF-sized
microtask. `idleTimeout` is the synthesis — the connection survives a steady
stream of chunks and only lets go once genuinely idle, which is the situation
JPPF's design is actually protecting against.

**JPPF's separate "Idle Host" mode** — a node that only accepts work while its
host is otherwise idle, screensaver-style — has no equivalent built before
this document's previous revision. It is now `IN_USE` in the announcement
schema and `WorkerEligibility.Policy.avoidWhenInUse` (on by default): draining
a stranger's battery while they are mid-conversation with their phone is a
worse cost than doing it slowly while it charges overnight, whichever way the
device is powered.

## Per platform

**Android** is nearly free. Chicory is pure JVM bytecode with no JNI, so it runs
on ART — an Android worker reuses `WasmRunner` and `WasmHost` as they are. The
work is the app: a foreground service, the dial-out connection, and battery and
thermal reporting through `BatteryManager` and `HardwarePropertiesManager`.

**iOS is built**, at
[SIPS-iOS-Worker](https://github.com/deepsidhu1313/SIPS-iOS-Worker) — not the
WASM path originally sketched here, but the expression path: a Swift
`ArrayCompute` implementing all fifteen `Expr` ops, verified against the real
Java evaluator via `expr-conformance.json` and, separately, against a live
`WorkerConnections` process over a real socket (`MATCH true`). `MATMUL` routes
to Apple's own `cblas_sgemm` — no kernel-tuning work needed, unlike the OpenCL
path — and doing so surfaced a real, useful finding, corrected in this
repository's own `ExprConformanceTest`: see *Findings feeding back into this
repository* below.

The WASM path (a Swift runtime implementing the six host imports by hand,
originally estimated at "an afternoon's work") remains unbuilt and the
estimate untested. The expression path turned out to need no WASM runtime at
all, which is why it was built first — it directly serves the two workloads
phones are actually good for (batch inference, a `planReduce` partial), and
"no runtime to embed" is a smaller surface for exactly the packaging issues
`SIPS-iOS-Worker/docs/FINDINGS.md` documents in detail.

Building it also surfaced a real answer to the question this document used to
leave open — whether iOS is a reference implementation or a shipping app: it
does not have to be decided up front. The library (`SIPSWorker`, tested via
plain `swift build`/`swift test`, no Xcode-GUI dependency) is useful as a
reference/interop target on its own, independent of whether the app half ever
ships; that separation turned out to matter for a concrete tooling reason
(`AppleProductTypes` is unavailable to the open-source CLI toolchain) as well
as the product one.

## Findings feeding back into this repository

Two things built for iOS changed code here, not just documentation:

- **`WorkerEligibility.ThermalLevel`** — iOS has no Celsius API at all, only
  `ProcessInfo.thermalState`'s four-level enum. `Reading`/`Policy` gained a
  parallel discrete-level path (`onBatteryWithThermalLevel`,
  `mainsWithThermalLevel`, `Policy.maxThermalLevel`), and `WorkerRoster` parses
  an optional `THERMAL_STATE` announcement field alongside `TEMPERATURE_C`.
- **A conformance-vector overclaim, corrected.** `expr-conformance.json`'s
  `matmul-exact` and `gram-partial` vectors declared zero tolerance, reasoning
  that a defined summation order guarantees a defined result. Porting `MATMUL`
  to Accelerate's `cblas_sgemm` — the obviously correct choice on that
  platform — measured a real ~1-ULP gap, because a vendor BLAS's internal
  accumulation grouping is not documented to match a sequential accumulate.
  Both vectors now declare a small measured tolerance instead, and the file's
  general claim about zero tolerance is narrowed to what is actually true.
  Full detail: `SIPS-iOS-Worker/docs/FINDINGS.md`.

Two more were initially recorded as open findings and have since been
resolved, with the correction kept alongside the original mistake rather than
silently erased: the apparent "touch input doesn't reach the app" bug turned
out to reproduce on Apple's own stock Settings app too, isolating it to the
test session's simulator-automation touch-injection path rather than this
project's packaging; and the apparent "hello frame doesn't reach the master"
bug turned out to be a test script awaiting the wrong worker id
(`DialCheck`'s hardcoded id instead of the real app's device-derived one) —
the TCP connection and hello frame were correct the entire time. Both
corrections, including the counter-intuitive discovery that passing
`--entitlements` explicitly to `codesign` *breaks* launch (the working
invocation omits it), are recorded in detail in
`SIPS-iOS-Worker/docs/FINDINGS.md`.

**Android remains unbuilt.** The reasoning below (Chicory reuse, foreground
service, `BatteryManager`/`HardwarePropertiesManager`) is analysis, not a
verified result, in the same sense the WASM path above now is — noted so a
reader does not mistake "written down" for "measured," the distinction this
whole document now tries to keep visible throughout.

## What is deliberately absent

Per-layer model splitting across phones, in any form. The measurements above
rule it out, and building it would mean a framework whose headline feature does
not work on the hardware it targets.

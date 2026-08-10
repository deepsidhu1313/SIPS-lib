# WebAssembly chunks

> **What is WebAssembly and why is it here?** WASM is a small, portable
> binary instruction format: any language that compiles to it (C, Rust, Go,
> ...) produces a module any conformant runtime can execute — on a server, in
> a JVM, on a phone. Two properties make it ideal as a unit of distributed
> work. **Sandboxing by construction**: a module can only call the functions
> its host explicitly provides (here, six — the import list *is* the security
> model), so untrusted code runs without trusting it. **Deterministic floats**:
> WASM specifies IEEE-754 exactly, so the same module gives bit-identical
> answers on x86 and ARM — which is what lets one job run across a mixed
> cluster and be verified. Background on why that matters: [primer §9](PRIMER.md).

A SIPS chunk is normally Java source. The node writes a `build.xml`, runs Ant to
compile it, then forks a JVM. That works, but it costs hundreds of milliseconds
per chunk before the first iteration runs, and it puts a floor under how small a
chunk can usefully be — which is exactly the knob a scheduler needs when the work
is uneven.

A WebAssembly chunk skips all of it. The module arrives precompiled, the node
loads it in-process, and the call costs microseconds.

Measured on this repo's suite (`WasmRunnerTest`): a module parses once in single-digit
milliseconds and every subsequent chunk starts in well under a millisecond.

## The contract

A SIPS module exports one function:

```wat
(func (export "run") (param i64 i64) (result i64))
;;                          ^    ^            ^
;;                      first  last        0 = success
```

- `first` — the first iteration index this chunk owns
- `last` — one past the last (a half-open range, so `last - first` is the count)
- return — `0` for success, anything else for failure

A module that traps, or that runs past its timeout, fails the chunk without
taking the node with it.

## The host interface

Two integers in and one out is not enough to be a unit of work. A module may
also import these — all six are supplied, and a module declares only what it
uses:

```wat
(import "sips" "input_size"   (func (result i32)))
(import "sips" "input_read"   (func (param i32 i32 i32) (result i32)))
(import "sips" "output_write" (func (param i32 i32)))
(import "sips" "log"          (func (param i32 i32)))
(import "sips" "break_all"    (func (param i64 i32 i32)))
(import "sips" "break_after"  (func (param i64)))
```

| function | arguments | what it does |
| --- | --- | --- |
| `input_size` | — | how many bytes of input this chunk has |
| `input_read` | destination, source offset, length | copies input into the module's own memory; returns bytes actually copied |
| `output_write` | offset, length | appends memory to the chunk's result |
| `log` | offset, length | one UTF-8 line back to the submitter |
| `break_all` | index, offset, length | found it — stop the job, carrying these bytes home |
| `break_after` | index | nothing past this index is wanted |

`input_read` copies into memory the module already owns, so neither side needs an
allocator, and a module with a small scratch buffer can stream a large input.

A module using any of these must define and export its memory.

**This list is the security model.** There is no file access, no network, no
clock and no allocator, and an import outside this list is refused at load time —
which is why a module runs inside the node's own process rather than behind a
fork. The runtime enforces it; nothing relies on the module behaving.

`break_all` and `break_after` reach the same early-exit state the Java API uses,
so a WASM search chunk stops the cluster exactly as `sim.breakAll()` does. Their
value is carried home as raw bytes: only the module knows what its answer means.

## Declaring one

In `manifest.json`:

```json
{
  "PROJECT": "mandelbrot",
  "TYPE": "wasm",
  "WASM": {
    "MODULE": "kernel.wasm",
    "INPUT": "tile.bin",
    "OUTPUT": "tile-out.bin",
    "ENTRY": "run",
    "TIMEOUT": 600
  }
}
```

`TYPE` says how the chunk runs — `java` (the default, and what every manifest
written before this field existed means) or `wasm`. It is declared rather than
inferred from which other fields happen to be set, so a manifest that names no
executor it meant gets a sentence back instead of a surprise. A `wasm` job needs
the `WASM` block; a `java` job needs `MAIN`.

`INPUT` names the per-chunk file the module reads, `OUTPUT` where its result
lands (default `output.bin`), `ENTRY` defaults to `run`, and `TIMEOUT` to 600
seconds. A chunk that computes purely from its index range needs neither `INPUT`
nor `OUTPUT`.

The module and a `chunk.json` naming the range travel in the per-chunk directory,
the same channel that already carries generated sources:

```json
{ "FIRST": 0, "LAST": 4096 }
```

## Producing a module

Anything that targets WebAssembly works — the node only sees bytes. Rust:

```rust
#[link(wasm_import_module = "sips")]
extern "C" {
    fn input_size() -> i32;
    fn input_read(destination: i32, source: i32, length: i32) -> i32;
    fn output_write(offset: i32, length: i32);
}

#[no_mangle]
pub extern "C" fn run(first: i64, last: i64) -> i64 {
    for i in first..last {
        // your work
    }
    0
}
```

```bash
rustc --target wasm32-unknown-unknown -O --crate-type cdylib kernel.rs
```

C via clang, TinyGo, AssemblyScript and Zig all work the same way.

## What this does not do

**A running module cannot be interrupted.** WebAssembly has no interrupt, so a
chunk that has already started runs to completion or hits its timeout. A `breakAll`
arriving mid-flight will stop *queued* chunks and let running ones finish; it
cannot cancel them the way `Process.destroy()` cancels a forked JVM. Size chunks
accordingly if early exit matters to you.

**Input and output are whole-chunk, not streaming.** The chunk's input is read
into memory before the module starts and its output is written after it returns.
Work whose inputs do not fit in a node's memory still belongs on the Java path.

**Floating point crosses node boundaries unchanged.** WebAssembly requires
correctly-rounded IEEE-754 arithmetic and forbids FMA contraction, so unlike the
OpenCL kernels — where SIPS restricts itself to integers precisely because
float behaviour is implementation-defined — a float result here is bit-identical
on every node. The one exception is NaN payload bits, which the spec leaves
free; don't build a checksum over them.

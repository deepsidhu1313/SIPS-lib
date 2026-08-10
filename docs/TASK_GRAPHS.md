# Task graphs

**Status: built.** All four steps are implemented and tested. What has *not*
been done is running any of it against a live cluster — see
[What is not verified](#what-is-not-verified).

## The problem the loop model cannot express

`sim.parallelFor()` splits one iteration space across nodes. Real work is rarely
one loop. An MRI pipeline is:

```
load volume → bias-correct (per slice) → register (whole volume) → segment (per slice) → merge
```

Steps 2 and 4 are parallel loops. The ordering between them is not, and today
you express it by submitting four jobs and waiting between them by hand — which
means the cluster drains to idle at every boundary, and nothing can start step 4
on the slices that are ready while step 3 is still finishing others.

That is the gap. Not "loops with dependencies" — a different scheduling question.

## What exists today

`Job` is the graph and its rules; `JobSequencer` decides what may run when.
Keeping them apart means a graph can be inspected, drawn and validated without
pretending to execute anything — and the same sequencer logic drives a live
cluster and an offline evaluation.

```java
JobSequencer run = new JobSequencer(job);
while (!run.isFinished()) {
    for (Stage stage : run.ready()) {   // everything whose dependencies are done
        run.started(stage);
        distribute(stage);              // hand to the existing scheduler
    }
    // ... later, as each finishes:
    run.completed(stage);               // or run.failed(stage, why)
}
```

`ready()` is the whole point. It releases a stage the moment *its own*
dependencies finish — not at a round boundary, and not when the whole level is
done. Measured on an uneven pipeline (a three-step quick chain beside one slow
stage), that is 101 time units against 103 for submitting level by level; the
gap widens with the length of the quick chain.

A stage that fails marks everything downstream `SKIPPED` immediately, because a
pipeline that waits forever on a step which already failed is the worst outcome
available and the one you get by default. Work unaffected by the failure keeps
running — those results are still results.

`JobRunner` drives the loop, and `StageRunner` is what "running a stage"
actually means — a fake in tests, `DistributedStageRunner` on a node. That split
is why every failure worth being sure about (a node dying, a distribution that
never lands, a stage that runs forever) is testable without hardware.

`Stage.timeout()` is enforced by `JobRunner`, and cancelling a timed-out stage
sends the node the `kill` command it has understood for years and nothing ever
sent.

A manifest declaring `STAGES` takes the pipeline path; anything else — every
manifest written before stages existed — goes through the single-loop path
unchanged.

## The API

```java
Job job = new Job("mri-pipeline");

Stage load      = job.single("load");
Stage correct   = job.parallelFor("bias", 0, sliceCount).after(load);
Stage register  = job.single("register").after(correct);
Stage segment   = job.parallelFor("segment", 0, sliceCount).after(register);
Stage merge     = job.single("merge").after(segment);

job.validate();     // cycles, duplicate names, edges to another job's stages
```

A `Stage` is a unit of scheduling. Two kinds:

- **`single(name)`** — one task, one node. The thing the loop model has no word for.
- **`parallelFor(name, first, last)`** — an iteration space, split by a
  `LoopPolicy` exactly as today. Everything already built applies unchanged.

`after(...)` is the only structural operator. It takes any number of stages and
means *all of them finished*. That is enough to express any DAG, and refusing
anything richer keeps the scheduler's input a plain dependency graph.

Per-stage knobs reuse what exists:

```java
job.parallelFor("segment", 0, sliceCount)
        .after(register)
        .using(new Factoring())            // LoopPolicy, as today
        .timeout(Duration.ofMinutes(10))
        .type(TaskType.WASM);              // the manifest's TYPE, per stage
```

Per stage, not per job — which is the reason a pipeline is worth expressing as a
graph at all. Its steps do not behave alike: one stage over ragged work wants
Factoring where an even one is fine with Chunk, and a tight per-pixel stage wants
WASM where the step that needs a library wants Java. A single stage refuses a
`LoopPolicy`: there is no iteration space for it to divide, and silently ignoring
it would hide a category error.

Still to come: `.requires(Accelerator.GPU)`. `DeviceAware` already ranks nodes by
fitness, but nothing yet lets a stage state a requirement for a
`PlacementPolicy` to honour.

### Data between stages

A stage's output is the next stage's input, and the framework must know that or
locality is unschedulable:

```java
Stage correct  = job.parallelFor("bias", 0, slices).writes("corrected/{index}.raw");
Stage register = job.single("register").after(correct).reads(correct);
```

`reads(stage)` is what makes placement interesting. `register` should land where
`correct`'s output already sits, and the scheduler cannot know that from the
dependency edge alone.

## Placement

Everything below is built. `PlacementPolicy` lives beside `LoopPolicy`;
`EarliestFinish`, `Heft`, `LeastLoaded` and `NearestData` ship in
`SIPS-Schedulers`; `DagEvaluator` compares them offline against the same `Job`
the cluster would run.

### What it is worth, measured

`DagEvaluatorTest` runs a three-stage chain beside a single leaf on two nodes,
one half again as fast as the other:

| policy | makespan |
| --- | --- |
| HEFT | 20.67 |
| EarliestFinish | 24.00 |
| critical-path floor | 20.67 |

The 16% gap is ordering alone — both use the same node-choice rule. HEFT gives
the fast node to the chain everything else waits on; plain EFT takes the stages
as declared and gives it to the leaf.

HEFT is also insensitive to the order the pipeline was written in, where the
simpler policies are not. That is a real cost of the simpler ones: the same
pipeline, written two equally sensible ways, schedules differently and nothing in
the file explains why.

Three honest boundaries, each with a test:

- **On a uniform cluster every policy ties.** Heterogeneity is the only thing
  that separates them; on identical machines the estimates buy nothing.
- **This HEFT is insertion-less.** It never backfills an idle gap it created, so
  where one node is much faster it piles a queue onto it. At a 3× spread on this
  graph, plain `LeastLoaded` wins. Adding insertion is the obvious next
  improvement — and having the evaluator first is what makes it worth doing.
- **No policy beats the critical path.** Worth knowing before blaming a
  scheduler.

### Data locality

`reads` and `writes` are built, and `DagEvaluator` charges for moving data that
is not already where a task landed. Without that charge a policy that ignores
locality is never punished for it and the comparison is meaningless.

Measured on two producers and two consumers, transfer costing as much as
processing: `NearestData` finishes at 40.0 against `EarliestFinish` at 45.71 —
the blind policy reaches for the faster node and pays to drag the volume across
to it.

## Why it is a second SPI

`LoopPolicy` answers *how big is the next batch*. A DAG answers *which ready task
goes to which node*, and the good answers depend on estimated task durations,
transfer costs and the critical path — HEFT, PEFT, lookahead, and the rest of a
literature considerably larger than the loop-scheduling one.

So: a second SPI, deliberately not the same one.

```java
public interface PlacementPolicy {

    String name();

    /**
     * Which node should run this task, given the tasks that are ready now and
     * what each node is already doing.
     *
     * @return the chosen node, or empty to leave the task queued this round
     */
    Optional<Node> place(ReadyTask task, List<Node> nodes, ClusterState state);

    default String description() {
        return name() + " placement policy";
    }
}
```

`ReadyTask` carries what a heuristic needs and nothing more: the stage, the
estimated duration per node (from the benchmark data `DeviceAware` already
reads), the upward rank on the critical path, and where its inputs currently
live. `ClusterState` gives queue depths and in-flight tasks.

Same bargain as `LoopPolicy`: one method between having an idea and finding out
whether it works. HEFT ships as the reference implementation, and `Evaluator`
grows a DAG mode so a new policy can be compared offline against a recorded
workload before it touches a cluster.

**This is the reason to build it.** Loop scheduling is a solved-ish field with a
small literature; DAG scheduling is active, and nobody offers a pluggable surface
for it. It is the same gap `LoopPolicy` exploits against JPPF's `Bundler`, one
level up.

## Lambda: a stage you submit without a job

Built. `ClusterCall` is a one-stage job — literally: `asJob()` produces that
stage, so a call goes through the same graph, sequencer and placement policies as
anything else rather than getting an execution path of its own.

`LocalCallDispatcher` runs a call in-process, which is both how a module gets
checked before it is sent anywhere and the whole answer on a single machine.
`NodeChoice` turns the live cluster into what a `PlacementPolicy` reasons about.
Results below 256 KB ride home inside the finish message, because a caller
blocked on two kilobytes cannot afford a second round trip; anything larger stays
on disk for the existing fetch path.

```java
byte[] thumbnail = ClusterCall.of(Path.of("thumbnail.wasm"), imageBytes)
        .placedBy(new NearestData())      // or LeastLoaded, or Heft
        .timeout(Duration.ofSeconds(30))
        .on(dispatcher)
        .orThrow();
```

No manifest, no distribution directory, no chunk range — a module, some bytes,
and a policy deciding where it runs. WASM is what makes this affordable: a
sub-millisecond start means a request-sized unit of work is worth scheduling at
all, which it never was when every task cost a javac and a JVM.

What it deliberately is not: SIPS does not provision anything, so this is not
Lambda's defining property. It is *placement as a service* over a cluster you
already run — which is honestly the more interesting half for HPC, and the half
Lambda does not let you touch.

## What is not verified

Everything here is tested; none of it has run on a live cluster. Specifically:

- **`DistributedStageRunner.start`** needs live nodes. Its polling half is tested
  against a populated distribution table; the distributing half is not.
- **The result round trip.** A node encodes a small WASM result into the finish
  message and the master records it. Each end is tested; the two have never
  talked to each other.
- **`KillChunk`.** The node has understood `kill` for years, but nothing sent it
  until now, so the handler's behaviour under a real kill is unexercised.
- **The evaluator models compute and transfer, not contention or cache.** Treat
  its numbers as a way to rank policies, not to predict runtime.

## What is left

1. **Insertion-based HEFT** — the measured gap above says when it matters.
2. **A custom `PlacementPolicy` named in a manifest**, as `LoopPolicy` already
   can be. Today a pipeline gets the default.
3. **A cluster `CallDispatcher`** — `NodeChoice` picks the node and the result
   path exists; what is missing is the piece that sends the call and waits.

The thing to avoid: making `PlacementPolicy` and `LoopPolicy` one interface so
the framework looks unified. They answer different questions, and forcing one
shape over both would restore exactly the barrier `LoopPolicy` was written to
remove.

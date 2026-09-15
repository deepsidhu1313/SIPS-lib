# Large-model research: layer pipelines, expert serving, and elastic nodes

> Background: [primer §6](PRIMER.md#6-three-ways-to-split-a-model-and-the-two-that-dont-survive-ethernet)
> (the three ways to split a model, and why two of them don't survive Ethernet)
> is the reasoning everything below either follows or deliberately departs from.

Three capabilities, born from the same question — can SIPS help research a
model too large for one machine — and answered three different ways, because
"too large for one machine" turned out to name at least two different
problems: how to split work **across a cluster**, and how to fit more than
one machine's own fast memory holds **on that one machine**.

**Status: all three are real, tested, scheduling-and-execution machinery, run
and measured in-process on one machine, and now compose with each other where
that composition makes sense (a node hosting a pipeline stage or an expert
replica can consult its own elastic cache — see each section below). None has
run over a real network or against a real model, and none has real fault
tolerance.** That is not a minor footnote; see *What's deliberately absent*
under each for exactly what it means and does not mean.

## Why three capabilities, not one

The primer's own rule already answers why "split a large model across a
cluster" is not one question, and the third capability answers a question the
primer doesn't ask at all:

- A **pipeline** (`in.co.s13.sips.lib.ml.pipeline`) is what the primer calls
  pipeline parallelism, built for the case the primer says it survives:
  throughput, not latency. A model split by layer-group, streaming a batch of
  independent items through — CT slices, in spirit — where nobody is waiting
  on any one item's answer and the pipeline can stay full. Communication
  happens once per item per stage boundary, not once per matmul.
- **Expert serving** (`in.co.s13.sips.lib.ml.serving`) is not model
  parallelism in the primer's sense at all — it does not split one model, it
  places several *different* models (experts) across a fleet and decides,
  live, which request goes to which one, with session affinity for multi-turn
  clients and reactive rebalancing for the expert everyone happens to want
  right now. The primer's rule about communication frequency still applies,
  but the thing being decided is routing, not gradient or activation
  exchange.
- **Elastic nodes** (`in.co.s13.sips.lib.ml.elastic`) is not a cluster
  question at all — it's what one node does with its *own* fast-but-small and
  slow-but-large memory once a request arrives there, the mechanism a real
  edge-native MoE serving engine ([FreeToken](https://github.com/FlashML-org/FreeToken))
  uses to fit a model too big for one GPU's VRAM. It composes with the other
  two rather than replacing either: a node running an expert-serving replica,
  or hosting a pipeline stage, still has to decide what stays resident in its
  own fast tier — that composition is real and unstarted, see below.

All three reuse existing machinery rather than inventing parallel
infrastructure: a pipeline stage is an [`Expr`](ARRAY_OPS.md)
graph evaluated by the same `ArrayCompute` every other array op in this
repository goes through, and all three packages' balancing logic descends directly
from `ShardPlan`'s "balance by measured worth, not by guessing" principle.

## Pipeline parallelism for batch throughput

`LayerGroup` is a slice of a model's layers: an `Expr` graph plus the weight
matrices it reads as constants, validated at construction so a graph with a
dangling or misnamed input fails at assembly time rather than the first time
an item happens to flow through it. `PipelineModel` orders the groups.
`PipelinePlan.assign` decides which node runs which stage and how many
replicas of each, filtering by memory eligibility first — a stage whose
weights do not fit on any node is a real, reportable planning failure, not a
shard to quietly shrink — and, given a per-stage cost map from
`PipelinePlan.measureStageCost`, weighs each stage's *items/second capacity*
rather than raw assigned node speed alone (see *the negative finding* below
for why this was added). `PipelineRunner` streams items through real,
concurrently running stages on `java.util.concurrent` primitives, wired queue
to queue, optionally with a bounded `queueCapacity` so a producer stage faster
than its consumer genuinely blocks instead of growing that queue without
limit. `ElasticLayerExecutor` lets a node hosting a stage consult its own
`ElasticNodeCache` on every access, opt-in per node (see *elastic nodes*
below) — a `LayerExecutor` composing with another `LayerExecutor`, not a
special case either package needs to know about.

**Measured** (`SIPS-samples/LayeredPipeline`, macOS Intel i7-9750H, 4,000
items across a 3-stage toy model with one deliberately expensive stage):

```
configuration                      wall time       throughput
one replica per stage                 165 ms        24242 items/s
PipelinePlan.assign, cost-blind        84 ms        47619 items/s
hand-tuned at the bottleneck           77 ms        51948 items/s
PipelinePlan.assign, cost-aware        49 ms        81633 items/s
```

Replicating the true bottleneck genuinely helps — over 2× the minimal
baseline — which validates `PipelineRunner` as a real execution mechanism,
not just plumbing. The cost-blind plan splits nodes evenly and leaves real
throughput on the table next to a hand-tuned allocation, exactly the gap *the
negative finding* below describes. Fed `measureStageCost`'s real per-stage
numbers instead, the planner beats the cost-blind plan every repeated run
(1.2×–3.0× across several runs) and sometimes beats the hand-tuned guess too,
since it also correctly weighs the stage the hand-tuned guess assumed was
free.

### What's deliberately absent

- **No real network.** `PipelineRunner.local()` runs every stage in-process
  regardless of which node uuid a plan assigned it to. `LayerExecutor` exists
  as an interface specifically so a future executor can dial a real node
  instead — the queueing, replication, and shutdown logic would not need to
  change — but that executor does not exist yet, and the round trip a real
  cluster would add to every stage boundary is entirely unmeasured.
- **No fault tolerance.** A node holding one stage's only replica going away
  mid-run is not handled; the pipeline simply stalls behind it. This depends
  on the real network layer above existing first.

## Expert serving for multi-turn, multi-client throughput

`ExpertRegistry` tracks which nodes host which experts and each (expert,
node) pair's live queue depth. `SessionRegistry` pins a session to a node
with expiry, against an injected clock so tests move time forward
deterministically rather than sleeping through a real timeout, carries an
opaque per-session `state` object across re-pins, and can mark a node
unavailable — a pinned session then reads as unpinned for routing without
losing that state. `ExpertRouter` resolves the pull between session affinity
and load balancing with a strict priority — affinity wins, but only when the
pinned node can actually serve the expert this specific request needs, since
a session's pin may be left over from an earlier request to a different
expert entirely — and reports when a fallback routing decision is recovering
a session that carried real state, as opposed to a genuinely fresh one.
`HotExpertPolicy` adds a replica of an overloaded expert reactively rather
than provisioning every expert for a worst case that may never come, now
processing several simultaneously-hot experts in order of actual severity and
tracking a node's memory as it depletes within one rebalance call, so two
experts cannot each be handed a replica on a node that could only ever hold
one of them. `ExpertServingRunner` drives all of it with real threads and
real elapsed time; what an expert actually computes is out of scope on
purpose, the same way `ShardPlan` never runs the training step it divides
work for. `ElasticRequestExecutor` lets a node consult its own
`ElasticNodeCache` before serving a request, the same composition pattern the
pipeline package uses (see *elastic nodes* below).

**Measured** (`SIPS-samples/ExpertServing`, macOS Intel i7-9750H, 300 turns
across 20 multi-turn sessions, one expert deliberately popular at 70% of
traffic):

```
run                             count     p50 ms     p99 ms affinity hit
static (1 replica each)           300       3.84      33.10        78.7%
adaptive (HotExpertPolicy)        300       3.70       4.65        83.7%
```

p99 latency improved roughly 5.7–12× across runs in this and the previous
release (machine load, not a regression, explains the width of that range)
once the hot expert's queue depth crossed the rebalancing threshold and a
second replica absorbed the load — measured with the identical turn sequence
and an identical pause inserted at the identical point in both runs, so the
difference is the rebalance decision and not the pause itself giving the pool
a moment to catch up. One early run, before that control was in place,
showed almost no difference — kept in `SIPS-samples/ExpertServing/Readme.md`
rather than discarded, since a demo's own measurement bug is exactly the
kind of finding this project keeps.

### What's deliberately absent

- **No real network, no real expert models.** Every "expert" is a stand-in
  callback; every "node" is a label. What's real is the scheduling logic
  around them, which is this package's actual job.
- **No automatic failure detection.** `SessionRegistry.markUnavailable` and
  `ExpertRegistry`'s replica set are both changed only by explicit caller
  action here — nothing watches a real node and calls either on its own, and
  there is no way to retract a replica once `ExpertRegistry.addReplica` has
  registered it. This depends on the real network layer above existing
  first, the same as pipeline fault tolerance.

## Elastic nodes: one machine's own memory hierarchy

Not a cluster capability — this is what happens *after* a request has already
arrived at a node, and that node has to decide whether what it needs is
already in its own fast-but-small memory or has to be fetched from its own
slow-but-large memory first. `UnitProfile` describes one resident unit's size
and cost (`fetchMillis` to promote it, `hotComputeMillis` once resident,
`coldComputeMillis` to run it in place without promoting). `ElasticNodeCache`
makes the actual decision on every access: promote and pay the transfer cost,
or run in place — an LRU policy evicts the least-recently-used resident unit
when room is needed, and an `adaptive` flag lets a caller compare that
cost-aware decision against a naive always-promote cache on the identical
workload, since a policy that can't be measured against a simpler alternative
hasn't proven anything. `TransferCost.profileFor` now measures a real
`fetchMillis` (a timed `System.arraycopy` of the unit's own byte size) instead
of taking it as a declared constant. `ElasticLayerExecutor` and
`ElasticRequestExecutor` compose this cache into the pipeline and serving
packages respectively, opt-in per node.

**Measured** (`SIPS-samples/ElasticNode`, 5,000 accesses across six cheap,
popular "good" units and two expensive, unpopular "bad" ones sized so
promoting one "bad" unit evicts three "good" ones):

```
configuration                          total ms     promotions
no fast tier at all                       30000              0
naive (always promote)                    13059           2538
adaptive (cost-aware)                     10234            706
```

The adaptive policy still beats naive outright (1.28× faster, 706 promotions
against naive's 2,538) by correctly declining to promote a "bad" unit
whenever `fetch + hotCompute` exceeds `coldCompute`. What changed from the
previous release: with `fetchMillis` now a real measured number rather than a
declared 50ms constant, naive always-promote is *not* worse than no fast tier
at all (2.30× faster, not 0.80× slower) — that finding was an artifact of a
declared cost 25× larger than a 3×-larger unit's real transfer cost, and did
not survive real measurement. See *the negative finding* below for the fuller
account; the mechanism (checking real costs beats blind promotion) held, the
specific "worse than nothing" headline did not.

### What's deliberately absent

- **No real GPU, no real model.** Every "unit" is a name plus a real measured
  transfer cost and two declared compute numbers — there is no real
  accelerator kernel or model weight behind either compute number.
- **Bandwidth measured locally, not over a real link.** `TransferCost` times
  a real `System.arraycopy` between two heap arrays on one machine — a real
  number, but the honest ceiling on what is measurable without real
  PCIe/NVLink or network bindings, not equivalent to a real transfer.
- **Composition covers wiring, not scheduling.** `ElasticLayerExecutor` and
  `ElasticRequestExecutor` let a node's cache observe real traffic passing
  through a real executor; neither package's own scheduling decision (which
  replica runs an item, which node serves a request) is influenced by cache
  state yet. A planner or router that preferred a node already holding what
  it needs hot is real, unstarted work on top of what this release adds.
- **Coarse-grained locking.** Every public method on `ElasticNodeCache` is
  `synchronized`, a real, stated bottleneck if this sits on a hot path under
  heavy concurrent access — correctness over performance was the right choice
  for a first version, not an oversight to leave undocumented.

## The negative findings, for the primer's own register

**Cost-blind balancing under-serves a pipeline's true bottleneck — fixed,
not just found.** `PipelinePlan.assign` mirrored `ShardPlan`'s principle —
balance discrete resources by measured worth — applied to something
`ShardPlan` never had to handle: work that is not homogeneous. `ShardPlan`
divides a single, uniform batch of samples, so node speed alone is the whole
story; a pipeline's stages genuinely differ in cost, and node speed alone
materially under-served the true bottleneck, measured directly above. The
fix was not a bigger balancing formula; it was a missing input — a per-stage
cost estimate, now `PipelinePlan.measureStageCost`, measured the way this
project measures everything else rather than declared, and fed into a new
`assign` overload. Recorded here, resolved and not deleted, because a
refuted assumption about what "balance the cluster" requires is worth
exactly as much before and after it's fixed — the register is a history, not
a live TODO list to prune once an item closes.

**A declared cost can manufacture a finding that real measurement erases.**
`SIPS-samples/ElasticNode`'s first release declared a "bad" unit's transfer
cost as a flat 50ms against a "good" unit's 2ms — a 25× gap for units only 3×
apart in size — and found naive always-promote caching *worse than no cache
at all*. Once `TransferCost` measured that cost for real instead (a real
`System.arraycopy` scaling roughly linearly with bytes moved, as transfer
cost actually should), the gap shrank to roughly 3×, and the "worse than
nothing" finding did not survive: naive always-promote turned out to beat no
caching after all, just by less than the adaptive policy does. The
underlying mechanism this sample exists to show — checking real costs beats
blind promotion — was never in question and remains measured true. What was
wrong was a specific, more dramatic headline resting on an unmeasured input.
Recorded here for the same reason as the entry above: not because the
mistake was large, but because a wrong finding correcting itself once a
placeholder became a real measurement is exactly the kind of event this
register exists to keep visible rather than quietly overwrite.

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
and measured in-process on one machine. None has run over a real network or
against a real model.** That is not a minor footnote; see *What's deliberately
absent* under each for exactly what it means and does not mean.

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
shard to quietly shrink. `PipelineRunner` streams items through real,
concurrently running stages on `java.util.concurrent` primitives, wired queue
to queue, so a throughput number measured against it means something.

**Measured** (`SIPS-samples/LayeredPipeline`, macOS Intel i7-9750H, 4,000
items across a 3-stage toy model with one deliberately expensive stage):

```
configuration                      wall time       throughput
one replica per stage                 752 ms         5319 items/s
PipelinePlan.assign, 6 nodes          542 ms         7380 items/s
hand-tuned at the bottleneck          432 ms         9259 items/s
```

Replicating the true bottleneck genuinely helps — 1.74× over the minimal
baseline — which validates `PipelineRunner` as a real execution mechanism,
not just plumbing. But `PipelinePlan.assign`, given the same nodes, split them
evenly instead of concentrating on the bottleneck, landing 25% below what a
caller who knew the per-stage cost could build by hand. See *the negative
finding* below for why, and what it implies for what to build next.

### What's deliberately absent

- **No real network.** `PipelineRunner.local()` runs every stage in-process
  regardless of which node uuid a plan assigned it to. `LayerExecutor` exists
  as an interface specifically so a future executor can dial a real node
  instead — the queueing, replication, and shutdown logic would not need to
  change — but that executor does not exist yet, and the round trip a real
  cluster would add to every stage boundary is entirely unmeasured.
- **No per-stage cost model feeding the planner.** `PipelinePlan.assign`
  balances replicas by *node speed*, the lever `ShardPlan` has for a
  homogeneous batch of samples. A pipeline's stages are not homogeneous in
  cost, and nothing here measures a stage's own expense — the way
  `WorkerBench` measures a node — to weigh against it. This is the single
  most consequential thing to build next; the measured gap above is the
  argument for building it, not a guess.
- **No streaming backpressure.** Queues between stages are unbounded. A
  producer stage faster than its consumer will grow that queue without limit
  rather than slow down — fine for a bounded demo batch, a real liability for
  an unbounded, long-running stream.
- **No fault tolerance.** A node holding one stage's only replica going away
  mid-run is not handled; the pipeline simply stalls behind it.

## Expert serving for multi-turn, multi-client throughput

`ExpertRegistry` tracks which nodes host which experts and each (expert,
node) pair's live queue depth. `SessionRegistry` pins a session to a node
with expiry, against an injected clock so tests move time forward
deterministically rather than sleeping through a real timeout. `ExpertRouter`
resolves the pull between session affinity and load balancing with a strict
priority — affinity wins, but only when the pinned node can actually serve
the expert this specific request needs, since a session's pin may be left
over from an earlier request to a different expert entirely. `HotExpertPolicy`
adds a replica of an overloaded expert reactively rather than provisioning
every expert for a worst case that may never come. `ExpertServingRunner`
drives all of it with real threads and real elapsed time; what an expert
actually computes is out of scope on purpose, the same way `ShardPlan` never
runs the training step it divides work for.

**Measured** (`SIPS-samples/ExpertServing`, macOS Intel i7-9750H, 300 turns
across 20 multi-turn sessions, one expert deliberately popular at 70% of
traffic):

```
run                             count     p50 ms     p99 ms affinity hit
static (1 replica each)           300       3.45      40.59        83.0%
adaptive (HotExpertPolicy)        300       3.26       3.87        89.0%
```

p99 latency improved roughly 10–12× across repeated runs once the hot
expert's queue depth crossed the rebalancing threshold and a second replica
absorbed the load — measured with the identical turn sequence and an
identical pause inserted at the identical point in both runs, so the
difference is the rebalance decision and not the pause itself giving the
pool a moment to catch up. One early run, before that control was in place,
showed almost no difference — kept in `SIPS-samples/ExpertServing/Readme.md`
rather than discarded, since a demo's own measurement bug is exactly the
kind of finding this project keeps.

### What's deliberately absent

- **No real network, no real expert models.** Every "expert" is a stand-in
  callback; every "node" is a label. What's real is the scheduling logic
  around them, which is this package's actual job.
- **No prioritisation among several simultaneously hot experts.**
  `HotExpertPolicy.rebalance` processes experts in whatever order it is
  given and adds at most one replica per over-threshold expert per call —
  it has no notion of ranking multiple hot experts against each other for a
  limited pool of spare capacity, and nothing in the current sample creates
  that contention to test against.
- **No session migration.** If the node a session is pinned to disappears
  mid-conversation, the session simply fails to route on its next turn.
  Recovering session state elsewhere is unstarted work.
- **No persistent session state.** `Session` carries only a pinned node uuid
  and a last-seen time — no conversation history, no KV-cache reference,
  nothing an expert would actually need to resume a conversation. Session
  *affinity* (routing) is what's built; session *state* (what to route to,
  and its recovery) is not.

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
hasn't proven anything.

**Measured** (`SIPS-samples/ElasticNode`, 5,000 accesses across six cheap,
popular "good" units and two expensive, unpopular "bad" ones sized so
promoting one "bad" unit evicts three "good" ones):

```
configuration                          total ms     promotions
no fast tier at all                       30000              0
naive (always promote)                    37484           2538
adaptive (cost-aware)                     10152            706
```

The naive always-promote policy is not merely worse than adaptive — it is
worse than having no fast tier at all (37,484ms vs. 30,000ms). Every access to
a "bad" unit evicts several "good" ones to make room, and those good units
then pay to be re-promoted the next time they're needed: classic thrashing,
produced by a workload built specifically to trigger it, not smoothed away by
picking only uniformly-good candidates. The adaptive policy checks the actual
numbers per access, correctly declines to promote either "bad" unit, and
keeps the popular units resident — a real 3.69× improvement over naive, not
an assumed one.

### What's deliberately absent

- **No real GPU, no real transfer, no real bandwidth measurement.** Every
  cost is a declared number, not measured off real hardware the way
  `WorkerBench` measures a node's own compute or the busy-endpoint findings
  measure a real link. A genuinely bandwidth-adaptive version would measure
  its own transfer rate rather than take it as a constructor argument.
- **No composition with the other two capabilities yet.** A node running an
  expert-serving replica or hosting a pipeline stage does not yet consult an
  `ElasticNodeCache` to decide what of its own workload stays resident — the
  three packages are real, tested, and independently useful, but nothing
  wires them into one system yet. That wiring is real, unstarted work, not
  a small addition: it would mean `ExpertServingRunner`'s `RequestExecutor`
  (or `PipelineRunner`'s `LayerExecutor`) consulting a per-node cache instead
  of assuming its work is always immediately ready.
- **Coarse-grained locking.** Every public method on `ElasticNodeCache` is
  `synchronized`, a real, stated bottleneck if this sits on a hot path under
  heavy concurrent access — correctness over performance was the right choice
  for a first version, not an oversight to leave undocumented.

## The negative finding, for the primer's own register

`PipelinePlan.assign` mirrors `ShardPlan`'s principle — balance discrete
resources by measured worth — applied to something `ShardPlan` never had to
handle: work that is not homogeneous. `ShardPlan` divides a single, uniform
batch of samples, so node speed alone is the whole story. A pipeline's stages
genuinely differ in cost, and node speed alone materially under-serves the
true bottleneck — measured directly above, not assumed. The fix is not a
bigger balancing formula; it is a missing input; a per-stage cost estimate,
measured the way this project measures everything else, rather than declared.
Recorded here because a refuted assumption about what "balance the cluster"
requires is worth exactly as much as a confirmed one, which is the whole
premise of keeping this register at all.

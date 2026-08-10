# The wire protocol

What one node may ask another to do, and how it finds out.

## The problem this solves

Nodes talk in JSON over sockets, and until protocol 1 every message assumed the
peer was running the same build. That held only because nobody had upgraded half
a cluster — which is the normal state during any rollout, and the one state
nobody tests.

The failures it produces are quiet. A node given a WebAssembly chunk it cannot
run reads the manifest expecting `MAIN`, does not find it, and throws. The chunk
is lost, the job slows down, and the reason appears in a log on the machine that
could not run it — the one place nobody looks.

## How a node announces itself

Every ping reply carries `PROTOCOL`:

```json
{
  "UUID": "…", "HOSTNAME": "node-3", "TASK_LIMIT": 8,
  "DEVICES": [ … ],
  "PROTOCOL": 1
}
```

Carried on the ping deliberately, rather than as a handshake of its own. Ping
already runs once per discovery cycle and already says what a node offers —
memory, accelerators, benchmark scores — so a version belongs beside them. A
handshake per connection would add a round trip to every chunk sent, on a path
where the whole point of the recent work was to make chunks cheap.

Task messages carry it too, so a node receiving work knows who is talking without
asking.

## `UNKNOWN` means unknown

A peer that sends no `PROTOCOL` is version **0**, and that is not the same as
"old but probably fine". Every release up to 1.2.4 is silent. Some of them
understand `breakAll`; there is no way to tell which. Guessing is the thing this
exists to stop.

## Two kinds of feature

This is the part that makes it negotiation rather than a version stamp. Not every
new message is dangerous to an older node:

| | An older node… | So a peer of unknown version… |
|---|---|---|
| `IGNORED_BY_OLDER` | drops the message | **gets it anyway** |
| `BREAKS_ON_OLDER` | accepts the work and fails it | **is left out** |

| Feature | Since | On an older node |
|---|---|---|
| `EARLY_EXIT` | 1 | ignored — the loop runs to completion, slower but correct |
| `INLINE_RESULTS` | 1 | ignored — the result is simply not collected that way |
| `WASM_TASKS` | 1 | **breaks** — no `MAIN` in the manifest, so it throws |
| `STAGED_JOBS` | 1 | **breaks** — takes the single-loop path and produces nothing |
| `FETCHED_RESULTS` | 2 | **breaks** — the command is unknown, so the node answers nothing and the master times out |

The effect is that a half-upgraded cluster keeps working for everything that
degrades safely, and only work that would actually be wasted is withheld.

## What happens to work that cannot be placed

Filtering happens when work is **scheduled**, not when it is sent — sending and
failing costs the whole round trip.

```java
Feature required = stage.taskType() == TaskType.WASM
        ? Feature.WASM_TASKS : Feature.STAGED_JOBS;
ConcurrentHashMap<String, Node> usable = NodeCapabilities.capableOf(required, live);
```

Nodes left out are named in the job log, with what to do about them:

```
2 of 5 nodes cannot run wasm tasks and were left out: node-4, node-7.
This node speaks protocol 0 (a release before protocol negotiation) and cannot
run wasm tasks, which needs protocol 1. Upgrade it, or the job will only use
nodes that can.
```

A cluster silently running at half its size is a performance mystery rather than
an obvious fault, so it says so. A fully upgraded cluster says nothing — a
warning printed every cycle is a warning nobody reads.

If **no** node can run the work, the stage fails immediately with that reason,
rather than waiting for a timeout.

## Adding a feature

1. Add it to `Protocol.Feature` with the version that introduces it, and — the
   part that takes thought — how an older node reacts to it.
2. Raise `Protocol.VERSION` if this is the first feature in a new version.
3. Filter with `NodeCapabilities.capableOf` wherever the work is scheduled.

A test asserts that no feature claims a version newer than the build speaks, so
raising one without the other fails the build.

## Rules

- **A newer peer is treated as current, not believed.** If a node announces a
  version this build has never heard of, it is read as this build's version. It
  knows about us; we know nothing about it. Staying compatible is the newer
  node's job.
- **Fields are additive.** `PROTOCOL` is read with a default, exactly as
  `DEVICES` was when it was added, so a node from before negotiation reads a new
  node's ping reply without noticing anything changed.
- **Never repurpose a feature name.** It is compared against what peers report.

## What this does not do

**It has not been run across two real builds.** The negotiation is tested — the
version reaches the wire through a real socket and a real handler, and the
filtering is tested against nodes that announce nothing — but an actual 1.2.4
node talking to an actual 1.2.5 node has never happened.

**There is no negotiation for the file transfer or API ports.** Only ping and
task distribution carry the version today.

**Old nodes cannot learn.** Negotiation only helps from the version that has it
onward. A cluster of 1.2.4 nodes gains nothing until the *other* side is
upgraded, because the information flows from the announcing node.

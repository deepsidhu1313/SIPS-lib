# Migrations

> **Why "migrations" at all?** A database schema is shared state between the
> code and the disk, and only the code gets upgraded by a release. A migration
> is a versioned, ordered, run-exactly-once script that carries an existing
> installation from the old shape to the new one; a ledger table records which
> have run, so upgrading is "run whatever the ledger has not seen, in order,
> each in a transaction". The alternative — code that guesses the schema shape
> at runtime — degrades into un-testable archaeology. This is the same design
> Laravel, Rails and Flyway converged on independently, which is usually a
> sign the shape is right.

How a schema or settings change reaches an installation that already exists.

## The short version

Run the node. That is the whole upgrade procedure.

`Settings.init()` migrates the databases and the settings files at every
startup. Against an up-to-date node it costs one query per database and changes
nothing, which is what lets it be unconditional — there is no upgrade step for
an operator to remember, run twice, or run out of order.

## Why this exists

Every long-lived table used to be created by a bare `CREATE TABLE` at the point
of first use. That works exactly once. On the second start the create fails,
because the table is there — and in the warehouses, the code took that failure
as a cue to **rename the existing database out of the way and begin an empty
one**. It read like log rotation. It was the failure branch of a create that was
never meant to fail, and it meant the history those tables exist to accumulate
never accumulated.

Adding a column would have been worse: the create fails, and then every insert
naming the new column fails too, on every existing installation, with nothing to
repair it.

## Writing a database migration

A migration is one change, with an id that never changes once it has shipped.

```java
static Migration addWattsToDistributionWarehouse() {
    return new Migration() {
        public String id() {
            return "2026_09_01_000001_add_watts_to_distwh";
        }

        public String description() {
            return "energy per chunk";
        }

        public void up(Connection database) throws SQLException {
            try (Statement statement = database.createStatement()) {
                statement.execute("ALTER TABLE DISTWH ADD COLUMN WATTS REAL");
            }
        }
    };
}
```

Then add it to the list in
[`NodeDatabases`](../../SIPS-Node/src/main/java/in/co/s13/SIPS/db/NodeDatabases.java):

```java
new Migrator().with(createDistributionWarehouse(), addWattsToDistributionWarehouse())
```

Order in that call does not matter — migrations are sorted by id, so two people
adding one on separate branches cannot get different results depending on how the
merge ordered them. Date-prefixing keeps new ones sorting last without anyone
maintaining a counter.

### The rules

- **Never edit a migration that has shipped.** It has already run somewhere.
  Editing it changes that database's recorded history, not its contents. Add
  another migration instead.
- **Do not read application state.** A migration runs at startup, in a state its
  author cannot see. Depend only on the schema the migrations before it left.
- **Assume it may run against an old installation.** Someone will upgrade across
  four releases at once, because people do.

### What you get

- **Each migration runs once.** Recorded by id in a `sips_migrations` table, so
  a restart, a reinstall and a multi-version upgrade all converge on the same
  schema.
- **All or nothing.** Each runs in its own transaction. A failure rolls that one
  back and stops, leaving the database at the last migration that succeeded —
  never half-way through one.
- **Retry by restarting.** A failed migration was never recorded, so fixing the
  cause and starting again is the whole recovery.

### What you do not get

There is no rollback. A `down()` that reverses a schema change can rarely reverse
the data change that came with it, and offering one invites treating it as a
safety net it is not. Restore a backup.

## Writing a settings migration

Adding a setting needs nothing. Every value is read as
`get(name, currentDefault)`, so a key an old file lacks falls back to the code.
That is why this went so long without existing.

What needs a step is anything that is **not** a pure addition:

| Change | Without a step |
|---|---|
| rename a key | the operator's value is silently dropped for a default |
| change units | the old number is read as the new unit — a thousand times off, and not obviously wrong |
| split one key into two | as above |

```java
static SettingsMigrator settingsMigrations() {
    return new SettingsMigrator()
            .step(1, "the ping delay is now milliseconds", settings -> {
                if (settings.has("PING_DELAY")) {
                    settings.put("PING_DELAY_MS", settings.getLong("PING_DELAY") * 1000);
                    settings.remove("PING_DELAY");
                }
                return settings;
            });
}
```

`SettingsMigrator.rename(from, to)` and `remove(key)` cover the common cases.

A file records how far it has come in `SETTINGS_VERSION`. A file written before
versioning existed has none, and is treated as version 0 — so every step runs
against it, in order.

## Which databases need this

| Database | Lifecycle | Migrations |
|---|---|---|
| `.parsed/`, `.simulated/` (per job) | deleted and rebuilt on every submission | **not needed** — the schema can change freely |
| `log/dw-dist.db` | accumulates across jobs and upgrades | **yes** |
| `log/dw-result.db` | accumulates across jobs and upgrades | **yes** |
| `data/<job>/dist-db/` | per job, but outlives it | **yes**, when it gains one |

The per-job databases are the exception worth knowing: `Visitor` deletes and
recreates them on every parse, so a change to the AST schema needs no migration
at all. Everything that outlives a single job does.

## What is not covered

**Scripts** are not migrated; they are overwritten. `ExecutorScripts.install()`
rewrites `bin/executor.*` and `bin/simulate.*` at every `TaskServer` start. A
script change therefore ships with a restart and needs nothing — but local edits
to those files are silently replaced. Put changes in the generator, not the
generated file.

**The wire protocol is versioned separately.** Nodes of different builds can talk
to each other: each announces what it speaks on every ping, and work an older one
would accept and then fail is scheduled onto the nodes that can run it. That is
its own mechanism, described in [PROTOCOL.md](PROTOCOL.md).

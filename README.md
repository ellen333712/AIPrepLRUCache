# lru-cache

lru-cache is a cache you build from scratch. A cache holds a fixed number of key/value pairs, and the rule that makes it interesting is what happens when it fills up. You read a value by its key, you write a key/value pair, and every time you touch an entry, reading it or writing it, that entry becomes the freshest one. When the cache is full and a brand new key comes in, the entry nobody has touched in the longest time gets dropped to make room. The catch is that both the read and the write have to stay O(1), so the structure underneath has to do real work.

## Your task

You have two jobs.

1. **Build the core and get it working.** A cache with a fixed capacity, a way to read a value by its key, and a way to insert or update a key/value pair. Reading or writing an entry makes it the most recently used. When an insert would push the cache past its capacity, evict the least recently used entry first. Both the read and the write need to run in O(1). This part is the floor, and most submissions get here with or without an AI.
2. **Push it further toward production readiness.** The interesting part is where you go from the basics. You won't get it fully production-ready in the time you have, and we don't expect you to. Treat this like real code you'd be happy to hand a teammate, decide what would matter most for putting it in front of real users, and push it as far as you can on the things you pick. What "production ready" means here, and how far you grow the functionality beyond the core, is yours to define. Be ready to walk through your plan and defend why you tackled what you did first.
   One thing that's *not* in scope is deployment. We're not hosting this anywhere, so skip Dockerfiles, CI, and infra and focus on the code itself.

We care more about your judgment on what to tackle first than about a long list of half-finished features.

## Time

Aim for **30 minutes**. This is practice, so going over is fine, but your submission records exactly how long you took, and that time factors into the evaluation.

## Tools

Use whatever you'd use day to day. AI assistants (Cursor, Copilot, ChatGPT, Claude), search, library docs, Stack Overflow. Anything goes. We want to see how you actually work, not how you perform without your normal tooling.

## Building it

This repo is intentionally empty except for this README, so you're starting from a blank page. Set it up in whatever language and framework you like, using your own toolchain (your own package manager, test runner, and build commands).

When you're done, run `npx @hellointerview/ai-coding submit` to submit your code.

## Git

This is a regular git repo, so use git however you like. Commit as you go, make branches, do whatever fits how you normally work. It won't get in the way and we're not going to fight your workflow.

The one thing to know is there's nowhere to push. When you run `npx @hellointerview/ai-coding submit` we bundle up your work and submit it for you, so commits are just for your own benefit, not how you turn the exercise in. Your full set of changes is included either way, whether you committed them or not.

## Java implementation

`LRUCache<K, V>` uses a hash map pointing to nodes that hold keys and values,
plus a doubly linked list ordered from most recently used at the front to least
recently used at the tail. Reads move an existing node to the front without
changing the map. Writes insert or update entries and refresh recency. Overflow
immediately removes the tail entry from both structures.

Reads and writes take expected O(1) time, with O(capacity) storage. Public methods
are synchronized so each operation updates the map and list atomically; sequences
of multiple calls are not atomic, and returned mutable values are not protected.
Keys must keep stable, well-behaved `equals()` and `hashCode()` implementations.

- `new LRUCache<K, V>(capacity)`: requires a positive integer capacity.
- `get(key)`: returns the value and refreshes recency, or returns `null` on a miss.
- `put(key, value)`: inserts or updates, evicting the oldest entry if needed.
- `size()`: returns occupancy without changing recency.

Null keys and values are rejected with `NullPointerException`; nonpositive
capacities are rejected with `IllegalArgumentException`.

## Files and verification

- `src/LRUCache.java`: generic cache implementation with explanatory comments.
- `src/Main.java`: runnable starter program that prints a greeting and numbers 1–5.
- `tests/LRUCacheTest.java`: 13 regression groups, including 15,552 exhaustive short histories.
- `tests/LRUCacheStressTest.java`: seeded workloads with an independent reference model and failure replay.
- `tests/LRUCacheConcurrencyTest.java`: seven coordinated races, linearizability checking, and sustained contention.
- `tests/LRUCacheMemoryTest.java`: bounded-heap payload churn and retention diagnostics.
- `tests/LRUCacheBenchmark.java`: informational timing samples across increasing capacities.
- `tests/CacheTestSupport.java`: assertions, the list-based reference model, and structural checks.
- `tests/RunCacheTests.java`: subprocess runner with hard deadlines and a 128 MiB heap per suite.
- `TEST_PLAN.md`: coverage details, accepted contracts, and test limitations.

Tests require only a JDK (Java 8 or later); no external test framework or dependencies
are needed. Checks remain enabled without `-ea`. Structural assertions inspect map
and list invariants under the cache lock without changing recency. These assertions
are intentionally tied to the chosen architecture; behavioral expectations come
from an independent ordered-list model.

Compile and run the full suite from the project root:

```sh
mkdir -p out
javac -Xlint:all -d out src/*.java tests/*.java
java -cp out RunCacheTests all
```

The full suite includes 1,020,000 seeded model-checked operations, 700 short race
histories, 480,000 contention iterations across eight workers, and 120,000 payload
insertions under a 128 MiB heap limit. It also enumerates 15,552 short histories.
Individual cache operations are checked for linearizability; multi-call sequences
are not promised to be atomic.

Other run options:

```sh
java -cp out RunCacheTests quick
java -cp out RunCacheTests stress
java -cp out RunCacheTests diagnostics
```

`quick` runs regressions, 20 short seeds, 70 race histories, and contention.
`stress` runs the million-operation model suite and memory churn. `diagnostics`
prints timing samples; it does not assert performance thresholds or prove O(1).
The full suite reports heap samples but does not require a particular GC schedule.

For custom stress runs, arguments are seed count, operations per seed, and first
seed. Model failures save full and reduced operation histories under `out/failures/`:

```sh
java -Xmx128m -cp out LRUCacheStressTest 100 10000 1000
java -Xmx128m -cp out LRUCacheStressTest --replay out/failures/stress-1000-full.txt
```

The replay path is an example; use the actual path reported on failure. Reduction
is bounded to 200 replays and is not guaranteed to produce the smallest history.
Direct suite invocations bypass the runner's process deadline. Concurrent failures
report operation intervals/results or propagate the original worker exception.

Running `java -cp out Main` only runs the starter program, not cache tests. In IntelliJ IDEA,
configure a project JDK; `src` is the source root and `tests` is the test source root.

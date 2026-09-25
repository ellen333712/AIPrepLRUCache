# Java LRU Cache Test Plan and Coverage

This plan incorporates the test sub-agent's review and is now implemented with
dependency-free Java test programs. Run `java -cp out RunCacheTests all` after
compiling as shown in README.md. The operation descriptions below refer to
`get`, `put`, and `size` on the implemented cache.

## Implemented suites

| Suite | Coverage |
| --- | --- |
| `LRUCacheTest` | 13 regression groups, all deterministic cases below, validation, supported values, collision-heavy keys, lookup exceptions, and 15,552 exhaustive histories |
| `LRUCacheStressTest` | 100 seeds x 10,000 operations; capacities 1, 2, 3, 16, 64; four workload modes; full-state checks after every operation; saved/reduced failures and replay |
| `LRUCacheConcurrencyTest` | 100 repetitions of seven race scenarios; exhaustive legal order search for each six-operation history; 480,000 iterations across eight workers and capacities 1, 3, 64 |
| `LRUCacheMemoryTest` | 120,000 payload insertions (~469 MiB cumulative allocation) with capacity 32 under the runner's 128 MiB heap; structural checks and post-warm-up heap samples |
| `LRUCacheBenchmark` | Separate informational read/update/overflow timings at capacities 16, 256, 4,096, 65,536 |

The runner also executes 20 short seeds (20,000 additional operations). Every suite
runs in a separate JVM with a deadline and heap limit. Nonzero exits, worker
exceptions, invariant failures, or timeouts fail the run. Tests do not rely on `-ea`.

## Required behavior

The README requires fixed capacity, key/value reads and writes, recency updates
on reads of existing entries and on writes, eviction of the least recently used
entry when inserting a new key into a full cache, and O(1) reads and writes.

## Deterministic correctness cases

Each row starts with a fresh cache. Letters represent distinct keys.

| Scenario | Operations | Expected result |
| --- | --- | --- |
| Below capacity | Capacity 3; write A, B | Both retained |
| Exactly full | Capacity 3; write A, B, C | All retained; no early eviction |
| First overflow | Capacity 3; write A, B, C, D | A evicted; B, C, D retained |
| Read refresh | Capacity 3; write A, B, C; read A; write D | B evicted |
| Update refresh | Capacity 3; write A, B, C; update A; write D | B evicted; A has its updated value |
| Update while full | Capacity 3; write A, B, C; update B | No eviction; occupancy remains 3 |
| Same-value write | Capacity 2; write A, B; write A with the same value; write C | B evicted |
| Read newest repeatedly | Capacity 2; write A, B; read B repeatedly; write C | A evicted |
| Capacity one | Write A; update A; write B; write A | Only one entry retained after each operation |
| Reinsert evicted key | Capacity 2; write A, B, C, A | Final entries are C and A, with A newest |
| Eviction cascade | Capacity 3; write many unique keys | Only the latest three retained |
| Repeated reads | Fill cache; repeatedly read existing entries | Values preserved; occupancy unchanged |
| Independent instances | Operate on two caches with overlapping keys | Values and recency remain independent |

Assertions that read entries also change recency. Include assertion reads in the
reference history, or use fresh caches for independent checks. Use only the
public behavior for scenario assertions. Additional structural checks now inspect
the specified map/list implementation under its monitor: forward/back links,
cycles, map/list identity, occupancy, and exact recency/value order. These checks
must be updated if the chosen internal architecture changes.

## Model-based tests and stress workloads

- Use a simple ordered-list reference model independent of the cache implementation.
  Compare operation results and observable state throughout reproducible histories.
- Exercise capacities 1, 2, 3, and larger sizes. Use key sets below, at, and far
  above capacity. Include all-insert, all-update, read-heavy, and mixed workloads.
- Bias operations toward oldest/newest entries, updates while full, recently
  evicted keys, and repeated access to the same key.
- Check occupancy never exceeds capacity, each key has one logical entry,
  updates preserve occupancy, and overflow evicts exactly the expected entry.
- Start with short deterministic histories in routine tests. For a separately
  invoked stress suite, propose 100 recorded seeds with 10,000 operations each;
  tune the budget once runtime is measurable. Save seeds, capacities, operation
  histories, and reduced failing sequences for reproduction. The implementation
  limits reduction to 200 replays; global minimality is not guaranteed.
- Cycle many more unique keys than capacity to detect stale references and
  unbounded retention. Compare retained heap across windows after JVM warm-up,
  accounting for garbage collection and allocation noise; set thresholds after
  establishing a baseline. Current tests print heap samples without a byte-growth
  assertion; retained map/list entries are checked exactly and churn runs with a
  bounded heap. This detects major retention regressions, not every possible leak.
- Benchmark reads, updates, and overflow insertions across increasing capacities.
  Timing trends support complexity analysis but cannot prove O(1); inspect the
  algorithm as well. Avoid absolute timing assertions in correctness tests.

## Concurrency tests

Use Java barriers or latches, bounded timeouts, and repeatable workloads rather
than sleeps to coordinate workers. Check both atomicity and cross-thread visibility
under the agreed concurrency contract. Proposed race scenarios:

- Two inserts competing for the final free slot.
- Two distinct new-key inserts into an already full cache.
- A read refreshing the oldest entry while an insert may evict it.
- An update racing with eviction of the same key.
- Concurrent first inserts and updates to the same key.
- Capacity-one contention and many workers sharing a tiny cache.
- Readers/writers sharing hot keys while other writers churn through cold keys.

Verify the agreed behavior without requiring a specific ordering for overlapping
operations. Propagate worker exceptions to the test runner and bound worker joins.
Use a process-level timeout for tests that could deadlock, so a stuck worker cannot
hang the suite. Check for corruption, duplicate logical entries, capacity
violations, unexpected exceptions, and failure to terminate.

The synchronized public operations promise per-operation atomicity. Record logical
invocation/completion timestamps, arguments,
and results for short concurrent histories. Check that at least one serial order
consistent with real-time precedence matches the reference model. Keep histories
small enough for exhaustive checking. Longer contention tests supplement these
checks but do not prove race freedom. Add controlled internal interleavings only
if a future design provides suitable test hooks. Current tests use a start barrier
and do not force a particular interleaving inside methods. The history checker is
also checked against valid overlapping histories and deliberately impossible ones.

## Accepted contracts and limits

- API: `LRUCache<K,V>(int capacity)`, `get`, `put`, `size`.
- Nonpositive capacity is rejected. `Integer.MAX_VALUE` is accepted without eager
  allocation. Java rejects incompatible capacity argument types at compile time.
- Missing reads return `null` and leave recency unchanged. Null keys/values are
  rejected before mutation. Zero, `false`, and empty strings are valid values.
- Equal-but-distinct keys and hash collisions work. Values retain reference
  semantics; callers coordinate mutation of returned objects themselves.
- Cached keys must retain stable, well-behaved hashing/equality. Tests check that
  failures during an incoming key's initial lookup preserve state. Recovery from
  keys that mutate their hash, throw midway through an operation, or reenter the
  cache is outside the current contract; no blanket exception-safety claim is made.
- Single operations are synchronized and linearizable. Multi-call transactions
  are not supported. Successful stress runs are evidence, not proof of race freedom.
- No tests assume unimplemented deletion, resizing, iteration, callbacks, or metrics.
- Timing samples are diagnostic only. Code inspection supports expected O(1)
  hash lookup plus constant link updates; hash collisions and scheduling can affect
  measured time, and the tests do not assert a strict worst-case O(1) guarantee.

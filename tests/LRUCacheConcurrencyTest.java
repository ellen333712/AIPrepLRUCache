import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinated races, bounded linearizability checking, and sustained contention. */
public class LRUCacheConcurrencyTest {
    public static void main(String[] args) throws Exception {
        // Check the history oracle itself before trusting it to validate concurrent cache operations.
        oracleRejectsInvalidHistories();
        int repeats = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        CacheTestSupport.check(repeats > 0, "Repeat count must be positive");
        ExecutorService workers = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "cache-test-worker");
            thread.setDaemon(true); // Failed workers cannot keep the child JVM alive.
            return thread;
        });
        try {
            for (int repeat = 0; repeat < repeats; repeat++) {
                for (int scenario = 0; scenario < 7; scenario++) shortHistory(workers, scenario);
            }
            for (int capacity : new int[] {1, 3, 64}) contention(workers, capacity);
            System.out.println("Passed " + (7 * repeats) + " linearizable race histories and 480,000 contention iterations.");
        } finally {
            workers.shutdownNow();
            CacheTestSupport.check(workers.awaitTermination(5, TimeUnit.SECONDS), "Workers failed to terminate");
        }
    }

    private static void shortHistory(ExecutorService workers, int scenario) throws Exception {
        // Start competing operations together; accept any serial order allowed by their intervals.
        int capacity = scenario == 6 ? 1 : 2;
        LRUCache<Integer, Integer> cache = new LRUCache<>(capacity);
        CacheTestSupport.Model initial = new CacheTestSupport.Model(capacity);
        if (scenario != 4) seed(cache, initial, 0, 10);
        if (scenario >= 1 && scenario <= 3 || scenario == 5) seed(cache, initial, 1, 20);
        CacheTestSupport.Op left;
        CacheTestSupport.Op right;
        switch (scenario) {
            case 0: // Competing for the last free slot.
            case 1: // Both inserts start with a full cache.
                left = op('P', 2, 30); right = op('P', 3, 40); break;
            case 2: // Read-refresh races with eviction.
                left = op('G', 0, 0); right = op('P', 2, 30); break;
            case 3: // Update races with eviction.
                left = op('P', 0, 11); right = op('P', 2, 30); break;
            case 4: // Same-key first insertion.
            case 5: // Same-key existing update.
                left = op('P', 0, 11); right = op('P', 0, 12); break;
            default: // Every distinct write evicts at capacity one.
                left = op('P', 1, 20); right = op('P', 2, 30);
        }
        CyclicBarrier start = new CyclicBarrier(2);
        AtomicLong clock = new AtomicLong();
        List<Future<List<Event>>> futures = new ArrayList<>();
        for (CacheTestSupport.Op first : new CacheTestSupport.Op[] {left, right}) {
            futures.add(workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                List<Event> events = new ArrayList<>();
                for (CacheTestSupport.Op operation : new CacheTestSupport.Op[] {
                        first, op('G', 0, 0), op('S', 0, 0)}) {
                    long invoked = clock.incrementAndGet();
                    Object result = operation.apply(cache);
                    long completed = clock.incrementAndGet();
                    events.add(new Event(operation, invoked, completed, result));
                }
                return events;
            }));
        }
        List<Event> history = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (Future<List<Event>> future : futures) history.addAll(await(future, deadline));
        List<Object> finalState = CacheTestSupport.snapshot(cache, capacity);
        CacheTestSupport.check(linearizable(history, initial, finalState),
                "Nonlinearizable scenario " + scenario + ": " + history + "; final=" + finalState);
    }

    private static void contention(ExecutorService workers, int capacity) throws Exception {
        // Mix shared hot-key access with distinct-key churn; values identify their keys.
        LRUCache<Integer, Integer> cache = new LRUCache<>(capacity);
        CyclicBarrier start = new CyclicBarrier(8);
        List<Future<Void>> futures = new ArrayList<>();
        for (int worker = 0; worker < 8; worker++) {
            final int id = worker;
            futures.add(workers.submit(() -> {
                Random random = new Random(1_000L * capacity + id);
                start.await(5, TimeUnit.SECONDS);
                for (int step = 0; step < 20_000; step++) {
                    int key = id < 2 ? 1_000 + id * 20_000 + step : random.nextInt(8);
                    if (id < 2 || random.nextBoolean()) cache.put(key, key);
                    else {
                        Integer value = cache.get(key);
                        CacheTestSupport.check(value == null || value == key, "Corrupted value for " + key);
                    }
                    int size = cache.size();
                    CacheTestSupport.check(size >= 0 && size <= capacity, "Invalid concurrent occupancy");
                    if (step % 257 == 0) CacheTestSupport.snapshot(cache, capacity);
                }
                return null;
            }));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (Future<Void> future : futures) await(future, deadline);
        CacheTestSupport.snapshot(cache, capacity);
        // After quiescence, fresh inserts must evict every entry left by the workers.
        for (int key = -1; key >= -capacity; key--) cache.put(key, key);
        for (int key = -1; key >= -capacity; key--) CacheTestSupport.equal(key, cache.get(key));
        CacheTestSupport.equal(capacity, cache.size());
    }

    private static <T> T await(Future<T> future, long deadline) throws Exception {
        // Propagate worker exceptions and use one shared deadline for a whole group.
        long remaining = deadline - System.nanoTime();
        CacheTestSupport.check(remaining > 0, "Worker group timed out");
        return future.get(remaining, TimeUnit.NANOSECONDS);
    }

    private static void seed(LRUCache<Integer, Integer> cache, CacheTestSupport.Model model, int key, int value) {
        // Give the cache and reference model identical known starting states.
        CacheTestSupport.Op operation = op('P', key, value);
        operation.apply(cache);
        model.apply(operation);
    }

    private static CacheTestSupport.Op op(char kind, int key, int value) {
        return new CacheTestSupport.Op(kind, key, value);
    }

    private static final class Event {
        final CacheTestSupport.Op operation;
        final long start;
        final long end;
        final Object result;
        Event(CacheTestSupport.Op operation, long start, long end, Object result) {
            this.operation = operation;
            this.start = start;
            this.end = end;
            this.result = result;
        }
        @Override public String toString() {
            return operation + " [" + start + "," + end + "] -> " + result;
        }
    }

    private static boolean linearizable(List<Event> history, CacheTestSupport.Model initial, List<Object> finalState) {
        // Search all permitted serializations of these deliberately small histories.
        return search(history, initial, finalState, 0);
    }

    private static boolean search(List<Event> history, CacheTestSupport.Model model, List<Object> finalState, int used) {
        // Only choose an operation after every real-time predecessor has been chosen.
        if (used == (1 << history.size()) - 1) return model.state().equals(finalState);
        for (int index = 0; index < history.size(); index++) {
            if ((used & (1 << index)) != 0) continue;
            Event candidate = history.get(index);
            boolean ready = true;
            for (int other = 0; other < history.size(); other++) {
                if ((used & (1 << other)) == 0 && history.get(other).end < candidate.start) ready = false;
            }
            if (!ready) continue;
            CacheTestSupport.Model branch = model.copy();
            if (Objects.equals(candidate.result, branch.apply(candidate.operation))
                    && search(history, branch, finalState, used | (1 << index))) return true;
        }
        return false;
    }

    private static void oracleRejectsInvalidHistories() {
        // Confirm the checker respects real-time order and does not accept fabricated values.
        CacheTestSupport.Model empty = new CacheTestSupport.Model(1);
        List<Event> history = new ArrayList<>();
        history.add(new Event(op('P', 0, 10), 1, 2, null));
        history.add(new Event(op('G', 0, 0), 3, 4, null));
        CacheTestSupport.Model full = empty.copy();
        full.apply(op('P', 0, 10));
        CacheTestSupport.check(!linearizable(history, empty, full.state()), "Accepted impossible miss");
        history.set(1, new Event(op('G', 0, 0), 3, 4, 10));
        CacheTestSupport.check(linearizable(history, empty, full.state()), "Rejected valid history");
        history.set(1, new Event(op('G', 0, 0), 1, 4, null));
        CacheTestSupport.check(linearizable(history, empty, full.state()), "Rejected valid overlapping miss");
        history.set(1, new Event(op('G', 0, 0), 1, 4, 999));
        CacheTestSupport.check(!linearizable(history, empty, full.state()), "Accepted fabricated value");
    }
}

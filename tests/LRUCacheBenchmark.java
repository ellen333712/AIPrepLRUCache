/** Informational scaling samples; these timings are not correctness thresholds. */
public class LRUCacheBenchmark {
    private static volatile long sink;

    public static void main(String[] args) {
        // Warm the JVM and print operation costs over increasing capacities without pass/fail timing gates.
        System.out.println("Informational ns/op; JVM/GC noise applies, timings do not prove O(1).");
        for (int capacity : new int[] {16, 256, 4_096, 65_536}) {
            sample(capacity, 100_000, false);
            sample(capacity, 500_000, true);
        }
    }

    private static void sample(int capacity, int count, boolean report) {
        // Measure reads, existing-key updates, and distinct-key overflow in separate loops.
        LRUCache<Integer, Integer> cache = new LRUCache<>(capacity);
        for (int key = 0; key < capacity; key++) cache.put(key, key);
        long sum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) sum += cache.get(i % capacity);
        long reads = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < count; i++) cache.put(i % capacity, i);
        long updates = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < count; i++) cache.put(capacity + i, i);
        long inserts = System.nanoTime() - start;
        sink = sum;
        if (report) System.out.printf("capacity=%d get=%.1f update=%.1f overflow=%.1f%n",
                capacity, (double) reads / count, (double) updates / count, (double) inserts / count);
    }
}

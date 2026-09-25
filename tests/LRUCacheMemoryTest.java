import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/** Bounded-heap churn plus structural retention checks and heap diagnostics. */
public class LRUCacheMemoryTest {
    public static void main(String[] args) {
        // Allocate far more than the runner's heap limit while retaining only a fixed live set.
        int capacity = 32;
        LRUCache<Integer, byte[]> cache = new LRUCache<>(capacity);
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        for (int window = 0; window < 6; window++) {
            for (int offset = 0; offset < 20_000; offset++) {
                int key = window * 20_000 + offset;
                byte[] payload = new byte[4_096];
                payload[0] = (byte) key;
                cache.put(key, payload);
                if (offset % 1_000 == 0) CacheTestSupport.snapshot(cache, capacity);
            }
            CacheTestSupport.equal(capacity, cache.size());
            CacheTestSupport.snapshot(cache, capacity);
            int newest = (window + 1) * 20_000 - 1;
            CacheTestSupport.equal(null, cache.get(newest - capacity));
            for (int key = newest - capacity + 1; key <= newest; key++) {
                CacheTestSupport.equal((byte) key, cache.get(key)[0]);
            }
            // GC is a hint, not a correctness assertion; report warmed windows without a flaky threshold.
            System.gc();
            System.out.println((window == 0 ? "Warm-up" : "Window " + window)
                    + " used heap bytes: " + memory.getHeapMemoryUsage().getUsed());
        }
        System.out.println("Passed 120,000 payload inserts (~469 MiB allocated) with a 32-entry live set.");
    }
}

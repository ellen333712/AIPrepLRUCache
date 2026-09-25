import java.util.Objects;

/** Dependency-free regression checks; run with {@code java -cp out LRUCacheTest}. */
public class LRUCacheTest {
    public static void main(String[] args) {
        // Run focused checks for the implemented contract; failures throw AssertionError.
        fillingAndEviction();
        readsRefreshRecency();
        updatesRefreshWithoutGrowing();
        capacityOne();
        repeatedEviction();
        validationPreservesState();
        collidingKeys();
        independentInstances();
        reinsertionAndReadOnlyWorkload();
        supportedValuesAndLargeCapacity();
        manyCollisions();
        exceptionsDuringLookup();
        exhaustiveShortHistories();
        System.out.println("Passed 13 regression groups, including 15,552 exhaustive histories.");
    }

    private static void fillingAndEviction() {
        // Fill exactly to capacity, then check that overflow removes only the oldest key.
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        equal(0, cache.size());
        equal(null, cache.get("missing"));
        cache.put("A", 1);
        cache.put("B", 2);
        equal(2, cache.size());
        cache.put("C", 3);
        equal(3, cache.size());
        equal(null, cache.get("missing"));
        cache.put("D", 4);
        equal(3, cache.size());
        equal(null, cache.get("A"));
        equal(2, cache.get("B"));
        equal(3, cache.get("C"));
        equal(4, cache.get("D"));
    }

    private static void readsRefreshRecency() {
        // Promote a tail and a middle node, then repeatedly read the head before eviction.
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        equal(1, cache.get("A"));
        equal(3, cache.get("C"));
        equal(3, cache.get("C"));
        cache.put("D", 4);
        equal(null, cache.get("B"));
        equal(1, cache.get("A"));
        equal(3, cache.size());
    }

    private static void updatesRefreshWithoutGrowing() {
        // Updating an existing key, even to the same value, refreshes it without eviction.
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("A", 10);
        equal(2, cache.size());
        equal(2, cache.get("B"));
        cache.put("A", 10);
        cache.put("C", 3);
        equal(null, cache.get("B"));
        equal(10, cache.get("A"));
        equal(3, cache.get("C"));
        equal(2, cache.size());
    }

    private static void capacityOne() {
        // Exercise updates and eviction when the same node is both head and tail.
        LRUCache<String, Integer> cache = new LRUCache<>(1);
        cache.put("A", 1);
        cache.put("A", 2);
        equal(2, cache.get("A"));
        cache.put("B", 3);
        equal(null, cache.get("A"));
        cache.put("A", 4);
        equal(null, cache.get("B"));
        equal(4, cache.get("A"));
        equal(1, cache.size());
    }

    private static void repeatedEviction() {
        // Churn through distinct keys and check capacity and immediate map cleanup.
        LRUCache<Integer, Integer> cache = new LRUCache<>(3);
        for (int key = 0; key < 1_000; key++) {
            cache.put(key, key);
            equal(Math.min(key + 1, 3), cache.size());
            if (key >= 3) {
                equal(null, cache.get(key - 3));
            }
        }
        equal(997, cache.get(997));
        equal(998, cache.get(998));
        equal(999, cache.get(999));
    }

    private static void validationPreservesState() {
        // Reject invalid capacities and null inputs before they can change cache contents.
        throwsType(IllegalArgumentException.class, () -> new LRUCache<>(0));
        throwsType(IllegalArgumentException.class, () -> new LRUCache<>(-1));
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        cache.put("A", 1);
        cache.put("B", 2);
        throwsType(NullPointerException.class, () -> cache.get(null));
        throwsType(NullPointerException.class, () -> cache.put(null, 3));
        throwsType(NullPointerException.class, () -> cache.put("A", null));
        equal(2, cache.size());
        cache.put("C", 3);
        equal(null, cache.get("A"));
        equal(2, cache.get("B"));
        equal(3, cache.get("C"));
    }

    private static void collidingKeys() {
        // These unequal strings share a hash; equal-but-distinct keys must still update.
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        equal("Aa".hashCode(), "BB".hashCode());
        cache.put("Aa", 1);
        cache.put("BB", 2);
        cache.put(new String("Aa"), 3);
        equal(2, cache.size());
        cache.put("C", 4);
        equal(null, cache.get("BB"));
        equal(3, cache.get("Aa"));
    }

    private static void independentInstances() {
        // Verify that separate caches do not share values or eviction state.
        LRUCache<String, Integer> first = new LRUCache<>(1);
        LRUCache<String, Integer> second = new LRUCache<>(1);
        first.put("A", 1);
        second.put("A", 2);
        first.put("B", 3);
        equal(null, first.get("A"));
        equal(2, second.get("A"));
    }

    private static void reinsertionAndReadOnlyWorkload() {
        // Reinsert an evicted key and repeatedly access survivors without increasing size.
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        cache.put("A", 4);
        equal(null, cache.get("B"));
        for (int i = 0; i < 1_000; i++) {
            equal(3, cache.get("C"));
            equal(4, cache.get("A"));
            equal(2, cache.size());
        }
    }

    private static void supportedValuesAndLargeCapacity() {
        // Verify falsey values, reference semantics, and a large limit without eager allocation.
        throwsType(IllegalArgumentException.class, () -> new LRUCache<>(Integer.MIN_VALUE));
        LRUCache<String, Object> cache = new LRUCache<>(Integer.MAX_VALUE);
        cache.put("", 0);
        cache.put("false", false);
        cache.put("empty", "");
        StringBuilder mutable = new StringBuilder("a");
        cache.put("mutable", mutable);
        mutable.append("b");
        equal(0, cache.get(""));
        equal(false, cache.get("false"));
        equal("", cache.get("empty"));
        CacheTestSupport.check(cache.get("mutable") == mutable, "Value should retain identity");
        equal("ab", cache.get("mutable").toString());
        equal(4, cache.size());
    }

    private static void manyCollisions() {
        // Exercise many equal-hash keys and fresh equal instances during lookup and eviction.
        LRUCache<CollisionKey, Integer> cache = new LRUCache<>(32);
        for (int i = 0; i < 100; i++) cache.put(new CollisionKey(i), i);
        for (int i = 0; i < 68; i++) equal(null, cache.get(new CollisionKey(i)));
        for (int i = 68; i < 100; i++) equal(i, cache.get(new CollisionKey(i)));
        CacheTestSupport.snapshot(cache, 32);
    }

    private static final class CollisionKey {
        final int id;
        CollisionKey(int id) { this.id = id; }
        @Override public int hashCode() { return 7; }
        @Override public boolean equals(Object other) {
            return other instanceof CollisionKey && ((CollisionKey) other).id == id;
        }
    }

    private static void exceptionsDuringLookup() {
        // A failing incoming lookup must not mutate an otherwise healthy cache.
        LRUCache<Object, Integer> cache = new LRUCache<>(2);
        cache.put("A", 1);
        cache.put("B", 2);
        Object badHash = new Object() {
            @Override public int hashCode() { throw new IllegalStateException("hash failure"); }
        };
        Object badEquals = new Object() {
            @Override public int hashCode() { return "A".hashCode(); }
            @Override public boolean equals(Object other) { throw new IllegalStateException("equals failure"); }
        };
        throwsType(IllegalStateException.class, () -> cache.get(badHash));
        throwsType(IllegalStateException.class, () -> cache.put(badHash, 3));
        throwsType(IllegalStateException.class, () -> cache.get(badEquals));
        throwsType(IllegalStateException.class, () -> cache.put(badEquals, 3));
        cache.put("C", 3);
        equal(null, cache.get("A"));
        equal(2, cache.get("B"));
        equal(3, cache.get("C"));
    }

    private static void exhaustiveShortHistories() {
        // Enumerate all length-five histories of reads/writes over three keys at tiny capacities.
        for (int capacity : new int[] {1, 2}) {
            for (int encoded = 0; encoded < 7_776; encoded++) {
                int remaining = encoded;
                LRUCache<Integer, Integer> cache = new LRUCache<>(capacity);
                CacheTestSupport.Model model = new CacheTestSupport.Model(capacity);
                for (int step = 0; step < 5; step++) {
                    int choice = remaining % 6;
                    remaining /= 6;
                    CacheTestSupport.Op op = new CacheTestSupport.Op(choice < 3 ? 'P' : 'G', choice % 3, step);
                    equal(model.apply(op), op.apply(cache));
                    equal(model.state(), CacheTestSupport.snapshot(cache, capacity));
                }
            }
        }
    }

    private static void equal(Object expected, Object actual) {
        // Keep checks active even when Java's optional assertions are disabled.
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void throwsType(Class<? extends Throwable> type, Runnable action) {
        // Verify validation failures have the documented exception type.
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName(), failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}

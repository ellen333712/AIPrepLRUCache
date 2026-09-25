import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared assertions, an independent list model, and structural checks. */
final class CacheTestSupport {
    private CacheTestSupport() { }

    static void check(boolean condition, String message) {
        // Fail regardless of whether the JVM enables language-level assertions.
        if (!condition) throw new AssertionError(message);
    }

    static void equal(Object expected, Object actual) {
        // Compare observable values with a useful failure description.
        check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual);
    }

    static final class Op {
        final char kind;
        final int key;
        final int value;

        Op(char kind, int key, int value) {
            this.kind = kind;
            this.key = key;
            this.value = value;
        }

        Object apply(LRUCache<Integer, Integer> cache) {
            // Execute precisely one public cache operation.
            if (kind == 'G') return cache.get(key);
            if (kind == 'S') return cache.size();
            if (kind != 'P') throw new IllegalArgumentException("Unknown operation " + kind);
            cache.put(key, value);
            return null;
        }

        @Override public String toString() { return kind + " " + key + " " + value; }
    }

    static final class Model {
        final int capacity;
        final List<Integer> keys = new ArrayList<>(); // Most recent first.
        final List<Integer> values = new ArrayList<>();

        Model(int capacity) { this.capacity = capacity; }

        Model copy() {
            // Branch the slow reference model for concurrent-history exploration.
            Model result = new Model(capacity);
            result.keys.addAll(keys);
            result.values.addAll(values);
            return result;
        }

        Object apply(Op op) {
            // Use linear searches and array-list moves, independently of cache internals.
            if (op.kind == 'S') return keys.size();
            int index = keys.indexOf(op.key);
            if (op.kind == 'G' && index < 0) return null;
            Integer value = op.kind == 'G' ? values.get(index) : op.value;
            if (index >= 0) {
                keys.remove(index);
                values.remove(index);
            }
            keys.add(0, op.key);
            values.add(0, value);
            if (keys.size() > capacity) {
                keys.remove(keys.size() - 1);
                values.remove(values.size() - 1);
            }
            return op.kind == 'G' ? value : null;
        }

        List<Object> state() {
            // Encode recency and values without executing reads that would change order.
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                result.add(keys.get(i));
                result.add(values.get(i));
            }
            return result;
        }
    }

    static List<Object> snapshot(LRUCache<?, ?> cache, int capacity) {
        // Inspect this chosen architecture under its lock, without changing recency.
        synchronized (cache) {
            try {
                Map<?, ?> entries = (Map<?, ?>) field(cache, "entries");
                Object head = field(cache, "head");
                Object tail = field(cache, "tail");
                check(field(head, "previous") == null, "Head has an outer link");
                check(field(tail, "next") == null, "Tail has an outer link");
                Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                List<Object> state = new ArrayList<>();
                Object previous = head;
                Object node = field(head, "next");
                while (node != tail) {
                    check(node != null && seen.add(node), "Broken link or cycle");
                    check(seen.size() <= capacity, "List exceeds capacity");
                    check(field(node, "previous") == previous, "Broken backward link");
                    Object key = field(node, "key");
                    check(entries.get(key) == node, "Map/list node mismatch");
                    state.add(key);
                    state.add(field(node, "value"));
                    previous = node;
                    node = field(node, "next");
                }
                check(field(tail, "previous") == previous, "Broken tail link");
                equal(entries.size(), seen.size());
                equal(entries.size(), cache.size());
                check(entries.size() <= capacity, "Map exceeds capacity");
                for (Object entry : entries.values()) check(seen.contains(entry), "Unlinked map node");
                return state;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Update structural checks if cache internals change", failure);
            }
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        // Read implementation fields only in this optional structural test layer.
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}

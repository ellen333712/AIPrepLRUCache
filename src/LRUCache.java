import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A fixed-capacity cache with least-recently-used eviction.
 *
 * <p>The map points to nodes containing the cached keys and values. A doubly
 * linked list orders those nodes from most recently used to least recently used.
 * Successful reads and all writes refresh recency; misses leave it unchanged.
 * Reads and writes take expected O(1) time, and storage is O(capacity).
 *
 * <p>Public operations synchronize on this cache so map and list changes are
 * atomic and visible across threads. Multiple calls are not one atomic operation,
 * and mutable values returned to callers are not protected by this lock.
 * Keys must keep stable, well-behaved {@code equals} and {@code hashCode} methods
 * while cached. Null keys and values are rejected so null unambiguously means a miss.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> entries = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);

    /**
     * Creates an empty cache that retains at most {@code capacity} entries.
     *
     * @throws IllegalArgumentException if capacity is zero or negative
     */
    public LRUCache(int capacity) {
        // Validate the limit and connect sentinels to represent an empty list.
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }
        this.capacity = capacity;
        head.next = tail;
        tail.previous = head;
    }

    /**
     * Returns the value and marks it most recently used, or returns null on a miss.
     *
     * @throws NullPointerException if key is null
     */
    public synchronized V get(K key) {
        // Find the existing node and refresh only its list position, not the map.
        Objects.requireNonNull(key, "Key must not be null");
        Node<K, V> node = entries.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.value;
    }

    /**
     * Inserts or updates an entry and marks it most recently used.
     * A new entry exceeding capacity immediately evicts the least recently used.
     *
     * @throws NullPointerException if key or value is null
     */
    public synchronized void put(K key, V value) {
        // Update an existing node, or insert a new one and evict any overflow.
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Node<K, V> node = entries.get(key);
        if (node != null) {
            node.value = value;
            moveToFront(node);
            return;
        }

        node = new Node<>(key, value);
        entries.put(key, node);
        addToFront(node);
        if (entries.size() > capacity) {
            Node<K, V> leastRecentlyUsed = tail.previous;
            entries.remove(leastRecentlyUsed.key);
            unlink(leastRecentlyUsed);
        }
    }

    /** Returns the current number of entries without changing recency. */
    public synchronized int size() {
        // Read occupancy while holding the same lock used by reads and writes.
        return entries.size();
    }

    private void moveToFront(Node<K, V> node) {
        // Detach an existing entry before making it the most recently used.
        unlink(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        // Insert immediately after the head sentinel, updating both directions.
        node.previous = head;
        node.next = head.next;
        head.next.previous = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        // Join the neighbors and release this node's links to the list.
        node.previous.next = node.next;
        node.next.previous = node.previous;
        node.previous = null;
        node.next = null;
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> previous;
        private Node<K, V> next;

        private Node(K key, V value) {
            // Keep the key for map removal when this entry is evicted.
            this.key = key;
            this.value = value;
        }
    }
}

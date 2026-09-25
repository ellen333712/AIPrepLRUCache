import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded reference-model tests with saved and reduced failure histories. */
public class LRUCacheStressTest {
    public static void main(String[] args) throws Exception {
        // Run a fixed reproducible seed range, or replay a previously saved failure file.
        if (args.length == 2 && args[0].equals("--replay")) {
            replayFile(Paths.get(args[1]));
            return;
        }
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int operations = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        long firstSeed = args.length > 2 ? Long.parseLong(args[2]) : 0;
        CacheTestSupport.check(seeds > 0 && operations > 0, "Counts must be positive");
        int[] capacities = {1, 2, 3, 16, 64};
        for (int run = 0; run < seeds; run++) {
            long seed = firstSeed + run;
            int capacity = capacities[(int) Math.floorMod(seed, (long) capacities.length)];
            int mode = (int) Math.floorMod(seed / capacities.length, 4L);
            List<CacheTestSupport.Op> history = generate(seed, capacity, operations, mode);
            try {
                replay(capacity, history);
            } catch (AssertionError failure) {
                Path full = save(seed, capacity, history, "full");
                Path reduced = save(seed, capacity, reduce(capacity, history), "reduced");
                throw new AssertionError("seed=" + seed + ", capacity=" + capacity
                        + ", mode=" + mode + "; replay " + full + "; reduced " + reduced, failure);
            }
        }
        System.out.println("Passed " + seeds + " seeds x " + operations + " operations ("
                + ((long) seeds * operations) + " model-checked operations), starting seed " + firstSeed + ".");
    }

    private static List<CacheTestSupport.Op> generate(long seed, int capacity, int count, int mode) {
        // Mix hot keys, oldest/newest entries, evicted keys, and key sets around capacity.
        Random random = new Random(seed);
        CacheTestSupport.Model model = new CacheTestSupport.Model(capacity);
        List<CacheTestSupport.Op> history = new ArrayList<>();
        int[] domains = {Math.max(1, capacity / 2), capacity, capacity * 8};
        Integer lastEvicted = null;
        for (int step = 0; step < count; step++) {
            int domain = domains[(step / 100) % domains.length];
            int key = random.nextInt(domain);
            int bias = random.nextInt(5);
            if (!model.keys.isEmpty() && bias == 0) key = model.keys.get(0);
            if (!model.keys.isEmpty() && bias == 1) key = model.keys.get(model.keys.size() - 1);
            if (lastEvicted != null && bias == 2) key = lastEvicted;
            char kind;
            if (mode == 0) {
                kind = 'P';
                key = step; // Continuous unique-key eviction.
            } else if (mode == 1) {
                kind = 'P';
                key = random.nextInt(capacity); // Repeated updates after filling.
            } else {
                kind = random.nextInt(100) < (mode == 2 ? 85 : 45) ? 'G' : 'P';
                if (step % 31 == 0) kind = 'S';
            }
            CacheTestSupport.Op op = new CacheTestSupport.Op(kind, key, random.nextInt());
            if (kind == 'P' && model.keys.size() == capacity && !model.keys.contains(key)) {
                lastEvicted = model.keys.get(model.keys.size() - 1);
            }
            model.apply(op);
            history.add(op);
        }
        return history;
    }

    static void replay(int capacity, List<CacheTestSupport.Op> history) {
        // Check every result and full recency/value state without assertion reads altering it.
        LRUCache<Integer, Integer> cache = new LRUCache<>(capacity);
        CacheTestSupport.Model model = new CacheTestSupport.Model(capacity);
        for (int index = 0; index < history.size(); index++) {
            CacheTestSupport.Op op = history.get(index);
            try {
                CacheTestSupport.equal(model.apply(op), op.apply(cache));
                CacheTestSupport.equal(model.state(), CacheTestSupport.snapshot(cache, capacity));
            } catch (RuntimeException | AssertionError failure) {
                throw new AssertionError("Operation " + index + ": " + op, failure);
            }
        }
    }

    private static List<CacheTestSupport.Op> reduce(int capacity, List<CacheTestSupport.Op> original) {
        // Remove chunks that preserve failure, bounded to 200 replays rather than promising minimality.
        List<CacheTestSupport.Op> best = new ArrayList<>(original);
        int attempts = 0;
        for (int chunk = Math.max(1, best.size() / 2); chunk >= 1 && attempts < 200; chunk /= 2) {
            for (int start = 0; start < best.size() && attempts < 200;) {
                List<CacheTestSupport.Op> candidate = new ArrayList<>(best);
                candidate.subList(start, Math.min(start + chunk, candidate.size())).clear();
                attempts++;
                try {
                    replay(capacity, candidate);
                    start += chunk;
                } catch (AssertionError failure) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static Path save(long seed, int capacity, List<CacheTestSupport.Op> history, String suffix)
            throws Exception {
        // Persist exact operations and capacity in the ignored build directory for replay.
        Path directory = Paths.get("out", "failures");
        Files.createDirectories(directory);
        Path path = directory.resolve("stress-" + seed + "-" + suffix + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add(Integer.toString(capacity));
        for (CacheTestSupport.Op op : history) lines.add(op.toString());
        Files.write(path, lines, StandardCharsets.UTF_8);
        return path;
    }

    private static void replayFile(Path path) throws Exception {
        // Read the simple capacity-plus-operations format emitted on failure.
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int capacity = Integer.parseInt(lines.get(0));
        List<CacheTestSupport.Op> history = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(" ");
            history.add(new CacheTestSupport.Op(parts[0].charAt(0), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        replay(capacity, history);
        System.out.println("Replay passed: " + path);
    }
}

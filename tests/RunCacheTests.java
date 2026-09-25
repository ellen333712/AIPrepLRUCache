import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs suites in separate JVMs with heap limits and hard process deadlines. */
public class RunCacheTests {
    public static void main(String[] args) throws Exception {
        // Isolate deadlocks and memory regressions so failures cannot hang the test runner.
        String mode = args.length == 0 ? "all" : args[0];
        if (!Arrays.asList("all", "quick", "stress", "diagnostics").contains(mode)) {
            throw new IllegalArgumentException("Use all, quick, stress, or diagnostics");
        }
        if (mode.equals("all") || mode.equals("quick")) {
            run(60, "LRUCacheTest");
            run(60, "LRUCacheStressTest", "20", "1000");
            run(90, "LRUCacheConcurrencyTest", mode.equals("quick") ? "10" : "100");
        }
        if (mode.equals("all") || mode.equals("stress")) {
            run(120, "LRUCacheStressTest", "100", "10000");
            run(90, "LRUCacheMemoryTest");
        }
        if (mode.equals("diagnostics")) run(120, "LRUCacheBenchmark");
        System.out.println("PASS: " + mode);
    }

    private static void run(int timeoutSeconds, String suite, String... arguments) throws Exception {
        // Fail the parent on child failure, forcibly stopping any child that exceeds its deadline.
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(Arrays.asList("-Xmx128m", "-cp", System.getProperty("java.class.path"), suite));
        command.addAll(Arrays.asList(arguments));
        System.out.println("Running " + suite + " " + String.join(" ", arguments));
        Process process = new ProcessBuilder(command).inheritIO().start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new AssertionError(suite + " exceeded " + timeoutSeconds + " seconds");
            }
            CacheTestSupport.equal(0, process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}

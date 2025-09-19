import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class Worker implements Runnable {
    private final boolean cpuBound;

    public Worker (boolean cpuBound) {
        this.cpuBound = cpuBound;
    }

    @Override
    public void run() {
        if (cpuBound) {
            //CPU-intensive
            long count = 0;
            for (long i = 2; i < 50_000; i++) {
                if (isPrime(i)) count++;
            }
        } else {
            // I/O simulation
            try {
                long sleep = 50 + (long) (Math.random() * 50);
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isPrime(long n) {
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
}

public class ThreadPerformance {

    private void trackPerformance(ExecutorService executorService, boolean cpuBound, int tasks, String type) throws InterruptedException {
        System.out.println("==================== " + type + " =======================");
        long start = System.currentTimeMillis();
        for (int i = 0; i < tasks; i++) {
            executorService.submit(new Worker(cpuBound));
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);

        long end = System.currentTimeMillis();
        System.out.println(type + " threads took " + (end - start) + " ms");
        System.out.println(type + " throughput: " + (tasks * 1000.0 / (end - start)) + " tasks/sec");
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPerformance m = new ThreadPerformance();
        int tasks = 10000;

        System.out.println("--------------------------- CPU Bound Tasks -------------------------");
        //Virtual threads
        ExecutorService executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        m.trackPerformance(executorService, true, tasks, "Virtual");

        //Platform threads
        executorService = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory());
        m.trackPerformance(executorService, true, tasks, "Platform");

        //Fixed threads
        executorService = Executors.newFixedThreadPool(10);
        m.trackPerformance(executorService, true, tasks, "Fixed");

        System.out.println("\n--------------------------- I/O Bound Tasks -------------------------");
        executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        m.trackPerformance(executorService, false, tasks, "Virtual");

        //Platform threads
        executorService = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory());
        m.trackPerformance(executorService, false, tasks, "Platform");

        //Fixed threads
        executorService = Executors.newFixedThreadPool(10);
        m.trackPerformance(executorService, false, tasks, "Fixed");
    }
}
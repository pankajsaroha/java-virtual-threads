import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadsLimit {
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final AtomicInteger running;
    private final AtomicInteger maxRunning;

    public VirtualThreadsLimit(int maxConcurrentThreads) {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.semaphore = new Semaphore(maxConcurrentThreads);
        this.running = new AtomicInteger(0);
        this.maxRunning = new AtomicInteger(0);
    }

    public void createLimitedThreads() {
        for (int i = 0; i < 1000; i++) {
            try {
                semaphore.acquire();
                executorService.submit(() -> {
                    running.incrementAndGet();
                    maxRunning.updateAndGet(prev -> Math.max(prev, running.get()));
                    try {
                        long sleep = (long) (50 + Math.random() * 50);
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        running.decrementAndGet();
                        semaphore.release();
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        VirtualThreadsLimit vtl = new VirtualThreadsLimit(100);
        vtl.createLimitedThreads();

        //wait for all tasks to finish
        Thread.sleep(2000);
        System.out.println("Max concurrent tasks: " + vtl.maxRunning.get());
        vtl.executorService.shutdown();
    }
}

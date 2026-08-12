package com.traffic.sim.parallel;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Dedicated pools for simulation CPU work — never rides the common ForkJoinPool. */
public final class SimExecutor implements AutoCloseable {

    private static final AtomicInteger INSTANCE = new AtomicInteger();

    private final ForkJoinPool routingPool;
    private final ForkJoinPool tickPool;

    public SimExecutor(int routingParallelism, int tickParallelism) {
        int id = INSTANCE.incrementAndGet();
        int routing = Math.max(2, routingParallelism);
        int tick = Math.max(2, tickParallelism);
        this.routingPool = new ForkJoinPool(
                routing,
                pool -> {
                    ForkJoinWorkerThread t =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    t.setName("sim-routing-" + id + "-" + t.getPoolIndex());
                    t.setDaemon(true);
                    return t;
                },
                null,
                false
        );
        this.tickPool = new ForkJoinPool(
                tick,
                pool -> {
                    ForkJoinWorkerThread t =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    t.setName("sim-tick-" + id + "-" + t.getPoolIndex());
                    t.setDaemon(true);
                    return t;
                },
                null,
                false
        );
    }

    public static SimExecutor createDefault() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new SimExecutor(Math.max(2, cores - 1), Math.max(2, cores));
    }

    public ForkJoinPool routingPool() {
        return routingPool;
    }

    public ForkJoinPool tickPool() {
        return tickPool;
    }

    public int routingParallelism() {
        return routingPool.getParallelism();
    }

    public int tickParallelism() {
        return tickPool.getParallelism();
    }

    public void runRouting(Runnable task) {
        Objects.requireNonNull(task, "task");
        routingPool.submit(task).join();
    }

    public void runTick(Runnable task) {
        Objects.requireNonNull(task, "task");
        tickPool.submit(task).join();
    }

    @Override
    public void close() {
        shutdown(routingPool);
        shutdown(tickPool);
    }

    private static void shutdown(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

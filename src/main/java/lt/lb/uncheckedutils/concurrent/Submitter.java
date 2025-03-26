package lt.lb.uncheckedutils.concurrent;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import lt.lb.uncheckedutils.func.UncheckedSupplier;

/**
 *
 * @author laim0nas100
 */
public interface Submitter {

    <T> Future<T> submit(UncheckedSupplier<T> task);

    public static Submitter ofExecutorService(final ExecutorService service) {
        Objects.requireNonNull(service);
        return new Submitter() {
            ThreadLocal<Boolean> inside = ThreadLocal.withInitial(() -> false);

            @Override
            public <T> Future<T> submit(final UncheckedSupplier<T> task) {
                if (inside.get()) {
                    //just run
                    FutureTask<T> futureTask = new FutureTask<>(task);
                    futureTask.run();
                    return futureTask;
                } else {
                    return service.submit(() -> {
                        try {
                            inside.set(true);
                            return task.call();
                        } finally {
                            inside.set(false);
                        }

                    });
                }
            }
        };
    }

    public static final Submitter DEFAULT_POOL = Submitter.ofExecutorService(ForkJoinPool.commonPool());

    public static final Submitter IN_PLACE = new Submitter() {
        @Override
        public <T> Future<T> submit(UncheckedSupplier<T> task) {
            FutureTask<T> futureTask = new FutureTask<>(task);
            futureTask.run();
            return futureTask;
        }
    };
}

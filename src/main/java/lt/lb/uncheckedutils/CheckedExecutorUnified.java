package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.func.UncheckedRunnable;

/**
 *
 * {@inheritDoc}
 *
 * Every runnable is made to a {@link Supplier} and uses the same methods to
 * define context, instead of 2 separate. That is,
 * {@link CheckedExecutor#beforeCall(Supplier)} and {@link CheckedExecutor#afterCall(Supplier,SafeOpt)
 * } instead of {@link CheckedExecutor#beforeExecute(Runnable) } and {@link CheckedExecutor#afterExecute(Runnable, SafeOpt)
 *
 * @author laim0nas100
 */
public interface CheckedExecutorUnified extends CheckedExecutor {

    public static interface SupplierRun extends Supplier<Void>, Runnable {

        public Runnable getRunnable();

        @Override
        public default Void get() {
            getRunnable().run();
            return null;
        }

        @Override
        public default void run() {
            getRunnable().run();
        }

    }

    public default SupplierRun callable(Runnable run) {
        Objects.requireNonNull(run, "Runnable is null");
        return () -> run;
    }

    @Override
    public default void afterExecute(Runnable run, SafeOpt<Void> result) {
        if (run instanceof SupplierRun) {
            SupplierRun suplRun = (SupplierRun) run;
            afterCall(suplRun, result);
        }
    }

    @Override
    public default void beforeExecute(Runnable run) {
        if (run instanceof SupplierRun) {
            SupplierRun suplRun = (SupplierRun) run;
            beforeCall(suplRun);
        }
    }

    @Override
    public default SafeOpt<Void> execute(UncheckedRunnable run) {
        return call(callable(run));
    }

    @Override
    public default SafeOpt<Void> execute(Runnable run) {
        return call(callable(run));
    }

}

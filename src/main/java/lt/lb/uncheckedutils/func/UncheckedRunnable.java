package lt.lb.uncheckedutils.func;

import java.util.concurrent.Callable;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedRunnable extends Runnable {

    public static UncheckedRunnable from(Callable call) {
        return () -> call.call();
    }

    public static UncheckedRunnable from(Runnable run) {
        return () -> run.run();
    }

    @Override
    public default void run() throws NestedException {

        try {
            this.runUnchecked();
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * Run and get optional exception as {@link SafeOp}.
     *
     * @return
     */
    public default SafeOpt<Void> runSafe() {
        return SafeOpt.ofGet(() -> {
            run();
            return null;
        });
    }

    /**
     * {@link Runnable#run }
     * counterpart with unchecked operation.
     *
     * @throws java.lang.Throwable
     */
    public void runUnchecked() throws Throwable;

    public static <T> Callable<T> toCallable(UncheckedRunnable run, final T val) {
        return (UncheckedSupplier<T>) () -> val;
    }

    public static <T> Callable<T> toCallable(UncheckedRunnable run) {
        return toCallable(run, null);
    }
}

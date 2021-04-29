package lt.lb.uncheckedutils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.func.UncheckedRunnable;
import lt.lb.uncheckedutils.func.UncheckedSupplier;

/**
 *
 * @author laim0nas100
 */
public class Checked {

    /**
     * Run with wrapping exception
     *
     * @param r
     * @throws NestedException
     */
    public static void uncheckedRun(UncheckedRunnable r) throws NestedException {
        try {
            r.uncheckedRun();
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * Call with wrapping exception
     *
     * @param <T>
     * @param call
     * @return
     * @throws NestedException
     */
    public static <T> T uncheckedCall(UncheckedSupplier<T> call) throws NestedException {
        try {
            return call.uncheckedGet();
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * Run with wrapping exception inside handler
     *
     * @param cons
     * @param run
     */
    public static void uncheckedRunWithHandler(Consumer<Throwable> cons, UncheckedRunnable run) {
        try {
            run.uncheckedRun();
        } catch (Throwable e) {
            cons.accept(NestedException.unwrap(e));
        }
    }

    /**
     * Call with wrapping exception inside handler
     *
     * @param <T>
     * @param cons
     * @param call
     * @return result or {@code null} if exception was thrown
     */
    public static <T> T uncheckedCallWithHandler(Consumer<Throwable> cons, UncheckedSupplier<T> call) {
        try {
            return call.uncheckedGet();
        } catch (Throwable e) {
            cons.accept(NestedException.unwrap(e));
        }
        return null;
    }

    /**
     * Call with ignoring all exceptions. Returns null, if execution fails.
     *
     * @param <T>
     * @param call
     * @return result or {@code null} if exception was thrown
     */
    public static <T> T checkedCallNoExceptions(UncheckedSupplier<T> call) {
        try {
            return call.uncheckedGet();
        } catch (Throwable e) {
        }
        return null;
    }

    /**
     * Run and catch any possible error
     *
     * @param r
     * @return
     */
    public static Optional<Throwable> checkedRun(UncheckedRunnable r) {
        try {
            r.uncheckedRun();
            return Optional.empty();
        } catch (Throwable t) {
            return Optional.of(t).map(m -> NestedException.unwrap(m));
        }
    }

    /**
     * Run and catch any possible error
     *
     * @param r
     * @return
     */
    public static Optional<Throwable> checkedRun(Runnable r) {
        try {
            r.run();
            return Optional.empty();
        } catch (Throwable t) {
            return Optional.of(t).map(m -> NestedException.unwrap(m));
        }
    }

    /**
     * Call and catch any possible error alongside with optional error. Null
     * values are treated as not present.
     *
     * @param <T>
     * @param call
     * @return
     */
    public static <T> SafeOpt<T> checkedCall(UncheckedSupplier<T> call) {
        return SafeOpt.ofGet(call);
    }

    /**
     * Call and catch any possible error alongside with optional error. Null
     * values are treated as not present.
     *
     * @param <T>
     * @param call
     * @return
     */
    public static <T> SafeOpt<T> checkedCall(Supplier<T> call) {
        return SafeOpt.ofGet(call);
    }
}

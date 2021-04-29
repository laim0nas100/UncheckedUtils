package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.func.UncheckedRunnable;
import lt.lb.uncheckedutils.func.UncheckedSupplier;

/**
 *
 * A way to safely (catching all exceptions) execute arbitrary code, within
 * optionally defined context, that can be set before and after execution. For
 * example - transactions, error logging, monitoring.
 *
 * The result of type {@link SafeOpt} that is empty (with first optionally
 * caught exception) or with a returned result.
 *
 * @author laim0nas100
 */
public interface CheckedExecutor {

    public default SafeOpt<Void> execute(Runnable run) {
        Objects.requireNonNull(run, "Null runnable");
        beforeExecute(run);
        Optional<Throwable> checkedRun = Checked.checkedRun(run);
        SafeOpt<Void> result = checkedRun.isPresent() ? SafeOpt.error(checkedRun.get()) : SafeOpt.empty();
        afterExecute(run, result);
        return result;
    }

    public default SafeOpt<Void> execute(UncheckedRunnable run) {
        return execute((Runnable) run);
    }

    public default <T> SafeOpt<T> call(UncheckedSupplier<T> supl) {
        return call((Supplier<T>) supl);
    }

    public default <T> SafeOpt<T> call(Supplier<T> supl) {
        Objects.requireNonNull(supl, "Null supplier");
        beforeCall(supl);
        SafeOpt<T> result = SafeOpt.ofGet(supl);
        afterCall(supl, result);
        return result;
    }

    public default void beforeExecute(Runnable run) {

    }

    public default void afterExecute(Runnable run, SafeOpt<Void> result) {

    }

    public default <T> void beforeCall(Supplier<T> supl) {

    }

    public default <T> void afterCall(Supplier<T> supl, SafeOpt<T> result) {

    }

}

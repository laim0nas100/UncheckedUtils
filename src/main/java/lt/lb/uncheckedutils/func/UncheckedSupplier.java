package lt.lb.uncheckedutils.func;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedSupplier<T> extends Supplier<T>, Callable<T> {

    @Override
    public default T call() throws Exception {
        try {
            return this.getUnchecked();
        } catch (Throwable e) {
            Throwable real = NestedException.unwrap(e);
            if (real instanceof Exception) {
                throw (Exception) e;
            }
            throw NestedException.of(e);
        }
    }

    @Override
    public default T get() throws NestedException {

        try {
            return this.getUnchecked();
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * {@link Supplier#get }
     * counterpart with unchecked operation.
     *
     * @return
     * @throws java.lang.Throwable
     */
    public T getUnchecked() throws Throwable;

    /**
     * Get result as {@link SafeOp}.
     * @param t
     * @return 
     */
    public default SafeOpt<T> getSafe(T t) {
        return SafeOpt.ofGet(this);
    }
}

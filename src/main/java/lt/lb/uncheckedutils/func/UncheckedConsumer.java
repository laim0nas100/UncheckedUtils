package lt.lb.uncheckedutils.func;

import java.util.function.Consumer;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedConsumer<P> extends Consumer<P> {

    /**
     * {@link Consumer#accept(java.lang.Object) }
     * counterpart with unchecked operation.
     *
     * @param t
     * @throws java.lang.Throwable
     */
    public void acceptUnchecked(P t) throws Throwable;

    @Override
    public default void accept(P t) throws NestedException {
        try {
            acceptUnchecked(t);
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * Apply and get optional exception as {@link SafeOp}.
     *
     * @param t
     * @return
     */
    public default SafeOpt<Void> acceptSafe(P t) {
        return SafeOpt.ofGet(() -> {
            acceptUnchecked(t);
            return null;
        });
    }

}

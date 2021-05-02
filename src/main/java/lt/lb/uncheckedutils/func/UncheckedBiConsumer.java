package lt.lb.uncheckedutils.func;

import java.util.function.BiConsumer;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedBiConsumer<P, R> extends BiConsumer<P, R> {

    /**
     * {@link BiConsumer#accept(java.lang.Object, java.lang.Object) }
     * counterpart with unchecked operation.
     *
     * @param t
     * @param r
     * @throws Throwable
     */
    public void acceptUnchecked(P t, R r) throws Throwable;

    @Override
    public default void accept(P t, R r) throws NestedException {

        try {
            acceptUnchecked(t, r);
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * Accept and get optional exception as {@link SafeOp}.
     *
     * @param t
     * @param r
     * @return
     */
    public default SafeOpt<Void> acceptSafe(P t, R r) {
        return SafeOpt.ofGet(() -> {
            acceptUnchecked(t, r);
            return null;
        });
    }

}

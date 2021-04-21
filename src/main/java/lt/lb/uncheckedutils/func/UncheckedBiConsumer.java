package lt.lb.uncheckedutils.func;

import java.util.function.BiConsumer;
import lt.lb.uncheckedutils.NestedException;

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
    public void acceotUnchecked(P t, R r) throws Throwable;

    @Override
    public default void accept(P t, R r) throws NestedException {

        try {
            acceotUnchecked(t, r);
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

}

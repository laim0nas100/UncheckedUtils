package lt.lb.uncheckedutils.func;

import java.util.function.BiFunction;
import lt.lb.uncheckedutils.NestedException;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedBiFunction<O, P, R> extends BiFunction<O, P, R> {

    @Override
    public default R apply(O t, P u) throws NestedException {
        try {
            return applyUnchecked(t, u);
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }

    /**
     * {@link BiFunction#apply(java.lang.Object, java.lang.Object) }
     * counterpart with unchecked operation.
     *
     * @param t
     * @param u
     * @return
     * @throws java.lang.Throwable
     */
    public R applyUnchecked(O t, P u) throws Throwable;

}

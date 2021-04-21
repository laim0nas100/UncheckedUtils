package lt.lb.uncheckedutils.func;

import java.util.function.Function;
import lt.lb.uncheckedutils.NestedException;

/**
 *
 * @author laim0nas100
 */
@FunctionalInterface
public interface UncheckedFunction<P, R> extends Function<P, R> {

    /**
     * {@link Function#apply(java.lang.Object) }
     * counterpart with unchecked operation.
     *
     * @param t
     * @return
     * @throws java.lang.Throwable
     */
    public R applyUnchecked(P t) throws Throwable;

    @Override
    public default R apply(P t) throws NestedException {
        try {
            return applyUnchecked(t);
        } catch (Throwable e) {
            throw NestedException.of(e);
        }
    }
}

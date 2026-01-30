package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.func.UncheckedFunction;

/**
 * Simple lazy memoized value with dependency-aware invalidation.
 * <p>
 * Recomputes only when explicitly invalidated or when a dependency is
 * invalidated. New derived values created via {@link #map} become automatic
 * dependents.
 *
 * @author laim0nas100
 */
public class LazySafe<T> extends LazySafeBase<T, Supplier<T>, LazySafe<T>> {

    public LazySafe(Supplier<T> supplier) {
        super(supplier);
    }

    @Override
    public <A> LazySafe<A> map(Function<? super T, ? extends A> func) {
        Objects.requireNonNull(func);
        LazySafe<A> lazySafe = new LazySafe<>(() -> func.apply(get()));
        addDependent(lazySafe);
        return lazySafe;
    }

    @Override
    public <A> LazySafe<A> map(UncheckedFunction<? super T, ? extends A> func) {
        return map((Function) func);
    }

}

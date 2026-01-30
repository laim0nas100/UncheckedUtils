package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.LazySafeBase.ValHolder;
import lt.lb.uncheckedutils.func.UncheckedFunction;

/**
 * Lazy memoized value whose storage is a mutable {@link ValHolder}.
 * <p>
 * Supports manual override via {@link #accept(Object)}, which invalidates the current
 * value (and dependents) and replaces any previous supplier with a concrete value.
 */
public class LazySafeValue<T> extends LazySafeBase<T, ValHolder<T>, LazySafeValue<T>> implements Consumer<T> {

    /**
     * Creates a lazy value initialized with an immediate (eager) value.
     *
     * @param val initial concrete value
     */
    public LazySafeValue(T val) {
        super(ValHolder.ofVal(val));
    }

    /**
     * Creates a lazy value backed by a supplier (computed on first access).
     *
     * @param sup computation to perform lazily
     */
    public LazySafeValue(Supplier<T> sup) {
        super(ValHolder.ofSup(sup));

    }

    /**
     * Creates a derived lazy value by applying the given transformation. The
     * new instance becomes a dependent → invalidation propagates.
     *
     * @param func transformation to apply
     * @param <A> type of the resulting value
     * @return new derived lazy value
     */
    @Override
    public <A> LazySafeValue<A> map(Function<? super T, ? extends A> func) {
        Objects.requireNonNull(func);
        LazySafeValue<A> lazySafe = new LazySafeValue<>(() -> func.apply(get()));
        addDependent(lazySafe);
        return lazySafe;
    }

    /**
     * Convenience overload — treats unchecked function as regular Function.
     *
     * @param func transformation (may throw checked exceptions)
     * @param <A> type of the resulting value
     * @return new derived lazy value
     */
    @Override
    public <A> LazySafeValue<A> map(UncheckedFunction<? super T, ? extends A> func) {
        return map((Function) func);
    }

    /**
     * Once you set this value manually, the supplier connection is severed (if
     * the value was created by calling {@link LazySafeValue(java.util.function.Supplier)  or
     * {@link LazySafeValu##map(java.util.function.Function) }. Parent still invalidates this, but the value is
     * independent from in, because it's manually set..
     *
     * @param t
     */
    @Override
    public void accept(T t) {
        invalidate();
        this.supplier.realSupplier.accept(t);
    }

}

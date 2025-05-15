package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author laim0nas100
 */
public class SafeOptLazySnap<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    public static interface CachedSupplier<T> extends Supplier<SafeOpt<T>> {

        public boolean isComputable();

        public boolean isDone();

        public default boolean isComputed() {
            return isDone() && isComputable();
        }

        public SafeOpt<T> compute();
    }

    public static class CachedSupplierValue<T> implements CachedSupplier<T> {

        public CachedSupplierValue(SafeOpt<T> value) {
            this.value = value;
        }

        private final SafeOpt<T> value;

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isComputable() {
            return false;
        }

        @Override
        public SafeOpt<T> compute() {
            return value;
        }

        @Override
        public SafeOpt<T> get() {
            return value;
        }

    }

    public static abstract class CachedSupplierCompute<T> implements CachedSupplier<T> {

        private final CompletableFuture<SafeOpt<T>> cached = new CompletableFuture<>();
        private final AtomicBoolean computeCalled = new AtomicBoolean(false);

        @Override
        public boolean isDone() {
            return cached.isDone();
        }

        @Override
        public boolean isComputable() {
            return true;
        }

        @Override
        public final SafeOpt<T> get() {
            try {
                if (cached.isDone()) {
                    return cached.get();
                }
                if (!computeCalled.compareAndSet(false, true)) {
                    return cached.get();
                }//compute
                try {
                    SafeOpt<T> compute = compute();
                    cached.complete(compute);
                    return compute;
                } catch (Throwable tr) {
                    SafeOpt<T> error = SafeOpt.error(tr);
                    cached.complete(error);
                    return error;
                }

            } catch (InterruptedException | ExecutionException ex) {
                return SafeOpt.error(ex);// only interruptions can happen
            }
        }

    }

    /**
     * STATE. We only need to collapse the function once, so we need to save
     * some state.
     */
    protected CachedSupplier<T> supplier;

    protected SafeOptLazySnap(CachedSupplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    protected SafeOptLazySnap(SafeOpt<T> initial) {
        this.supplier = new CachedSupplierValue<>(initial);
    }

    @Override
    public SafeOpt<T> collapse() {
        return supplier.get();
    }

    @Override
    public <O> SafeOpt<O> functorCheap(Function<SafeOpt<T>, SafeOpt<O>> func) {
        return functor(true, func);
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        return functor(false, func);
    }

    protected <O> SafeOpt<O> functor(boolean cheap, Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if ((cheap && supplier.isDone()) || supplier.isComputed()) {
            return func.apply(collapse());// no more lazy application after collapse
        }
        SafeOptLazySnap<T> prev = this;
        return new SafeOptLazySnap<>(new CachedSupplierCompute<O>() {
            @Override
            public SafeOpt<O> compute() {
                return func.apply(prev.collapse());
            }
        });
    }

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return new SafeOptLazySnap<>(SafeOpt.empty());
        }
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException should not be present");
        }
        return new SafeOptLazySnap<>(SafeOpt.ofNullable(rawValue));
    }

    @Override
    public <A> SafeOpt<A> produceEmpty() {
        if (rawValue() == null && rawException() == null) {// is allready empty
            return (SafeOpt<A>) this;
        }
        return new SafeOptLazySnap<>(SafeOpt.empty());
    }

    @Override
    public <A> SafeOpt<A> produceError(Throwable rawException) {
        if (rawException == null) {
            return produceEmpty();
        }
        if (rawException() == rawException && rawValue() == null) {
            return (SafeOpt<A>) this;
        }
        return new SafeOptLazySnap<>(SafeOpt.error(rawException));
    }

}

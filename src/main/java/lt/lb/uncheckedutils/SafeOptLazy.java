package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 *
 * @author laim0nas100
 * @deprecated implicitly waits 60 seconds and fails if not completed by another thread, use {@link SafeOptLazySnap}
 */
@Deprecated
public class SafeOptLazy<S, T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    protected final SafeOpt<S> initial;
    protected final Function<SafeOpt<S>, SafeOpt<T>> function;

    private static final SafeOpt FAILED_COLLAPSE = SafeOpt.error(new PassableException("Failed to collapse SafeOptLazy"));
    /**
     * STATE. We only need to collapse the function once, so we need to save
     * some state.
     */
    protected SafeOpt<T> collapsed;
    protected final AtomicBoolean inCollapse = new AtomicBoolean(false);

    protected SafeOptLazy(SafeOpt<S> initial, Function<SafeOpt<S>, SafeOpt<T>> function) {
        this.initial = Objects.requireNonNull(initial);
        this.function = Objects.requireNonNull(function);
    }

    protected SafeOptLazy() {
        this.initial = SafeOpt.empty();
        this.function = f -> (SafeOpt<T>) f;
    }

    protected SafeOptLazy(S initialVal, Function<S, T> func) {
        this.initial = SafeOpt.ofNullable(initialVal);
        this.function = f -> f.map(func);
    }

    protected SafeOptLazy(Throwable initialVal) {
        this.initial = SafeOpt.error(initialVal);
        this.function = f -> (SafeOpt<T>) f;
    }

    @Override
    public SafeOpt<T> collapse() {
        if (collapsed != null) {
            return collapsed;
        }
        if (inCollapse.compareAndSet(false, true)) {
            try {
                collapsed = function.apply(initial);
            } finally {
                if (collapsed == null) {
                    collapsed = FAILED_COLLAPSE;
                }
            }
        }
        int waits = 600_000;//60 seconds
        while (collapsed == null && --waits >= 0) {
            LockSupport.parkNanos(100_000_000);//100 ms
        }
        return Objects.requireNonNull(collapsed, "collapse accessed from another thread before finishing or collapsed function returned null");
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (collapsed != null) {
            return func.apply(collapsed);// no more lazy application after collapse
        }
        return new SafeOptLazy<>(initial, function.andThen(func));
    }

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return new SafeOptLazy<>();
        }
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException should not be present");
        }
        if (rawValue != null) {
            return new SafeOptLazy<>(rawValue, Function.identity());
        } else {
            return new SafeOptLazy<>(rawException);
        }
    }

    @Override
    public <A> SafeOpt<A> produceEmpty() {
        if (rawValue() == null && rawException() == null) {// is allready empty
            return (SafeOpt<A>) this;
        }
        return new SafeOptLazy<>();
    }

    @Override
    public <A> SafeOpt<A> produceError(Throwable rawException) {
        if (rawException == null) {
            return produceEmpty();
        }
        if (rawException() == rawException && rawValue() == null) {
            return (SafeOpt<A>) this;
        }
        return new SafeOptLazy<>(rawException);
    }

}

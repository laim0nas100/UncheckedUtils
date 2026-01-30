package lt.lb.uncheckedutils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lt.lb.uncheckedutils.func.UncheckedFunction;

/**
 * Lazy-initialized value with explicit invalidation support, dependency
 * propagation and optional cleanup hooks.
 * <p>
 * Provides memoization (value computed at most once per invalidation cycle)
 * with:
 * <ul>
 * <li>atomic invalidation flag</li>
 * <li>automatic propagation of invalidation to dependent computations</li>
 * <li>optional cleanup actions before recomputation</li>
 * </ul>
 * <p>
 * Dependencies can be established in two main ways:
 * <ul>
 * <li>implicitly — via {@link #map} (most common for derived computations)</li>
 * <li>explicitly — via {@link #dependsOn} or {@link #addDependent} (useful when
 * combining multiple independent sources)</li>
 * </ul>
 *
 * <p>
 * The actual memoization happens inside {@link SafeOptLazySnap}; this class
 * only manages invalidation, dependency propagation and cleanup orchestration.
 * </p>
 *
 * @param <T> type of the lazily computed value
 * @param <SUP> type of the underlying supplier
 * @param <M> concrete self-type (for fluent chaining)
 * @author laim0nas100
 */
public abstract class LazySafeBase<T, SUP extends Supplier<T>, M extends LazySafeBase<T, SUP, M>> implements Supplier<T> {

    /**
     * Current computed value wrapped in SafeOpt (never null)
     */
    protected SafeOpt<T> val;

    /**
     * Atomic flag indicating whether the value needs to be (re)computed
     */
    protected final AtomicBoolean invalid = new AtomicBoolean(true);

    /**
     * Decorated supplier that allows exactly one successful get() per
     * invalidation cycle
     */
    protected final DecoratedSupplier<T, SUP> supplier;

    /**
     * Weak references to objects that depend on this value (will be invalidated
     * together)
     */
    protected final Collection<WeakReference<LazySafeBase>> dependants = new ArrayList<>();

    /**
     * Cleanup actions executed when the value is invalidated (before
     * recomputation)
     */
    protected final Collection<Consumer<SafeOpt<T>>> cleanup = new ArrayList<>();

    /**
     * Wrapper around the user-provided supplier that enforces
     * single-use-per-invalidation and proper exception wrapping.
     */
    protected class DecoratedSupplier<A, S extends Supplier<A>> implements Supplier<A> {

        /**
         * The original supplier provided by the user
         */
        public final S realSupplier;

        public DecoratedSupplier(S real) {
            this.realSupplier = Objects.requireNonNull(real);
        }

        /**
         * Gets the value from the real supplier, but only once per invalidation
         * cycle.
         *
         * @return computed value
         * @throws NestedException if the supplier throws any exception
         * @throws IllegalStateException if called more than once without
         * invalidation
         */
        @Override
        public A get() {
            try {
                if (invalid.compareAndSet(true, false)) {
                    return realSupplier.get();
                }
            } catch (Throwable th) {
                throw NestedException.of(th);
            }
            throw new IllegalStateException("Supplier called while not invalid");
        }
    }

    /**
     * Returns the current value (throws if not present / failed). Shortcut for
     * {@code getSafe().throwAnyGet()}.
     *
     * @return the computed value
     * @throws NoSuchElementException if value is empty or computation failed
     * @throws NestedException if exception during computation has occured
     */
    @Override
    public T get() {
        return val.throwAnyGet();
    }

    /**
     * Returns the current value wrapped in {@link SafeOpt}. This is the safest
     * way to access the value.
     *
     * @return current computation result (success, empty or failure)
     */
    public SafeOpt<T> getSafe() {
        return val;
    }

    /**
     * Resets the internal value holder to the initial "starting" state so the
     * next {@code supplier.get()} call will be executed.
     */
    protected final void reinitVal() {
        val = SafeOptLazySnap.STARTING.map(m -> supplier.get());
    }

    /**
     * Executes all registered cleanup actions with the previous value.
     * Exceptions in cleanup handlers are silently swallowed.
     *
     * @param previousValue the value that is being replaced / invalidated
     */
    protected void clean(SafeOpt<T> previousValue) {
        try {
            cleanup.forEach(c -> c.accept(previousValue));
        } catch (Throwable th) {

        }
    }

    /**
     * Marks this value as invalid and triggers recomputation on next access.
     * Also cleans up the old value and invalidates all dependent objects.
     *
     * @return {@code true} if this instance was actually invalidated (was valid
     * before), {@code false} if it was already invalid
     */
    public boolean invalidate() {
        if (invalid.compareAndSet(false, true)) {
            clean(val);
            reinitVal();
            invalidateDependents();
            return true;
        }

        return false;
    }

    /**
     * Invalidates all dependent objects (weak references are cleaned up
     * automatically).
     */
    protected void invalidateDependents() {
        Iterator<WeakReference<LazySafeBase>> iterator = dependants.iterator();
        while (iterator.hasNext()) {
            WeakReference<LazySafeBase> reference = iterator.next();
            LazySafeBase dep = reference.get();
            if (dep == null) {
                iterator.remove();
                continue;
            }
            dep.invalidate();
        }
    }

    /**
     * Creates a new lazy value with the given supplier.
     *
     * @param supplier computation that produces value of type T (called lazily)
     */
    public LazySafeBase(SUP supplier) {
        this.supplier = new DecoratedSupplier<>(supplier);
        reinitVal();
    }

    /**
     * CRTP helper – returns {@code this} with the correct self-type.
     */
    protected M me() {
        return (M) this;
    }

    /**
     * Registers another {@code LazySafeBase} as dependent on this instance.
     * When this value is invalidated, the dependent will also be invalidated.
     *
     * @param dep dependent object to be invalidated together with this one
     * @return this instance (for method chaining)
     */
    public M addDependent(LazySafeBase dep) {
        dependants.add(new WeakReference<>(Objects.requireNonNull(dep)));
        return me();
    }

    /**
     * Convenience method: registers <b>this</b> instance as dependent on the
     * given parent.
     *
     * @param parent the object this instance depends on
     * @return this instance (for method chaining)
     */
    public M dependsOn(LazySafeBase parent) {
        Objects.requireNonNull(parent);
        parent.addDependent(this);
        return me();
    }

    /**
     * Adds a cleanup action that will be called with the current value
     * <b>only when it exists</b> (i.e. when {@code SafeOpt.isPresent()}).
     *
     * @param clean consumer called with the old value (if present)
     * @return this instance (for method chaining)
     */
    public M withCleanupIfPresent(Consumer<T> clean) {
        Objects.requireNonNull(clean);
        return withCleanup(safe -> safe.ifPresent(clean));
    }

    /**
     * Adds a cleanup action that will be called every time this value is
     * invalidated.
     *
     * @param clean consumer that receives the previous {@link SafeOpt} value
     * @return this instance (for method chaining)
     */
    public M withCleanup(Consumer<SafeOpt<T>> clean) {
        cleanup.add(Objects.requireNonNull(clean));
        return me();
    }

    /**
     * Returns a new lazy value derived from this one.
     * <p>
     * The returned instance is automatically registered as a dependent —
     * invalidation of this value will trigger invalidation of the derived
     * value.
     * </p>
     * <p>
     * The computation of the new value calls {@code this.get()} when needed.
     * </p>
     *
     * @param func transformation function (may throw)
     * @param <A> result type of the transformation
     * @return new lazy computation
     */
    public abstract <A> LazySafeBase map(UncheckedFunction<? super T, ? extends A> func);

    /**
     * Returns a new lazy value derived from this one.
     * <p>
     * The returned instance is automatically registered as a dependent —
     * invalidation of this value will trigger invalidation of the derived
     * value.
     * </p>
     * <p>
     * The computation of the new value calls {@code this.get()} when needed.
     * </p>
     *
     * @param func transformation function
     * @param <A> result type of the transformation
     * @return new lazy computation
     */
    public abstract <A> LazySafeBase map(Function<? super T, ? extends A> func);

    /**
     * Mutable holder for a value that can be either concrete (eager) or
     * provided lazily via a Supplier. Implements Supplier<T> (get value) and
     * Consumer<T> (set value). Setting a value discards any previous supplier.
     * <p>
     * Not thread-safe.
     *
     * @param <T> type of held/supplied value
     */
    public static class ValHolder<T> implements Supplier<T>, Consumer<T> {

        private T val;
        private Supplier<T> alternative;

        private ValHolder() {
        }

        /**
         * Creates holder with immediate value (no supplier).
         */
        public static <T> ValHolder<T> ofVal(T val) {
            ValHolder<T> vh = new ValHolder<>();
            vh.val = val;
            vh.alternative = null;
            return vh;
        }

        /**
         * Creates holder backed by a lazy supplier. Supplier is discarded on
         * set.
         */
        public static <T> ValHolder<T> ofSup(Supplier<T> sup) {
            ValHolder<T> vh = new ValHolder<>();
            vh.val = null;
            vh.alternative = Objects.requireNonNull(sup);
            return vh;
        }

        /**
         * Returns current value (from field or supplier).
         */
        @Override
        public T get() {
            return alternative == null ? val : alternative.get();
        }

        /**
         * Sets concrete value and clears supplier.
         */
        @Override
        public void accept(T t) {
            this.val = t;
            if (alternative != null) {
                this.alternative = null;
            }
        }

        /**
         * Alias for {@link #accept(Object)}.
         */
        public void set(T t) {
            accept(t);
        }
    }
}

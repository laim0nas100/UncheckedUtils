package lt.lb.uncheckedutils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lt.lb.uncheckedutils.func.UncheckedBiFunction;
import lt.lb.uncheckedutils.func.UncheckedConsumer;
import lt.lb.uncheckedutils.func.UncheckedFunction;
import lt.lb.uncheckedutils.func.UncheckedRunnable;

/**
 * Base lazy-ish behaviour methods for {@link SafeOpt} interface, for those
 * methods that can return the result eventually.
 *
 * @author laim0nas100
 * @param <T>
 */
public interface SafeOptCollapse<T> extends SafeOpt<T> {

    /**
     * Wait for all operations to finish and get current state
     *
     * @return
     */
    SafeOpt<T> collapse();

    /**
     * Queue up modifying operation (functor) and return resulting
     * {@link SafeOpt}
     *
     * @param <O>
     * @param func
     * @return
     */
    <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func);

    /**
     * Queue up modifying operation (functor) and return resulting
     * {@link SafeOpt}. If there is no queue, just modify it in place to safe
     * computing time or threading.
     *
     * @param <O>
     * @param func
     * @return
     */
    default <O> SafeOpt<O> functorCheap(Function<SafeOpt<T>, SafeOpt<O>> func) {
        return functor(func);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T rawValue() {
        return collapse().rawValue();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default Throwable rawException() {
        return collapse().rawException();
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<Throwable> getError() {
        return functorCheap(f -> f.getError());
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean isPresentWhen(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return collapse().isPresentWhen(predicate);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orNull() {
        return collapse().orNull();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T throwNestedOr(T val) {
        return collapse().throwNestedOr(val);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T throwNestedOrNull() {
        return collapse().throwNestedOrNull();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default <Ex extends Throwable> SafeOpt<T> throwIfErrorUnwrapping(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfErrorUnwrapping(type);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default <Ex extends Throwable> SafeOpt<T> throwIfError(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfError(type);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> throwIfErrorRuntime() {
        return collapse().throwIfErrorRuntime();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> throwIfErrorAsNested() {
        return collapse().throwIfErrorAsNested();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default <Ex extends Throwable> T throwIfErrorOrGet(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfErrorOrGet(type);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Objects.requireNonNull(exceptionSupplier);
        return collapse().orElseThrow(exceptionSupplier);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orElseGet(Supplier<? extends T> other) {
        return collapse().orElseGet(other);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orElse(T other) {
        return collapse().orElse(other);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default T get() {
        return collapse().get();
    }

    @Override
    public default <U, P> SafeOpt<U> mapCombine(SafeOpt<? extends P> with, BiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with);
        Objects.requireNonNull(mapper);
        return functor(f -> f.mapCombine(with, mapper));
    }

    @Override
    public default <U, P> SafeOpt<U> mapCombine(SafeOpt<? extends P> with, UncheckedBiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with);
        Objects.requireNonNull(mapper);
        return functor(f -> f.mapCombine(with, mapper));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<Void> keepError() {
        return functorCheap(f -> f.keepError());
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekError(Consumer<Throwable> errorCons) {
        Objects.requireNonNull(errorCons);
        return functorCheap(f -> f.peekError(errorCons));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> orGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return functorCheap(f -> f.orGet(supplier));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> orGetOpt(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        return functorCheap(f -> f.orGetOpt(supplier));
    }

    @Override
    public default <U> SafeOpt<U> flatMapOpt(UncheckedFunction<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMapOpt(mapper));
    }

    @Override
    public default <U> SafeOpt<U> flatMapOpt(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMapOpt(mapper));
    }

    @Override
    public default <U> SafeOpt<U> flatMap(Function<? super T, ? extends SafeOpt<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMap(mapper));
    }

    @Override
    public default <U> SafeOpt<U> flatMap(UncheckedFunction<? super T, ? extends SafeOpt<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMap(mapper));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default <U> SafeOpt<U> select(Class<? extends U> clazz) {
        Objects.requireNonNull(clazz);
        return functorCheap(f -> f.select(clazz));
    }

    @Override
    public default <U> SafeOpt<U> map(UncheckedFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.map(mapper));
    }

    @Override
    public default <U> SafeOpt<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.map(mapper));
    }

    @Override
    public default SafeOpt<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return functor(f -> f.filter(predicate));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peek(UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functorCheap(f -> f.peek(action));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functorCheap(f -> f.peek(action));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functorCheap(f -> f.peekOrElse(action, emptyAction));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrElse(Consumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functorCheap(f -> f.peekOrElse(action, emptyAction));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functorCheap(f -> f.peekOrElse(action, emptyAction));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functorCheap(f -> f.peekOrElse(action, emptyAction));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrDefault(T def, UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functorCheap(f -> f.peekOrDefault(def, action));
    }

    /**
     * Cheap.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> peekOrDefault(T def, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functorCheap(f -> f.peekOrDefault(def, action));
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default void ifPresent(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer);
        collapse().ifPresent(consumer);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        collapse().ifPresentOrElse(action, emptyAction);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default void ifPresentOrDefault(T def, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        collapse().ifPresentOrDefault(def, action);
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean isEmpty() {
        return collapse().isEmpty();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean hasValueOrError() {
        return collapse().hasValueOrError();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean hasError() {
        return collapse().hasError();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean isPresent() {
        return collapse().isPresent();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default Optional<T> ignoringExceptionOptional() {
        return collapse().ignoringExceptionOptional();
    }

    /**
     *
     * Collapsing.
     *
     * {@inheritDoc}
     */
    @Override
    public default Optional<T> asOptional() throws NestedException {
        return collapse().asOptional();
    }

    /**
     * Create an iterator from {@link SafeOpt}, without knowing if value is
     * present or not until the iterator is used.
     *
     * @param <S>
     * @param <T>
     * @param safeOpt
     * @param ignoreException
     * @return
     */
    public static <S, T> Iterator<T> asIterator(final SafeOpt<T> safeOpt, final boolean ignoreException) {
        Objects.requireNonNull(safeOpt);
        return new Iterator<T>() {

            final AtomicBoolean called = new AtomicBoolean(false);

            @Override
            public boolean hasNext() {
                if (called.get()) {
                    return false;
                }
                if (ignoreException) {
                    return safeOpt.isPresent();
                }
                return safeOpt.hasValueOrError();
            }

            @Override
            public T next() {
                if (called.compareAndSet(false, true)) {
                    if (!ignoreException) {
                        safeOpt.throwIfErrorAsNested();
                    }
                    return safeOpt.orElseThrow(() -> new NoSuchElementException("No more elements"));
                } else {
                    throw new NoSuchElementException("No more elements");
                }

            }
        };
    }

    @Override
    public default Stream<T> ignoringExceptionStream() {
        return StreamSupport.stream(Spliterators.spliterator(asIterator(this, true), 1, Spliterator.SIZED), false);
    }

    @Override
    public default Stream<T> stream() throws NestedException {
        return StreamSupport.stream(Spliterators.spliterator(asIterator(this, false), 1, Spliterator.SIZED), false);

    }
}

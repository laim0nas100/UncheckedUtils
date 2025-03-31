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
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T rawValue() {
        return collapse().rawValue();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default Throwable rawException() {
        return collapse().rawException();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<Throwable> getError() {
        return produceNew(rawException(), null);
    }

    /**
     *
     * Collapsed.
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
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orNull() {
        return collapse().orNull();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T throwNestedOr(T val) {
        return collapse().throwNestedOr(val);
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T throwNestedOrNull() {
        return collapse().throwNestedOrNull();
    }

    /**
     *
     * Collapsed.
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
     * Collapsed.
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
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> throwIfErrorRuntime() {
        return collapse().throwIfErrorRuntime();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default SafeOpt<T> throwIfErrorAsNested() {
        return collapse().throwIfErrorAsNested();
    }

    /**
     *
     * Collapsed.
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
     * Collapsed.
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
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orElseGet(Supplier<? extends T> other) {
        return collapse().orElseGet(other);
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default T orElse(T other) {
        return collapse().orElse(other);
    }

    /**
     *
     * Collapsed.
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

    @Override
    public default SafeOpt<Void> keepError() {
        return functor(f -> f.keepError());
    }

    @Override
    public default SafeOpt<T> peekError(Consumer<Throwable> errorCons) {
        Objects.requireNonNull(errorCons);
        return functor(f -> f.peekError(errorCons));
    }

    @Override
    public default SafeOpt<T> orGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return functor(f -> f.orGet(supplier));
    }

    @Override
    public default SafeOpt<T> orGetOpt(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        return functor(f -> f.orGetOpt(supplier));
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

    @Override
    public default <U> SafeOpt<U> select(Class<? extends U> clazz) {
        Objects.requireNonNull(clazz);
        return functor(f -> f.select(clazz));
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

    @Override
    public default SafeOpt<T> peek(UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(f -> f.peek(action));
    }

    @Override
    public default SafeOpt<T> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(f -> f.peek(action));
    }

    @Override
    public default SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(f -> f.peekOrElse(action, emptyAction));
    }

    @Override
    public default SafeOpt<T> peekOrElse(Consumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(f -> f.peekOrElse(action, emptyAction));
    }

    @Override
    public default SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(f -> f.peekOrElse(action, emptyAction));
    }

    @Override
    public default SafeOpt<T> peekOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(f -> f.peekOrElse(action, emptyAction));
    }

    @Override
    public default SafeOpt<T> peekOrDefault(T def, UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(f -> f.peekOrDefault(def, action));
    }

    @Override
    public default SafeOpt<T> peekOrDefault(T def, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(f -> f.peekOrDefault(def, action));
    }

    /**
     *
     * Collapsed.
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
     * Collapsed.
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
     * Collapsed.
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
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean isEmpty() {
        return collapse().isEmpty();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean hasValueOrError() {
        return collapse().hasValueOrError();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean hasError() {
        return collapse().hasError();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default boolean isPresent() {
        return collapse().isPresent();
    }

    /**
     *
     * Collapsed.
     *
     * {@inheritDoc}
     */
    @Override
    public default Optional<T> ignoringExceptionOptional() {
        return collapse().ignoringExceptionOptional();
    }

    /**
     *
     * Collapsed.
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
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        asIterator(this, true),
                        Spliterator.ORDERED),
                false);
    }

    @Override
    public default Stream<T> stream() throws NestedException {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        asIterator(this, false),
                        Spliterator.ORDERED),
                false);
    }
}

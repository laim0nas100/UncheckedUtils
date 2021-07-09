package lt.lb.uncheckedutils;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lt.lb.uncheckedutils.func.UncheckedBiFunction;
import lt.lb.uncheckedutils.func.UncheckedFunction;
import lt.lb.uncheckedutils.func.UncheckedSupplier;

/**
 *
 * {@code Optional} equivalent and in-place replacement, but with exception
 * capturing mapping.
 *
 * The main idea is to do any exception-throwing operation safely, without being
 * bombarded with exceptions. After all is set and done, do the exception
 * handling if need be.
 *
 * @author laim0nas100
 */
public interface ISafeOpt<T> {

    /**
     * Returns an {@code Optional} of current {@code SafeOpt}. This operation
     * prompts re-throwing previously caught exception (if one exists).
     *
     * @return an {@code Optional} with a present value if the specified value
     * is non-null, otherwise an empty {@code Optional}
     */
    public default Optional<T> asOptional() throws NestedException {
        return throwIfErrorAsNested().ignoringExceptionOptional();
    }

    /**
     * Returns an {@code Optional} of current {@code SafeOpt}. This operation
     * ignores previously caught exception (if one exists).
     *
     * @return an {@code Optional} with a present value if the specified value
     * is non-null, otherwise an empty {@code Optional}
     */
    public default Optional<T> ignoringExceptionOptional() {
        return Optional.ofNullable(rawValue());
    }

    public <A> ISafeOpt<A> produceNew(A rawValue, Throwable rawException);

    public default <A> ISafeOpt<A> produceError(Throwable rawException) {
        if (rawException == null) {
            return produceNew(null, null);
        }
        return produceNew(null, NestedException.unwrap(rawException));
    }

    public default <A> ISafeOpt<A> produceEmpty() {
        return produceNew(null, null);
    }

    public T rawValue();

    public Throwable rawException();

    /**
     * Return {@code true} if there is a value present, otherwise {@code false}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     */
    public default boolean isPresent() {
        return rawValue() != null;
    }

    /**
     *
     * @return {@code true} if there is a exception present, otherwise
     * {@code false}
     */
    public default boolean hasError() {
        return rawException() != null;
    }

    /**
     *
     * @return {@code true} if there is a exception or value present, otherwise
     * {@code false}
     */
    public default boolean hasValueOrError() {
        return isPresent() || hasError();
    }

    /**
     * Return {@code true} if there is no value present, otherwise
     * {@code false}.
     *
     * @return {@code true} if there is no value present, otherwise
     * {@code false}
     */
    public default boolean isEmpty() {
        return !isPresent();
    }

    /**
     * If a value is present, invoke the specified consumer with the value,
     * otherwise do nothing.
     *
     * @param consumer block to be executed if a value is present
     * @return this object
     * @throws NullPointerException if value is present and {@code consumer} is
     * null
     */
    public default ISafeOpt<T> ifPresent(Consumer<? super T> consumer) {
        if (isPresent()) {
            consumer.accept(rawValue());
        }
        return this;
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the given empty-based action.
     *
     * @param action the action to be performed, if a value is present
     * @param emptyAction the empty-based action to be performed, if no value is
     * present
     * @return this object
     * @throws NullPointerException if a value is present and the given action
     * is {@code null}, or no value is present and the given empty-based action
     * is {@code null}.
     */
    public default ISafeOpt<T> ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (isPresent()) {
            action.accept(rawValue());
        } else {
            emptyAction.run();
        }
        return this;
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the same action with the given default value. Whether
     * exception has occurred is irrelevant.
     *
     * @param def default value
     * @param action the action to be performed
     * @return this object
     * @throws NullPointerException if a value is present and the given action
     * is {@code null}. It is up to the caller to ensure that a passed default
     * value is not null.
     */
    public default ISafeOpt<T> ifPresentOrDefault(T def, Consumer<? super T> action) {
        action.accept(isPresent() ? rawValue() : def);
        return this;
    }

    /**
     * If a value is present, and the value matches the given predicate, return
     * an {@code SafeOpt} describing the value, otherwise return an empty
     * {@code SafeOpt}. If any exception occurs inside predicate, just returns
     * empty {@code SafeOpt} with captured exception.
     *
     * @param predicate a predicate to apply to the value, if present
     * @return an {@code SafeOpt} describing the value of this {@code SafeOpt}
     * if a value is present and the value matches the given predicate,
     * otherwise an empty {@code SafeOpt}
     * @throws NullPointerException if the predicate is null
     */
    public default ISafeOpt<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "Null predicate");
        if (!isPresent()) {
            return this;
        } else {
            try {
                return predicate.test(rawValue()) ? this : produceEmpty();
            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * If a value is present, apply the provided mapping function to it, and if
     * the result is non-null, return an {@code SafeOpt} describing the result.
     * Otherwise return an empty {@code SafeOpt}. If any exception occurs, just
     * returns empty {@code SafeOpt} with captured exception.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code SafeOpt} describing the result of applying a mapping
     * function to the value of this {@code SafeOpt}, if a value is present,
     * otherwise an empty {@code SafeOpt}
     * @throws NullPointerException if the mapping function is null
     */
    public default <U> ISafeOpt<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "Null map function");
        if (!isPresent()) {
            return produceError(rawException());
        } else {
            try {
                return produceNew(mapper.apply(rawValue()), null);
            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * If a value is present, apply the provided mapping function to it, and if
     * the result is non-null, return an {@code SafeOpt} describing the result.
     * Otherwise return an empty {@code SafeOpt}. If any exception occurs, just
     * returns empty {@code SafeOpt} with captured exception.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code SafeOpt} describing the result of applying a mapping
     * function to the value of this {@code SafeOpt}, if a value is present,
     * otherwise an empty {@code SafeOpt}
     * @throws NullPointerException if the mapping function is null
     */
    public default <U> ISafeOpt<U> map(UncheckedFunction<? super T, ? extends U> mapper) {
        return map((Function<? super T, ? extends U>) mapper);
    }

    /**
     * Sugar for {@code filter(clazz::isInstance).map(t -> (U) t);}
     *
     * @param <U> The type of the result of the mapping function
     * @param clazz instance to filter value
     * @return an {@code SafeOpt} of given action aggregation
     * @throws NullPointerException if the provided class is null
     */
    public default <U> ISafeOpt<U> select(Class<? extends U> clazz) {
        Objects.requireNonNull(clazz);
        return filter(clazz::isInstance).map(t -> (U) t);
    }

    /**
     * Analogous to flatMap, but with explicit exception ignoring
     *
     * @param <U>
     * @param mapper
     * @return
     */
    public default <U> ISafeOpt<U> flatMap(UncheckedFunction<? super T, ? extends ISafeOpt<? extends U>> mapper) {
        return flatMap((Function<? super T, ? extends ISafeOpt<? extends U>>) mapper);
    }

    /**
     * If a value is present, apply the provided {@code SafeOpt}-bearing mapping
     * function to it, return that result, otherwise return an empty
     * {@code SafeOpt}. This method is similar to {@link #map(Function)}, but
     * the provided mapper is one whose result is already an {@code SafeOpt},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code SafeOpt}.
     *
     * @param <U> The type parameter to the {@code SafeOpt} returned by
     * @param mapper a mapping function to apply to the value, if present the
     * mapping function
     * @return the result of applying an {@code SafeOpt}-bearing mapping
     * function to the value of this {@code SafeOpt}, if a value is present,
     * otherwise an empty {@code SafeOpt}
     * @throws NullPointerException if the mapping function is null
     */
    public default <U> ISafeOpt<U> flatMap(Function<? super T, ? extends ISafeOpt<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "Mapping function was null");
        if (!isPresent()) {
            return produceNew(null, rawException());
        } else {
            try {
                ISafeOpt<? extends U> opt = mapper.apply(rawValue());
                if (opt == null) {
                    return produceEmpty();
                }
                return produceNew(opt.rawValue(), rawException());

            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * If a value is present, apply the provided {@code Optional}-bearing
     * mapping function to it, return that result, otherwise return an empty
     * {@code SafeOpt}. This method is similar to {@link #map(Function)}, but
     * the provided mapper is one whose result is already an {@code SafeOpt},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code SafeOpt}.
     *
     * @param <U> The type parameter to the {@code SafeOpt} returned by
     * @param mapper a mapping function to apply to the value, if present the
     * mapping function
     * @return the result of applying an {@code Optional}-bearing mapping
     * function to the value of this {@code SafeOpt}, if a value is present,
     * otherwise an empty {@code SafeOpt}
     * @throws NullPointerException if the mapping function is null
     */
    public default <U> ISafeOpt<U> flatMapOpt(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "Mapping function was null");
        if (!isPresent()) {
            return produceError(rawException());
        } else {
            try {
                Optional<? extends U> apply = mapper.apply(rawValue());
                if (apply == null || !apply.isPresent()) {
                    return produceEmpty();
                } else {
                    return produceNew(apply.get(), null);
                }

            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * Analogous to flatMapOpt, but with explicit exception ignoring
     *
     * @param <U>
     * @param mapper
     * @return
     */
    public default <U> ISafeOpt<U> flatMapOpt(UncheckedFunction<? super T, ? extends Optional<? extends U>> mapper) {
        return flatMapOpt((Function<? super T, ? extends Optional<? extends U>>) mapper);
    }

    /**
     * If a value is present, returns an {@code SafeOpt} describing the value,
     * otherwise returns an {@code SafeOpt} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     * @return returns an {@code SafeOpt} describing the value of this
     * {@code SafeOpt}, if a value is present, otherwise an wrapped
     * {@code SafeOpt} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null}
     */
    public default ISafeOpt<T> orGetOpt(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "Supplier was null");
        if (isPresent()) {
            return this;
        } else {
            try {
                Optional<? extends T> get = supplier.get();
                if (get == null || !get.isPresent()) {
                    return this;
                } else {
                    return produceNew(get.get(), null);
                }
            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * If a value is present, returns an {@code SafeOpt} describing the value,
     * otherwise returns an {@code SafeOpt} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code SafeOpt}
     * @return returns an {@code SafeOpt} describing the value of this
     * {@code SafeOpt}, if a value is present, otherwise an wrapped
     * {@code SafeOpt} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null}
     */
    public default ISafeOpt<T> orGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "Supplier was null");
        if (isPresent()) {
            return this;
        } else {
            try {
                return produceNew(supplier.get(), null);
            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * Only if error is present, feed it to the consumer. If consumer throws any
     * errors they are not caught and just propagate upwards, so use this method
     * with caution.
     *
     * @param errorCons
     * @return the same unmodified object
     */
    public default ISafeOpt<T> peekError(Consumer<Throwable> errorCons) {
        Objects.requireNonNull(errorCons);
        if (hasError()) {
            errorCons.accept(rawException());
        }
        return this;
    }

    /**
     * Results in empty instance of {@link SafeOpt}, but keeps the error if one
     * is present.
     *
     * @return
     */
    public default ISafeOpt<Void> keepError() {
        return produceError(rawException());
    }

    /**
     * If both values are present (in this {@code SafeOpt} and provided
     * {@code SafeOpt with} object), then proceed with combining those values in
     * a safe manner, capturing any exceptions. With and mapper must be
     * explicitly not null.
     *
     * @param <U>
     * @param <P>
     * @param with
     * @param mapper
     * @return
     */
    public default <U, P> ISafeOpt<U> mapCombine(ISafeOpt<? extends P> with, UncheckedBiFunction<? super T, ? super P, ? extends U> mapper) {
        return mapCombine(with, (BiFunction<T, P, U>) mapper);
    }

    /**
     * If both values are present (in this {@code SafeOpt} and provided
     * {@code SafeOpt with} object, then proceed with combining those values in
     * a safe manner, capturing any exceptions. With and mapper must be
     * explicitly not null.
     *
     * @param <U>
     * @param <P>
     * @param with
     * @param mapper
     * @return
     */
    public default <U, P> ISafeOpt<U> mapCombine(ISafeOpt<? extends P> with, BiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with, "Null with object");
        Objects.requireNonNull(mapper, "Null map function");

        if (!isPresent()) {
            return produceError(rawException());
        } else if (!with.isPresent()) {
            return produceError(with.rawException());
        } else {
            try {
                U apply = mapper.apply(rawValue(), with.rawValue());
                return produceNew(apply, null);
            } catch (Throwable t) {
                return produceError(t);
            }
        }
    }

    /**
     * If exception has occurred, throws it wrapped in NestedException. If a
     * value is present, returns a sequential {@link Stream} containing only
     * that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    public default Stream<T> stream() throws NestedException {
        return throwIfErrorAsNested().ignoringExceptionStream();
    }

    /**
     * If a value is present, returns a sequential {@link Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    public default Stream<T> ignoringExceptionStream() {
        if (!isPresent()) {
            return Stream.empty();
        } else {
            return Stream.of(rawValue());
        }
    }

    /**
     * If a value is present in this {@code SafeOpt}, returns the value,
     * otherwise throws {@code NoSuchElementException}. Also, throws any caught
     * exception wrapped in {@link NestedException}.
     *
     * @return the non-null value held by this {@code Optional}
     * @throws NoSuchElementException if there is no value present
     *
     * @see SafeOpt#isPresent()
     */
    public default T get() {
        if (isPresent()) {
            return rawValue();
        }
        throwIfErrorAsNested();
        throw new NoSuchElementException("No value present");
    }

    /**
     * Return the value if present, otherwise return {@code other}.
     *
     * @param other the value to be returned if there is no value present, may
     * be null
     * @return the value, if present, otherwise {@code other}
     */
    public default T orElse(T other) {
        T val = rawValue();
        return val != null ? val : other;
    }

    /**
     * Return the value if present, otherwise invoke {@code other} and return
     * the result of that invocation.
     *
     * @param other a {@code Supplier} whose result is returned if no value is
     * present
     * @return the value if present otherwise the result of {@code other.get()}
     * @throws NullPointerException if value is not present and {@code other} is
     * null
     */
    public default T orElseGet(Supplier<? extends T> other) {
        T val = rawValue();
        return val != null ? val : Objects.requireNonNull(other, "Null supplier").get();
    }

    /**
     * Return the contained value, if present, otherwise throw an exception to
     * be created by the provided supplier.
     *
     * @apiNote A method reference to the exception constructor with an empty
     * argument list can be used as the supplier. For example,
     * {@code IllegalStateException::new}
     *
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier The supplier which will return the exception to
     * be thrown
     * @return the present value
     * @throws X if there is no value present
     * @throws NullPointerException if no value is present and
     * {@code exceptionSupplier} is null
     */
    public default <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        T val = rawValue();
        if (val != null) {
            return val;
        } else {
            throw exceptionSupplier.get();
        }
    }

    /**
     * Throw matching type of exception if present, or return contained value
     * (if no contained value, throws NoSuchElementException)
     *
     *
     * @param <Ex> Type of the exception to be thrown
     * @param type
     * @return the present value
     * @throws Ex if there is such exception
     */
    public default <Ex extends Throwable> T throwIfErrorOrGet(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return throwIfError(type).get();
    }

    /**
     * If an error has occurred, terminate by throwing such error wrapped in
     * NestedException
     *
     * @return returns an {@code SafeOpt} describing the value of this
     * {@code SafeOpt}, if a value is present
     * @throws NestedException if any error was present
     */
    public default ISafeOpt<T> throwIfErrorAsNested() {
        Throwable threw = rawException();
        if (threw != null) {
            throw NestedException.of(threw);
        }
        return this;
    }

    /**
     * If an {@link RuntimeException} has occurred, terminate by throwing such
     * exception.
     *
     * @return returns an {@code SafeOpt} describing the value of this
     * {@code SafeOpt}, if a value is present
     * @throws RuntimeException if any that kind of error was present
     */
    public default ISafeOpt<T> throwIfErrorRuntime() {
        return throwIfError(RuntimeException.class);
    }

    /**
     * Throw matching type of exception if present
     *
     *
     * @param <Ex> Type of the exception to be thrown
     * @param type
     * @return this object
     * @throws Ex if there is such exception
     */
    public default <Ex extends Throwable> ISafeOpt<T> throwIfError(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        ISafeOpt<Ex> select = getError().select(type);
        if (select.isPresent()) {
            throw select.get();
        }
        return this;
    }

    /**
     * Throw matching type of exception if present, or throws error wrapped is
     * {@link NestedException}. If error is not present, does nothing.
     *
     *
     * @param <Ex> Type of the exception to be thrown
     * @param type
     * @return this object
     * @throws Ex if there is such exception
     */
    public default <Ex extends Throwable> ISafeOpt<T> throwIfErrorUnwrapping(Class<Ex> type) throws Ex {
        ISafeOpt<Throwable> error = getError();
        ISafeOpt<Ex> select = error.select(type);
        if (select.isPresent()) {
            throw select.get();
        } else if (error.isPresent()) {
            throw NestedException.of(error.get());
        }
        return this;
    }

    /**
     * Shorthand for {@link SafeOpt#throwIfErrorAsNested() } and
     * {@code orElse(null)}
     *
     * @return
     */
    public default T throwNestedOrNull() {
        return throwIfErrorAsNested().orElse(null);
    }

    /**
     * Shorthand for {@link SafeOpt#throwIfErrorAsNested() } and
     * {@code orElse(Object)}
     *
     * @return
     */
    public default T throwNestedOr(T val) {
        return throwIfErrorAsNested().orElse(val);
    }

    /**
     * Shorthand for orElse(null)
     *
     * @return
     */
    public default T orNull() {
        return orElse(null);
    }

    /**
     * Shorthand for filter and isPresent operation
     *
     * @param predicate
     * @return
     */
    public default boolean isPresentWhen(Predicate<? super T> predicate) {
        return filter(predicate).isPresent();
    }

    /**
     * {@link SafeOpt} instance with optional {@link Throwable} error inside.
     *
     * @return
     */
    public default ISafeOpt<Throwable> getError() {
        return produceNew(rawException(), null);
    }

}

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
@Deprecated
public class SafeOptOld<T> {

    /**
     * If non-null, the value; if null, indicates no value is present
     */
    protected final T val;

    /**
     * If non-null, the exception; if null, indicates no exception is present
     */
    protected final Throwable threw;

    protected static final SafeOptOld<?> READY = new SafeOptOld<>(new Object(), null);

    protected static final SafeOptOld<?> EMPTY = new SafeOptOld<>(null, null);

    protected SafeOptOld(T value, Throwable throwable) {
        val = value;
        threw = throwable;
    }

    /**
     * Returns an {@code SafeOptOld} with the specified present non-null value.
     *
     * @param <T> the class of the value
     * @param val the value to be present, which must be non-null
     * @return an {@code SafeOptOld} with the value present
     * @throws NullPointerException if value is null
     */
    public static <T> SafeOptOld<T> of(T val) {
        Objects.requireNonNull(val);
        return new SafeOptOld(val, null);
    }

    /**
     * Returns an {@code SafeOptOld} with the specified present non-null value.
     *
     * @param <T> the class of the value
     * @param sup the value supplier to be present
     * @return an {@code SafeOptOld} with the value present, or an empty
     * {@code SafeOptOld} if supplier or it's value is null. If exception occurred
     * anywhere, then it will be captured and empty {@code SafeOptOld} with such
     * exception will be returned
     */
    public static <T> SafeOptOld<T> ofGet(Supplier<? extends T> sup) {
        Objects.requireNonNull(sup);
        return READY.map(m -> sup.get());
    }
    
    /**
     * Returns an {@code SafeOptOld} with the specified present non-null value.
     *
     * @param <T> the class of the value
     * @param sup the value supplier to be present
     * @return an {@code SafeOptOld} with the value with {@code SafeOptOld} present, or an empty
     * {@code SafeOptOld} if supplier or it's value is null or {@code SafeOptOld} with exception. If exception occurred
     * anywhere, then it will be captured and empty {@code SafeOptOld} with such
     * exception will be returned
     */
    public static <T> SafeOptOld<T> ofFlatGet(Supplier<? extends SafeOptOld<T>> sup) {
        Objects.requireNonNull(sup);
        return READY.flatMap(m -> sup.get());
    }

    /**
     * Returns an {@code SafeOptOld} with the specified present non-null value.
     *
     * @param <T> the class of the value
     * @param sup the value supplier to be present
     * @return an {@code SafeOptOld} with the value present, or an empty
     * {@code SafeOptOld} if supplier or it's value is null. If exception occurred
     * anywhere, then it will be captured and empty {@code SafeOptOld} with such
     * exception will be returned
     */
    public static <T> SafeOptOld<T> ofGet(UncheckedSupplier<? extends T> sup) {
        Objects.requireNonNull(sup);
        return READY.map(m -> sup.getUnchecked());
    }
    
    /**
     * Returns an {@code SafeOptOld} with the specified present non-null value.
     *
     * @param <T> the class of the value
     * @param sup the value supplier to be present
     * @return an {@code SafeOptOld} with the value with {@code SafeOptOld} present, or an empty
     * {@code SafeOptOld} if supplier or it's value is null or {@code SafeOptOld} with exception. If exception occurred
     * anywhere, then it will be captured and empty {@code SafeOptOld} with such
     * exception will be returned
     */
    public static <T> SafeOptOld<T> ofFlatGet(UncheckedSupplier<? extends SafeOptOld<T>> sup) {
        Objects.requireNonNull(sup);
        return READY.flatMap(m -> sup.get());
    }

    /**
     * Returns an empty {@code SafeOptOld} instance.
     *
     * @param <T> Type of the non-existent value
     * @return an empty {@code SafeOptOld}
     */
    public static <T> SafeOptOld<T> empty() {
        return (SafeOptOld<T>) EMPTY;
    }

    /**
     * Returns an empty {@code SafeOptOld} instance with given error. Error must be
     * provided.
     *
     * @param <T> Type of the non-existent value
     * @param error
     * @return an empty {@code SafeOptOld} with an error.
     */
    public static <T> SafeOptOld<T> error(Throwable error) {
        return new SafeOptOld<>(null, Objects.requireNonNull(error));
    }

    private static <T> SafeOptOld<T> errorOrEmpty(Throwable error) {
        return error == null ? empty() : new SafeOptOld<>(null, error);
    }

    /**
     * Returns an {@code SafeOptOld} describing the specified value, if non-null,
     * otherwise returns an empty {@code Optional}.
     *
     * @param <T> the class of the value
     * @param val the possibly-null value to describe
     * @return an {@code SafeOptOld} with a present value if the specified value is
     * non-null, otherwise an empty {@code Optional}
     */
    public static <T> SafeOptOld<T> ofNullable(T val) {
        return val == null ? SafeOptOld.empty() : new SafeOptOld(val, null);
    }

    /**
     * Simple mapping from {@code Optional} to {@code SafeOptOld}
     *
     * @param <T>
     * @param opt
     * @return
     */
    public static <T> SafeOptOld<T> ofOptional(Optional<? extends T> opt) {
        if (opt == null) {
            return SafeOptOld.empty();
        }
        return opt.isPresent() ? SafeOptOld.of(opt.get()) : SafeOptOld.empty();
    }

    /**
     * Returns an {@code Optional} of current {@code SafeOptOld}. This operation
     * prompts re-throwing previously caught exception (if one exists).
     *
     * @return an {@code Optional} with a present value if the specified value
     * is non-null, otherwise an empty {@code Optional}
     */
    public Optional<T> asOptional() throws NestedException {
        return throwIfErrorAsNested().ignoringExceptionOptional();
    }

    /**
     * Returns an {@code Optional} of current {@code SafeOptOld}. This operation
     * ignores previously caught exception (if one exists).
     *
     * @return an {@code Optional} with a present value if the specified value
     * is non-null, otherwise an empty {@code Optional}
     */
    public Optional<T> ignoringExceptionOptional() {
        return Optional.ofNullable(val);
    }

    /**
     * Return {@code true} if there is a value present, otherwise {@code false}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     */
    public boolean isPresent() {
        return val != null;
    }

    /**
     *
     * @return {@code true} if there is a exception present, otherwise
     * {@code false}
     */
    public boolean hasError() {
        return threw != null;
    }

    /**
     *
     * @return {@code true} if there is a exception or value present, otherwise
     * {@code false}
     */
    public boolean hasValueOrError() {
        return isPresent() || hasError();
    }

    /**
     * Return {@code true} if there is no value present, otherwise
     * {@code false}.
     *
     * @return {@code true} if there is no value present, otherwise
     * {@code false}
     */
    public boolean isEmpty() {
        return val == null;
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
    public SafeOptOld<T> ifPresent(Consumer<? super T> consumer) {
        if (isPresent()) {
            consumer.accept(val);
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
    public SafeOptOld<T> ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (isPresent()) {
            action.accept(val);
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
    public SafeOptOld<T> ifPresentOrDefault(T def, Consumer<? super T> action) {
        action.accept(isPresent() ? val : def);
        return this;
    }

    /**
     * If a value is present, and the value matches the given predicate, return
     * an {@code SafeOptOld} describing the value, otherwise return an empty
     * {@code SafeOptOld}. If any exception occurs inside predicate, just returns
     * empty {@code SafeOptOld} with captured exception.
     *
     * @param predicate a predicate to apply to the value, if present
     * @return an {@code SafeOptOld} describing the value of this {@code SafeOptOld}
     * if a value is present and the value matches the given predicate,
     * otherwise an empty {@code SafeOptOld}
     * @throws NullPointerException if the predicate is null
     */
    public SafeOptOld<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "Null predicate");
        if (!isPresent()) {
            return SafeOptOld.errorOrEmpty(threw);
        } else {
            try {
                return predicate.test(val) ? this : empty();
            } catch (Throwable t) {
                return SafeOptOld.errorOrEmpty(NestedException.unwrap(t));
            }
        }
    }

    /**
     * If a value is present, apply the provided mapping function to it, and if
     * the result is non-null, return an {@code SafeOptOld} describing the result.
     * Otherwise return an empty {@code SafeOptOld}. If any exception occurs, just
     * returns empty {@code SafeOptOld} with captured exception.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code SafeOptOld} describing the result of applying a mapping
     * function to the value of this {@code SafeOptOld}, if a value is present,
     * otherwise an empty {@code SafeOptOld}
     * @throws NullPointerException if the mapping function is null
     */
    public <U> SafeOptOld<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "Null map function");
        if (!isPresent()) {
            return SafeOptOld.errorOrEmpty(threw);
        } else {
            try {
                return SafeOptOld.ofNullable(mapper.apply(val));
            } catch (Throwable t) {
                return SafeOptOld.errorOrEmpty(NestedException.unwrap(t));
            }
        }
    }

    /**
     * If a value is present, apply the provided mapping function to it, and if
     * the result is non-null, return an {@code SafeOptOld} describing the result.
     * Otherwise return an empty {@code SafeOptOld}. If any exception occurs, just
     * returns empty {@code SafeOptOld} with captured exception.
     *
     * @param <U> The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code SafeOptOld} describing the result of applying a mapping
     * function to the value of this {@code SafeOptOld}, if a value is present,
     * otherwise an empty {@code SafeOptOld}
     * @throws NullPointerException if the mapping function is null
     */
    public <U> SafeOptOld<U> map(UncheckedFunction<? super T, ? extends U> mapper) {
        return map((Function<? super T, ? extends U>) mapper);
    }

    /**
     * Sugar for {@code filter(clazz::isInstance).map(t -> (U) t);}
     *
     * @param <U> The type of the result of the mapping function
     * @param clazz instance to filter value
     * @return an {@code SafeOptOld} of given action aggregation
     * @throws NullPointerException if the provided class is null
     */
    public <U> SafeOptOld<U> select(Class<? extends U> clazz) {
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
    public <U> SafeOptOld<U> flatMap(UncheckedFunction<? super T, ? extends SafeOptOld<? extends U>> mapper) {
        return flatMap((Function<? super T, ? extends SafeOptOld<? extends U>>) mapper);
    }

    /**
     * If a value is present, apply the provided {@code SafeOptOld}-bearing mapping
     * function to it, return that result, otherwise return an empty
     * {@code SafeOptOld}. This method is similar to {@link #map(Function)}, but
     * the provided mapper is one whose result is already an {@code SafeOptOld},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code SafeOptOld}.
     *
     * @param <U> The type parameter to the {@code SafeOptOld} returned by
     * @param mapper a mapping function to apply to the value, if present the
     * mapping function
     * @return the result of applying an {@code SafeOptOld}-bearing mapping
     * function to the value of this {@code SafeOptOld}, if a value is present,
     * otherwise an empty {@code SafeOptOld}
     * @throws NullPointerException if the mapping function is null
     */
    public <U> SafeOptOld<U> flatMap(Function<? super T, ? extends SafeOptOld<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "Mapping function was null");
        if (!isPresent()) {
            return SafeOptOld.errorOrEmpty(threw);
        } else {
            try {
                SafeOptOld<? extends U> opt = mapper.apply(val);
                if (opt == null) {
                    return SafeOptOld.empty();
                }
                return new SafeOptOld<>(opt.val, opt.threw);

            } catch (Throwable t) {
                return SafeOptOld.errorOrEmpty(NestedException.unwrap(t));
            }
        }
    }

    /**
     * If a value is present, apply the provided {@code Optional}-bearing
     * mapping function to it, return that result, otherwise return an empty
     * {@code SafeOptOld}. This method is similar to {@link #map(Function)}, but
     * the provided mapper is one whose result is already an {@code SafeOptOld},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code SafeOptOld}.
     *
     * @param <U> The type parameter to the {@code SafeOptOld} returned by
     * @param mapper a mapping function to apply to the value, if present the
     * mapping function
     * @return the result of applying an {@code Optional}-bearing mapping
     * function to the value of this {@code SafeOptOld}, if a value is present,
     * otherwise an empty {@code SafeOptOld}
     * @throws NullPointerException if the mapping function is null
     */
    public <U> SafeOptOld<U> flatMapOpt(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "Mapping function was null");
        if (!isPresent()) {
            return SafeOptOld.errorOrEmpty(threw);
        } else {
            try {
                return SafeOptOld.ofOptional(mapper.apply(val));
            } catch (Throwable t) {
                return SafeOptOld.errorOrEmpty(NestedException.unwrap(t));
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
    public <U> SafeOptOld<U> flatMapOpt(UncheckedFunction<? super T, ? extends Optional<? extends U>> mapper) {
        return flatMapOpt((Function<? super T, ? extends Optional<? extends U>>) mapper);
    }

    /**
     * If a value is present, returns an {@code SafeOptOld} describing the value,
     * otherwise returns an {@code SafeOptOld} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     * @return returns an {@code SafeOptOld} describing the value of this
     * {@code SafeOptOld}, if a value is present, otherwise an wrapped
     * {@code SafeOptOld} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null}
     */
    public SafeOptOld<T> orGetOpt(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "Supplier was null");
        if (isPresent()) {
            return this;
        } else {
            return SafeOptOld.ofOptional(supplier.get());
        }
    }

    /**
     * If a value is present, returns an {@code SafeOptOld} describing the value,
     * otherwise returns an {@code SafeOptOld} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code SafeOptOld}
     * @return returns an {@code SafeOptOld} describing the value of this
     * {@code SafeOptOld}, if a value is present, otherwise an wrapped
     * {@code SafeOptOld} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null}
     */
    public SafeOptOld<T> orGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "Supplier was null");
        if (isPresent()) {
            return this;
        } else {
            return SafeOptOld.READY.map(m -> supplier.get());
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
    public SafeOptOld<T> peekError(Consumer<Throwable> errorCons) {
        Objects.requireNonNull(errorCons);
        if (hasError()) {
            errorCons.accept(threw);
        }
        return this;
    }

    /**
     * Results in empty instance of {@link SafeOptOld}, but keeps the error if one
     * is present.
     *
     * @return
     */
    public SafeOptOld<Void> keepError() {
        return SafeOptOld.errorOrEmpty(threw);
    }

    /**
     * If both values are present (in this {@code SafeOptOld} and provided
     * {@code SafeOptOld with} object), then proceed with combining those values in
     * a safe manner, capturing any exceptions. With and mapper must be
     * explicitly not null.
     *
     * @param <U>
     * @param <P>
     * @param with
     * @param mapper
     * @return
     */
    public <U, P> SafeOptOld<U> mapCombine(SafeOptOld<? extends P> with, UncheckedBiFunction<? super T, ? super P, ? extends U> mapper) {
        return mapCombine(with, (BiFunction<T, P, U>) mapper);
    }

    /**
     * If both values are present (in this {@code SafeOptOld} and provided
     * {@code SafeOptOld with} object, then proceed with combining those values in
     * a safe manner, capturing any exceptions. With and mapper must be
     * explicitly not null.
     *
     * @param <U>
     * @param <P>
     * @param with
     * @param mapper
     * @return
     */
    public <U, P> SafeOptOld<U> mapCombine(SafeOptOld<? extends P> with, BiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with, "Null with object");
        Objects.requireNonNull(mapper, "Null map function");

        if (!isPresent()) {
            return SafeOptOld.errorOrEmpty(threw);
        } else if (!with.isPresent()) {
            return SafeOptOld.errorOrEmpty(with.threw);
        } else {
            try {
                return SafeOptOld.ofNullable(mapper.apply(val, with.val));
            } catch (Throwable t) {
                return SafeOptOld.errorOrEmpty(NestedException.unwrap(t));
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
    public Stream<T> stream() throws NestedException {
        return throwIfErrorAsNested().ignoringExceptionStream();
    }

    /**
     * If a value is present, returns a sequential {@link Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    public Stream<T> ignoringExceptionStream() {
        if (!isPresent()) {
            return Stream.empty();
        } else {
            return Stream.of(val);
        }
    }

    /**
     * Select first {@code SafeOptOld} which is present, otherwise return empty;
     *
     * @param <U>
     * @param options
     * @return
     */
    public static <U> SafeOptOld<U> selectFirstPresent(SafeOptOld<U>... options) {
        Objects.requireNonNull(options, "Null options");
        for (SafeOptOld<U> opt : options) {
            if (opt.isPresent()) {
                return opt;
            }
        }
        return SafeOptOld.empty();
    }

    /**
     * If a value is present in this {@code SafeOptOld}, returns the value,
     * otherwise throws {@code NoSuchElementException}. Also, throws any caught
     * exception wrapped in {@link NestedException}.
     *
     * @return the non-null value held by this {@code Optional}
     * @throws NoSuchElementException if there is no value present
     *
     * @see SafeOptOld#isPresent()
     */
    public T get() {
        if (isPresent()) {
            return val;
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
    public T orElse(T other) {
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
    public T orElseGet(Supplier<? extends T> other) {
        return val != null ? val : other.get();
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
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
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
    public <Ex extends Throwable> T throwIfErrorOrGet(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return throwIfError(type).get();
    }

    /**
     * If an error has occurred, terminate by throwing such error wrapped in
     * NestedException
     *
     * @return returns an {@code SafeOptOld} describing the value of this
     * {@code SafeOptOld}, if a value is present
     * @throws NestedException if any error was present
     */
    public SafeOptOld<T> throwIfErrorAsNested() {
        if (threw != null) {
            throw NestedException.of(threw);
        }
        return this;
    }

    /**
     * If an {@link RuntimeException} has occurred, terminate by throwing such
     * exception.
     *
     * @return returns an {@code SafeOptOld} describing the value of this
     * {@code SafeOptOld}, if a value is present
     * @throws RuntimeException if any that kind of error was present
     */
    public SafeOptOld<T> throwIfErrorRuntime() {
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
    public <Ex extends Throwable> SafeOptOld<T> throwIfError(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        SafeOptOld<Ex> select = getError().select(type);
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
    public <Ex extends Throwable> SafeOptOld<T> throwIfErrorUnwrapping(Class<Ex> type) throws Ex {
        SafeOptOld<Throwable> error = getError();
        SafeOptOld<Ex> select = error.select(type);
        if (select.isPresent()) {
            throw select.get();
        } else if (error.isPresent()) {
            throw NestedException.of(error.get());
        }
        return this;
    }

    /**
     * Shorthand for {@link SafeOptOld#throwIfErrorAsNested() } and
     * {@code orElse(null)}
     *
     * @return
     */
    public T throwNestedOrNull() {
        return throwIfErrorAsNested().orElse(null);
    }
    
    /**
     * Shorthand for {@link SafeOptOld#throwIfErrorAsNested() } and
     * {@code orElse(Object)}
     *
     * @return
     */
    public T throwNestedOr(T val) {
        return throwIfErrorAsNested().orElse(val);
    }

    /**
     * Shorthand for orElse(null)
     *
     * @return
     */
    public T orNull() {
        return orElse(null);
    }

    /**
     * Shorthand for filter and isPresent operation
     *
     * @param predicate
     * @return
     */
    public boolean isPresentWhen(Predicate<? super T> predicate) {
        return filter(predicate).isPresent();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.val);
        hash = 23 * hash + Objects.hashCode(this.threw);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SafeOptOld<?> other = (SafeOptOld<?>) obj;
        if (!Objects.equals(this.val, other.val)) {
            return false;
        }
        if (!Objects.equals(this.threw, other.threw)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (val != null) {
            return String.format("SafeOptOld[%s]", val);
        }
        if (threw != null) {
            return String.format("SafeOptOld.error[%s]", threw);
        }
        return "SafeOptOld.empty";
    }

    /**
     * {@link SafeOptOld} instance with optional {@link Throwable} error inside.
     *
     * @return
     */
    public SafeOptOld<Throwable> getError() {
        return SafeOptOld.ofNullable(threw);
    }

}

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
 *
 * @author laim0nas100
 */
public class SafeOptLazy<S, T> extends SafeOptBase<T> {

    protected final SafeOpt<S> initial;
    protected final Function<SafeOpt<S>, SafeOpt<T>> function;

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
        Objects.requireNonNull(func);
        this.initial = SafeOpt.ofNullable(initialVal);
        this.function = f -> f.map(func);
    }

    protected SafeOptLazy(Throwable initialVal) {
        this.initial = SafeOpt.error(initialVal);
        this.function = f -> (SafeOpt<T>) f;
    }

    protected SafeOpt<T> collapse() {
        if (collapsed != null) {
            return collapsed;
        }
        if (inCollapse.compareAndSet(false, true)) {
            collapsed = function.apply(initial);
        }
        return Objects.requireNonNull(collapsed, "collapse accessed from another thread before finishing or collapsed function returned null");
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
    public T rawValue() {
        return collapse().rawValue();
    }

    @Override
    public Throwable rawException() {
        return collapse().rawException();
    }

    @Override
    public SafeOpt<Throwable> getError() {
        return functor(init -> init.getError());
    }

    protected <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        if (collapsed != null) {
            return func.apply(collapsed);// no more lazy application after collapse
        }
        return new SafeOptLazy<>(initial, function.andThen(func));
    }

    @Override
    public boolean isPresentWhen(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return collapse().isPresentWhen(predicate);
    }

    @Override
    public T orNull() {
        return collapse().orNull();
    }

    @Override
    public T throwNestedOr(T val) {
        return collapse().throwNestedOr(val);
    }

    @Override
    public T throwNestedOrNull() {
        return collapse().throwNestedOrNull();
    }

    @Override
    public <Ex extends Throwable> SafeOpt<T> throwIfErrorUnwrapping(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfErrorUnwrapping(type);
    }

    @Override
    public <Ex extends Throwable> SafeOpt<T> throwIfError(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfError(type);
    }

    @Override
    public SafeOpt<T> throwIfErrorRuntime() {
        return collapse().throwIfErrorRuntime();
    }

    @Override
    public SafeOpt<T> throwIfErrorAsNested() {
        return collapse().throwIfErrorAsNested();
    }

    @Override
    public <Ex extends Throwable> T throwIfErrorOrGet(Class<Ex> type) throws Ex {
        Objects.requireNonNull(type);
        return collapse().throwIfErrorOrGet(type);
    }

    @Override
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Objects.requireNonNull(exceptionSupplier);
        return collapse().orElseThrow(exceptionSupplier);
    }

    @Override
    public T orElseGet(Supplier<? extends T> other) {
        return collapse().orElseGet(other);
    }

    @Override
    public T orElse(T other) {
        return collapse().orElse(other);
    }

    @Override
    public T get() {
        return collapse().get();
    }

    public static <S, T> Iterator<T> asIterator(final SafeOptLazy<S, T> safeOpt, boolean ignoreException) {
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
    public Stream<T> ignoringExceptionStream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        asIterator(this, true),
                        Spliterator.ORDERED),
                false);
    }

    @Override
    public Stream<T> stream() throws NestedException {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        asIterator(this, false),
                        Spliterator.ORDERED),
                false);
    }

    @Override
    public <U, P> SafeOpt<U> mapCombine(SafeOpt<? extends P> with, BiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with);
        Objects.requireNonNull(mapper);
        return functor(f -> f.mapCombine(with, mapper));
    }

    @Override
    public <U, P> SafeOpt<U> mapCombine(SafeOpt<? extends P> with, UncheckedBiFunction<? super T, ? super P, ? extends U> mapper) {
        Objects.requireNonNull(with);
        Objects.requireNonNull(mapper);
        return functor(f -> f.mapCombine(with, mapper));
    }

    @Override
    public SafeOpt<Void> keepError() {
        return functor(f -> f.keepError());
    }

    @Override
    public SafeOpt<T> peekError(Consumer<Throwable> errorCons) {
        Objects.requireNonNull(errorCons);
        return functor(f -> f.peekError(errorCons));
    }

    @Override
    public SafeOpt<T> orGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return functor(f -> f.orGet(supplier));
    }

    @Override
    public SafeOpt<T> orGetOpt(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        return functor(f -> f.orGetOpt(supplier));
    }

    @Override
    public <U> SafeOpt<U> flatMapOpt(UncheckedFunction<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMapOpt(mapper));
    }

    @Override
    public <U> SafeOpt<U> flatMapOpt(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMapOpt(mapper));
    }

    @Override
    public <U> SafeOpt<U> flatMap(Function<? super T, ? extends SafeOpt<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMap(mapper));
    }

    @Override
    public <U> SafeOpt<U> flatMap(UncheckedFunction<? super T, ? extends SafeOpt<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return functor(f -> f.flatMap(mapper));
    }

    @Override
    public <U> SafeOpt<U> select(Class<? extends U> clazz) {
        Objects.requireNonNull(clazz);
        return functor(init -> init.select(clazz));
    }

    @Override
    public <U> SafeOpt<U> map(UncheckedFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return functor(init -> init.map(mapper));
    }

    @Override
    public <U> SafeOpt<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return functor(init -> init.map(mapper));
    }

    @Override
    public SafeOpt<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return functor(init -> init.filter(predicate));
    }

    @Override
    public SafeOpt<T> peek(UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(init -> init.peek(action));
    }

    @Override
    public SafeOpt<T> peek(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(init -> init.peek(action));
    }

    @Override
    public SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(init -> init.peekOrElse(action, emptyAction));
    }

    @Override
    public SafeOpt<T> peekOrElse(Consumer<? super T> action, UncheckedRunnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(init -> init.peekOrElse(action, emptyAction));
    }

    @Override
    public SafeOpt<T> peekOrElse(UncheckedConsumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(init -> init.peekOrElse(action, emptyAction));
    }

    @Override
    public SafeOpt<T> peekOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        return functor(init -> init.peekOrElse(action, emptyAction));
    }

    @Override
    public SafeOpt<T> peekOrDefault(T def, UncheckedConsumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(init -> init.peekOrDefault(def, action));
    }

    @Override
    public SafeOpt<T> peekOrDefault(T def, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return functor(init -> init.peekOrDefault(def, action));
    }

    @Override
    public void ifPresent(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer);
        collapse().ifPresent(consumer);
    }

    @Override
    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        collapse().ifPresentOrElse(action, emptyAction);
    }

    @Override
    public void ifPresentOrDefault(T def, Consumer<? super T> action) {
        Objects.requireNonNull(action);
        collapse().ifPresentOrDefault(def, action);
    }

    @Override
    public boolean isEmpty() {
        return collapse().isEmpty();
    }

    @Override
    public boolean hasValueOrError() {
        return collapse().hasValueOrError();
    }

    @Override
    public boolean hasError() {
        return collapse().hasError();
    }

    @Override
    public boolean isPresent() {
        return collapse().isPresent();
    }

    @Override
    public <A> SafeOpt<A> produceEmpty() {
        return new SafeOptLazy<>();
    }

    @Override
    public <A> SafeOpt<A> produceError(Throwable rawException) {
        if (rawException == null) {
            return produceEmpty();
        }
        return new SafeOptLazy<>(rawException);
    }

    @Override
    public Optional<T> ignoringExceptionOptional() {
        return collapse().ignoringExceptionOptional();
    }

    @Override
    public Optional<T> asOptional() throws NestedException {
        return collapse().asOptional();
    }
}

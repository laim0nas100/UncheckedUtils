package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author laim0nas100
 */
public class Thrower<T extends Throwable> implements Supplier<T> {

    public final T throwable;

    public Thrower(T throwable) {
        this.throwable = Objects.requireNonNull(throwable);
    }

    public static <A extends Throwable> Thrower<A> of(A throwable) {
        return new Thrower<>(throwable);
    }

    /**
     * If throwable is instanceof {@link InterruptedException}
     *
     */
    public boolean isInterrupted() {
        return throwable instanceof InterruptedException;
    }

    /**
     * If throwable is instanceof {@link RuntimeException}
     *
     */
    public boolean isRuntime() {
        return throwable instanceof RuntimeException;
    }

    /**
     * If throwable is instanceof {@link Error }
     *
     */
    public boolean isError() {
        return throwable instanceof Error;
    }

    /**
     * If throwable is instanceof {@link RuntimeException} or {@link Error }
     *
     */
    public boolean isUnchecked() {
        return isRuntime() || isError();
    }

    /**
     * If throwable is instanceof {@link Error} or {@link not Thrower#isUnchecked()
     * }
     *
     * @return
     */
    public boolean isChecked() {
        return throwable instanceof Exception || !isUnchecked();
    }

    /**
     * If got {@link InterruptedException} then re-interrupts current thread and
     * return true, false otherwise
     *
     */
    public boolean afterInterrupt() {
        if (!isInterrupted()) {
            return false;
        }
        Thread.currentThread().interrupt();
        return true;
    }

    /**
     * Throws if applicable {@link RuntimeException} or {@link Error}
     *
     * @return
     * @throws Error
     * @throws RuntimeException
     */
    public Thrower<T> throwIfUnchecked() throws Error, RuntimeException {
        if (isRuntime()) {
            throw (RuntimeException) throwable;
        }
        if (isError()) {
            throw (Error) throwable;
        }
        return this;
    }

    /**
     * Throws if of given type. Null safe.
     *
     * @param <E>
     * @param type
     * @return
     * @throws E
     */
    public <E extends Throwable> Thrower<T> throwIf(Class<E> type) throws E {
        if (isType(type)) {
            throw (E) throwable;
        }
        return this;
    }

    /**
     * Throws if satisfy predicate. Null safe.
     *
     * @param check
     * @return
     * @throws T
     */
    public Thrower<T> throwIf(Predicate<T> check) throws T {
        if (check != null && check.test(throwable)) {
            throw throwable;
        }
        return this;
    }

    /**
     * Check if of given type. Null safe.
     *
     * @param <E>
     * @param type
     * @return
     * @throws E
     */
    public <E extends Throwable> boolean isType(Class<E> type) {
        return type != null && type.isInstance(throwable);
    }
    
    /**
     * Visitor chaining pattern. Null safe.
     * @param consumer
     * @return 
     */
    public Thrower<T> visit(Consumer<T> consumer){
        if(consumer != null){
            consumer.accept(throwable);
        }
        return this;
    }
    
    /**
     * Chain this object to produce something else.
     *
     * @param <U> result
     * @param chainFunction chaining function
     * @return
     */
    public <U> U chain(Function<Thrower<T>, ? extends U> chainFunction) {
        return Objects.requireNonNull(chainFunction, "Chain function is null").apply(this);
    }

    /**
     * @return T
     */
    public T get() {
        return throwable;
    }

    /**
     *
     * @return T wrapped inside NestedException
     */
    public NestedException toNested() {
        return NestedException.of(throwable);
    }

    /**
     *
     * @return T wrapped inside RuntimeException
     */
    public RuntimeException toRuntime() {
        return new RuntimeException(throwable);
    }

    /**
     *
     * @throws T wrapped inside RuntimeException
     */
    public RuntimeException toRuntime(String message) {
        return new RuntimeException(message, throwable);
    }

}

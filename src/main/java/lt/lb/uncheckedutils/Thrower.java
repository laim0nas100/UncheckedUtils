package lt.lb.uncheckedutils;

import java.util.Objects;

/**
 *
 * @author laim0nas100
 */
public class Thrower<T extends Throwable> {
    
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
     * Throws if applicable {@link RuntimeException} or {@link Errpr}
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
     * @throws T
     */
    public void throwSelf() throws T {
        throw throwable;
    }

    /**
     *
     * @throws T wrapped inside NestedException
     */
    public void throwSelfAsNested() throws NestedException {
        throw NestedException.of(throwable);
    }

    /**
     *
     * @throws T wrapped inside RuntimeException
     */
    public void throwSelfAsRuntime() throws RuntimeException {
        throw new RuntimeException(throwable);
    }

    /**
     *
     * @throws T wrapped inside RuntimeException
     */
    public void throwSelfAsRuntime(String message) throws RuntimeException {
        throw new RuntimeException(message, throwable);
    }
    
}

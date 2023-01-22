package lt.lb.uncheckedutils;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Objects;

/**
 *
 * Zero-cost nested exception (skips trace stack filling). Use this to mask
 * checked exceptions, without exposing the stack trace.
 *
 * @author laim0nas100
 */
public class NestedException extends RuntimeException {

    protected Throwable error;

    /**
     * Throws {@link NestedException} of given {@link Throwable}, unless it's
     * null.
     *
     * @param t
     */
    public static void nestedThrow(Throwable t) {
        if (t == null) {
            return;
        }
        throw NestedException.of(t);
    }

    /**
     *
     * @param t
     * @return Wrapped exception, unless provided {@link Throwable} already is
     * {@link NestedException}
     */
    public static NestedException of(Throwable t) {
        Objects.requireNonNull(t);
        if (t instanceof NestedException) {
            return (NestedException) t;
        } else {
            return new NestedException(t);
        }
    }

    /**
     *
     * @param t
     * @return real exception, if given {@link NestedException}.
     */
    public static Throwable unwrap(Throwable t) {
        if (t instanceof NestedException) {
            return ((NestedException) t).unwrapReal();
        } else {
            return t;
        }
    }

    /**
     * Unwraps {@link NestedException}, if such exists. Then compares exception
     * to allowed types and throws if any of the types matches.
     *
     * @param <X>
     * @param th
     * @param ex
     * @throws X
     */
    public static <X extends Throwable> void unwrappedThrowIf(Throwable th, Class<X>... ex) throws X {
        Throwable t = unwrap(th);
        for (Class<X> cls : ex) {
            if (cls.isInstance(t)) {
                throw (X) t;
            }
        }
    }

    protected NestedException(Throwable e) {
        super("Nested exception, to get real exception, call getCause", e, false, false);
        error = e;
    }

    /**
     * Does nothing.
     *
     * @return this
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Does nothing.
     */
    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return getCause().getStackTrace();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        getCause().printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        getCause().printStackTrace(s);
    }

    @Override
    public void printStackTrace() {
        getCause().printStackTrace();
    }

    @Override
    public String toString() {
        return "Nested Exception of " + getCause().toString();
    }

    @Override
    public synchronized Throwable initCause(Throwable cause) {
        error = cause;
        return this;
    }

    @Override
    public synchronized Throwable getCause() {
        return error;
    }

    public Throwable unwrapReal() {
        Throwable t = this;
        int check = 1_000_000;
        do {
            check--; // prevent cycle, if it happens somehow
            t = t.getCause();
        } while (t instanceof NestedException && check > 0);
        return t;
    }

    @Override
    public String getLocalizedMessage() {
        return getCause().getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return getCause().getMessage();
    }

}

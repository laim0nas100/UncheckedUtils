package lt.lb.uncheckedutils;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 *
 * @author laim0nas100
 */
public abstract class StacklessRuntimeException extends RuntimeException {

    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    protected StacklessRuntimeException(String message, Throwable cause) {
        super(message, cause, false, false);
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
        return EMPTY_STACK;
    }

    @Override
    public abstract void printStackTrace(PrintWriter s);

    @Override
    public abstract void printStackTrace(PrintStream s);

    @Override
    public abstract void printStackTrace();
}

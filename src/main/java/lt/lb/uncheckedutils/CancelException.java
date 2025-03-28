package lt.lb.uncheckedutils;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 *
 * @author laim0nas100
 */
public class CancelException extends StacklessRuntimeException {

    private static final PassableException NO_REASON = new PassableException("No exception provided");

    public CancelException(Throwable e) {
        super("Cancelled", e == null ? NO_REASON : e);
    }

    @Override
    public String toString() {
        return "Cancelled by exception:" + getCause().toString();
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

}

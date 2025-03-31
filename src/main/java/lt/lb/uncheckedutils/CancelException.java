package lt.lb.uncheckedutils;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 *
 * @author laim0nas100
 */
public class CancelException extends StacklessRuntimeException {

    private static final PassableException NO_REASON = new PassableException("No exception provided");

    protected final Object source;

    public CancelException(Throwable cause) {
        this(null, cause);
    }
    
    public CancelException(Object source, Throwable e) {
        super("Cancelled", e == null ? NO_REASON : e);
        this.source = source;
    }

    @Override
    public String toString() {
        return "Cancelled by exception:" + getCause().toString();
    }

    public Object getSource() {
        return source;
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

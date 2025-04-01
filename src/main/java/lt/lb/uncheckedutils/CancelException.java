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

    public CancelException(Object source, String msg) {
        this(msg, source, null);
    }

    public CancelException(Object source, Throwable e) {
        this(null, source, e);
    }

    public CancelException(String msg, Object source, Throwable e) {
        super(resolveMsg(msg, e), e == null ? NO_REASON : e);
        this.source = source;
    }

    private static String resolveMsg(String msg, Throwable cause) {
        if (msg == null) {
            if (cause == null) {
                return "Cancelled with no specific cause";
            }
            return "Cancelled by exception:" + cause.getClass().getSimpleName() + " " + cause.getMessage();
        }

        if (cause != null) {
            return msg + " due to " + cause.getMessage();
        }
        return msg;

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

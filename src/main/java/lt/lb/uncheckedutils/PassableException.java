package lt.lb.uncheckedutils;

import java.util.Objects;

/**
 *
 * Exception that does not fill in stack trace so it is cheap. It is more of a
 * result case when something (expected or not) went wrong. Not intended to be
 * thrown but can be.
 *
 * Pass a type of exception without creating it because stack trace filling is
 * major part of exception creating time. Also supports custom message and
 * optional untyped {@link Object} data.
 *
 * Intended to somewhat preserve the original exception convention without all
 * the cost. Natural addition to {@link SafeOpt} to be used with the control
 * flow, when exception is an expected case and really should be part of a
 * result.
 *
 * @author laim0nas100
 */
public class PassableException extends RuntimeException {

    protected final Class<? extends Throwable> exceptionType;
    protected final Object data;

    public PassableException(Class<? extends Throwable> cls, String message, Object data) {
        super(message);
        exceptionType = Objects.requireNonNull(cls, "Exception type must not be null");
        this.data = data;
    }

    public PassableException(Class<? extends Throwable> cls, String message) {
        this(cls, message, null);
    }

    public PassableException(String message) {
        this(defaultExceptionType(), message, null);
    }

    public PassableException(String message, Object data) {
        this(defaultExceptionType(), message, data);
    }

    public PassableException(Class<? extends Throwable> cls) {
        this(cls, defaultMessage(cls), null);
    }

    public PassableException(Class<? extends Throwable> cls, Object data) {
        this(cls, defaultMessage(cls), data);
    }

    /**
     * The default message is "Explicit error of type (className)"
     *
     * @param cls
     * @return
     */
    public static String defaultMessage(Class<? extends Throwable> cls) {
        Objects.requireNonNull(cls, "Exception type must not be null");
        return "Explicit error of type " + cls.getName();
    }
    
    /**
     * The default exception type is {@link RuntimeException}
     * @return 
     */
    public static Class<? extends Throwable> defaultExceptionType(){
        return RuntimeException.class;
    }

    /**
     * Get data passed to this {@link PassableException}.
     *
     * @return
     */
    public Object getData() {
        return data;
    }

    /**
     * Get data passed to this {@link PassableException} while performing a
     * cast.
     *
     * @return
     */
    public <T> T getDataCast() {
        return (T) data;
    }

    /**
     * Get {@link Throwable} type passed to this {@link PassableException}.
     * @return 
     */
    public Class<? extends Throwable> getType() {
        return exceptionType;
    }

    /**
     * Check if this {@link PassableException} passed type is a same or
     * superclass of any of the given parameter {@link Class} types.
     *
     * @param types
     * @return
     */
    public boolean isOfType(Class<? extends Throwable>... types) {
        for (int i = 0; i < types.length; i++) {
            Class<? extends Throwable> type = types[i];
            Objects.requireNonNull(type, "Throwable class at index:" + i + " is null");
            if (type.isAssignableFrom(getType())) {
                return true;
            }
        }
        return false;
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

}

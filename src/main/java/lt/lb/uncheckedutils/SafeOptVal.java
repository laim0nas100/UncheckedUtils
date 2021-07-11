package lt.lb.uncheckedutils;

/**
 *
 *
 * @author laim0nas100
 */
public class SafeOptVal<T> extends SafeOptBase<T> {

    /**
     * If non-null, the value; if null, indicates no value is present
     */
    protected final T val;

    /**
     * If non-null, the exception; if null, indicates no exception is present
     */
    protected final Throwable threw;

    protected SafeOptVal(T value, Throwable throwable) {
        if (value != null && throwable != null) {
            throw new IllegalArgumentException("rawValue AND rawException cannot both be present");
        }
        val = value;
        threw = throwable;
    }

    protected final static SafeOptVal empty = new SafeOptVal(null, null);

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException cannot both be present");
        } else if (rawValue == null && rawException == null) {
            return empty;
        }
        return new SafeOptVal<>(rawValue, rawException);
    }

    @Override
    public T rawValue() {
        return val;
    }

    @Override
    public Throwable rawException() {
        return threw;
    }

}

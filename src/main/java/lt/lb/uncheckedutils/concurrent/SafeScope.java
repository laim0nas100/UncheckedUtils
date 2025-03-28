package lt.lb.uncheckedutils.concurrent;

import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public class SafeScope {

    public Submitter submitter;

    public CancelPolicy cp;

    public <T> SafeOptAsync<T> of(T value) {
        return SafeOptAsync.ofNullable(submitter, value, cp);
    }

    public boolean isCancelled() {
        return cp == null ? false : cp.cancelled();
    }

}

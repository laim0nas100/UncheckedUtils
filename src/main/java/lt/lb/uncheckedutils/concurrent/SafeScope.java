package lt.lb.uncheckedutils.concurrent;

import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public class SafeScope {

    public Submitter submitter = Submitter.DEFAULT_POOL;

    public CancelPolicy cp;

    public <T> ScopedSafeOptAsync<T> of(T value) {
        return new ScopedSafeOptAsync<>(this, value);
    }

    public boolean isCancelled() {
        return cp == null ? false : cp.cancelled();
    }

    public void cancel(Throwable err) {
        if (cp == null) {
            return;
        }
        cp.cancel(err);
    }

    public static class ScopedSafeOptAsync<T> extends SafeOptAsync<T> {

        public final SafeScope scope;

        public ScopedSafeOptAsync(SafeScope scope, T base) {
            super(new CompletedFuture<>(SafeOpt.ofNullable(base)), scope.submitter, true, scope.cp);
            this.scope = scope;
        }

    }

}

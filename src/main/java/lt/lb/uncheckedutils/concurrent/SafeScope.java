package lt.lb.uncheckedutils.concurrent;

import java.util.concurrent.Future;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public class SafeScope {

    public Submitter submitter;

    public CancelPolicy cp;

    public <T> SafeOpt<T> of(T value) {
        if(value == null){
            return SafeOpt.empty();
        }
        return new SafeOptAsync<>(submitter, new CompletedFuture<>(SafeOpt.of(value)), true,new SafeOptAsync.AsyncWork(cp));
    }

    public boolean isCancelled() {
        return cp == null ? false : cp.cancelled();
    }

}

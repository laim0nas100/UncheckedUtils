package lt.lb.uncheckedutils.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import lt.lb.uncheckedutils.CancelException;
import lt.lb.uncheckedutils.PassableException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
public class CancelPolicy {
    
    private final AtomicReference<Throwable> state = new AtomicReference<>();
    private SafeOpt cancelOpt;
    
    public boolean cancelOnError = false;

    public void cancel(Throwable error) {
        if(error == null){
            error = new PassableException("Cancelled explicitly");
        }
        if(state.compareAndSet(null, error)){
            cancelOpt = SafeOpt.error(new CancelException(error));
        }
    }
    
    public boolean cancelled(){
        return state.get() != null;
    }
    
    public SafeOpt getError(){
        return cancelOpt;
    }
    
    
}

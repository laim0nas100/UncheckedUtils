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
    
    public final boolean cancelOnError;
    public final boolean interruptableAwait;
    private final ThreadParkSpace parkedThreads;

    public CancelPolicy(boolean cancelOnError, boolean interruptableAwait, int expectedThreads) {
        this(cancelOnError,interruptableAwait, new ThreadParkSpace(expectedThreads));
    }
    
    public CancelPolicy(boolean cancelOnError, boolean interruptableAwait, ThreadParkSpace parkedThreads) {
        this.cancelOnError = cancelOnError;
        this.interruptableAwait = interruptableAwait;
        this.parkedThreads = parkedThreads;
    }

    public CancelPolicy() {
        this(false,false,null);
    }
    
    
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
    
    public int parkIfSupported(){
        if(parkedThreads == null){
            return -1;
        }
        return parkedThreads.park();
    }
    
    public boolean unparkIfSupported(){
        if(parkedThreads == null){
            return false;
        }
        return parkedThreads.unpark();
    }
    
    public void interruptParkedThreads(){
        for(Thread thread:parkedThreads.getThreads()){
            thread.interrupt();
        }
    }
    
    
}

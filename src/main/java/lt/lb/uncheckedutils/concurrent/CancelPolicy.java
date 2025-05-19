package lt.lb.uncheckedutils.concurrent;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import lt.lb.uncheckedutils.CancelException;
import static lt.lb.uncheckedutils.concurrent.SafeOptAsync.DEBUG;
import lt.lb.uncheckedutils.PassableException;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
public class CancelPolicy {

    public static final PassableException ERR_DEPENDENCY_COMPLETION = new PassableException("Dependency completion");
    public static final PassableException ERR_DEPENDENCY_ERROR = new PassableException("Cancelled due to error in dependency");
    public static final PassableException ERR_CANCEL_EXPLICIT = new PassableException("Cancelled explicitly");

    private final CancelPolicy parent;
    private final Collection<CancelPolicy> children = new ConcurrentLinkedDeque<>();

    private final AtomicReference<Throwable> state = new AtomicReference<>();
    private final AtomicReference<SafeOpt> cancelledSource = new AtomicReference<>();
    private SafeOpt cancelOpt;

    public final boolean cancelOnError;
    public final boolean interruptableAwait;
    public final boolean passError;
    private final ThreadLocalParkSpace<Boolean> parkedThreads;

    public CancelPolicy() {
        this(true, true, true);
    }

    public CancelPolicy(boolean cancelOnError, boolean interruptableAwait, boolean passError) {
        this(null, cancelOnError, interruptableAwait, passError, new ThreadLocalParkSpace());
    }

    public CancelPolicy(CancelPolicy parent, boolean cancelOnError, boolean interruptableAwait, boolean passError) {
        this(parent, cancelOnError, interruptableAwait, passError, new ThreadLocalParkSpace());
    }

    public CancelPolicy(CancelPolicy parent, boolean cancelOnError, boolean interruptableAwait, boolean passError, ThreadLocalParkSpace parkedThreads) {
        this.parent = parent;
        this.cancelOnError = cancelOnError;
        this.interruptableAwait = interruptableAwait;
        this.passError = passError;
        this.parkedThreads = parkedThreads;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    public static CancelPolicy fromParent(CancelPolicy parent) {
        if (parent == null) {
            return null;
        }
        return new CancelPolicy(parent, parent.cancelOnError, parent.interruptableAwait, parent.passError);
    }

    public void cancel(Throwable error) {
        cancel(null, error);
    }

    public void cancel(SafeOpt source, Throwable error) {
        if (error == null) {
            error = ERR_CANCEL_EXPLICIT;
        }
        if (!passError) {
            error = ERR_DEPENDENCY_ERROR;
        }
        if (state.compareAndSet(null, error)) {

            if (source != null) {
                cancelledSource.compareAndSet(null, source);
            }
            SafeOpt csource = cancelledSource.get();
            cancelOpt = SafeOpt.error(new CancelException(csource, error));
            for (CancelPolicy child : children) {
                if (child == null) {
                    continue;
                }
                child.cancel(csource, error);
            }
            if (interruptableAwait) {
                interruptParkedThreads();
            }
        }
    }

    public void cancelOnCompletion(SafeOpt source) {
        if (state.compareAndSet(null, ERR_DEPENDENCY_COMPLETION)) {

            if (source != null) {
                cancelledSource.compareAndSet(null, source);
            }
            cancelOpt = SafeOpt.error(new CancelException(source, "Dependency completion"));
            for (CancelPolicy child : children) {
                if (child == null) {
                    continue;
                }
                child.cancelOnCompletion(source);
            }
            if (interruptableAwait) {
                interruptParkedThreads();
            }
        }
    }

    public boolean cancelled() {
        if (parent != null) {
            if (parent.cancelled()) {
                return true;
            }
        }
        return state.get() != null;
    }

    public SafeOpt getError() {
        if (parent != null) {
            SafeOpt error = parent.getError();
            if (error != null) {
                return error;
            }
        }
        return cancelOpt;
    }

    public SafeOpt getErrorSource() {
        if (parent != null) {
            SafeOpt error = parent.getErrorSource();
            if (error != null) {
                return error;
            }
        }
        return cancelledSource.get();
    }

    public int parkIfSupported() {
        if (parkedThreads == null) {
            return -1;
        }
        ///dummy item park
        int park = parkedThreads.park(Boolean.TRUE);
        return park;
    }

    public boolean unparkIfSupported(int idx) {
        if (parkedThreads == null) {
            return false;
        }
        return parkedThreads.unpark(idx);
    }

    public void interruptParkedThreads() {
        if (parkedThreads == null) {
            return;
        }
        if (DEBUG) {
            System.out.println("Interrupt live threads");
        }
        for (Thread thread : parkedThreads.getAliveThreads()) {
            thread.interrupt();
            if (DEBUG) {
                System.out.println("Interrupt" + thread.getName() + thread.getId());
            }
        }
    }

}

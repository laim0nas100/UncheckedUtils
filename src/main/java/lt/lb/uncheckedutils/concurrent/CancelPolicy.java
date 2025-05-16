package lt.lb.uncheckedutils.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import lt.lb.uncheckedutils.CancelException;
import lt.lb.uncheckedutils.Checked;
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
        this(cancelOnError, interruptableAwait, passError, new ThreadLocalParkSpace(Checked.REASONABLE_PARALLELISM));
    }

    public CancelPolicy(boolean cancelOnError, boolean interruptableAwait, boolean passError, ThreadLocalParkSpace parkedThreads) {
        this.cancelOnError = cancelOnError;
        this.interruptableAwait = interruptableAwait;
        this.passError = passError;
        this.parkedThreads = parkedThreads;
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
            cancelOpt = SafeOpt.error(new CancelException(cancelledSource.get(), error));
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
            if (interruptableAwait) {
                interruptParkedThreads();
            }
        }
    }

    public boolean cancelled() {
        return state.get() != null;
    }

    public SafeOpt getError() {
        return cancelOpt;
    }

    public SafeOpt getErrorSource() {
        return cancelledSource.get();
    }

    public int parkIfSupported() {
        if (parkedThreads == null) {
            return -1;
        }
        ///dummy item park
        return parkedThreads.park(Boolean.TRUE);
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
        for (Thread thread : parkedThreads.getAliveThreads()) {
            thread.interrupt();
        }
    }

}

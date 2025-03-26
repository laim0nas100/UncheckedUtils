package lt.lb.uncheckedutils.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author laim0nas100
 */
public class AtomicLock {

    private final AtomicInteger lock = new AtomicInteger(0); // negtive readers, positive writers

    public boolean tryRead() { // allow any readers if not being written
        return lock.accumulateAndGet(0, (current, discard) -> {
            return current <= 0 ? current - 1 : current;
        }) < 0;
    }

    public boolean tryWrite() {// allow only 1 writer
        return lock.accumulateAndGet(0, (current, discard) -> {
            return current == 0 ? current + 1 : current;
        }) == 1;
    }

    public void releaseRead() {
        lock.accumulateAndGet(0, (current, discard) -> {
            return current < 0 ? current + 1 : current;
        });
    }

    public void releaseWrite() {
        lock.accumulateAndGet(0, (current, discard) -> {
            return current > 0 ? current - 1 : current;
        });
    }

}

package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author laim0nas100
 */
public class ThreadParkSpace {

    private ThreadLocal<Integer> reservedIndex = ThreadLocal.withInitial(() -> -1);

    private final AtomicInteger slidingIndex;

    private final Thread[] threads;
    private final int size;

    public ThreadParkSpace(int maxThreads) {
        size = maxThreads;
        threads = new Thread[size];
        slidingIndex = new AtomicInteger(size);
    }

    public int park() {
        int index = reservedIndex.get();
        if (index < 0) { // new thread
            index = slidingIndex.accumulateAndGet(0, (current, discard) -> {
                return (current + 1) % size;
            });
            reservedIndex.set(index);
        }
        if (threads[index] == null) {
            threads[index] = Thread.currentThread();
        } else {
            reservedIndex.set(-1);
            return -1;
        }

        return index;
    }

    public boolean unpark() {
        int index = reservedIndex.get();
        if (index < 0) {
            return false;
        }
        if (threads[index] == null) {
            return false;
        }
        threads[index] = null;
        return true;
    }

    public List<Thread> getThreads() {
        ArrayList<Thread> arrayList = new ArrayList<>();
        for (Thread thread : threads) {
            if (thread != null) {
                arrayList.add(thread);
            }
        }
        return arrayList;
    }

}

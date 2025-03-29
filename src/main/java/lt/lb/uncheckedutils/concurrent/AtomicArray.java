package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author laim0nas100
 */
public class AtomicArray<T> {

    public static class AtomicArrayEntry<T> {

        private final AtomicLock lock = new AtomicLock();
        private volatile T value;

        public AtomicArrayEntry() {
        }

        public AtomicArrayEntry(T value) {
            this.value = value;
        }
    }

    protected final List<AtomicArrayEntry> array;
    protected final AtomicInteger lastWrite = new AtomicInteger(-1);
    public final CancelPolicy cp;

    public AtomicArray(int size) {
        this(size,null);
    }
    
    public AtomicArray(int size, CancelPolicy cancelledRef) {
        array = new CopyOnWriteArrayList<>();
        cp = cancelledRef;
    }

    public void add(T value) {
        array.add(new AtomicArrayEntry<>(value));
    }

    public T read(final int index) {
        return read(index, c -> {
        });
    }

    public T read(final int index, Consumer< ? super T> func) {
        while (true) {
            AtomicArrayEntry<T> entry = array.get(index);
            if (entry.lock.tryRead()) {
                try {
                    func.accept(entry.value);
                    return entry.value;
                } finally {
                    entry.lock.releaseRead();
                }
            }
        }

    }

    public T write(final int index, Function<? super T, ? extends T> func) {
        while (true) {
            AtomicArrayEntry<T> entry = array.get(index);
            if (entry.lock.tryWrite()) {
                try {
                    T apply = func.apply(entry.value);
                    entry.value = apply;

                    lastWrite.accumulateAndGet(index, (curr, i) -> {
                        return Math.max(curr, i);
                    });
                    return apply;
                } finally {
                    entry.lock.releaseWrite();
                }
            }
        }

    }
        
    public boolean isCancelled() {
        return cp == null ? false : cp.cancelled();
    }

    public int getLastWriteIndex() {
        return lastWrite.get();
    }

    public int size() {
        return array.size();
    }

}

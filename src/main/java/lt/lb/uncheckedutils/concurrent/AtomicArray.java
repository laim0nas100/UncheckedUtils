package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayList;
import java.util.List;
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
    

    private List<AtomicArrayEntry> array;
    private AtomicInteger lastWrite = new AtomicInteger(-1);
    private AtomicInteger adds = new AtomicInteger(0);

    public AtomicArray(int size){
        array = new ArrayList<>(size);
    }

    public void add(T value) {
        array.add(new AtomicArrayEntry<>(value));
        adds.incrementAndGet();
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

    public int getLastWriteIndex() {
        return lastWrite.get();
    }

    public int size() {
        return adds.get();
    }

}

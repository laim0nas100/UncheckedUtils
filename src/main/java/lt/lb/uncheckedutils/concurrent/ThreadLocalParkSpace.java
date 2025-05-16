package lt.lb.uncheckedutils.concurrent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author laim0nas100
 */
public class ThreadLocalParkSpace<T> implements Iterable<T> {

    private ThreadLocal<Integer> reservedIndex = ThreadLocal.withInitial(() -> -1);

    private final AtomicInteger slidingIndex = new AtomicInteger(-1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);

    private static class Item {

        private AtomicReference<Thread> thread;
        private Object item;

        public Item() {
            thread = new AtomicReference(null);
        }

        public Item(Thread thread, Object val) {
            this.thread = new AtomicReference(thread);
            this.item = val;
        }

        private boolean isAlive() {
            return thread.get() != null && thread.get().isAlive();
        }
    }

    private Item[] items;
    private volatile int writes = 0;

    public ThreadLocalParkSpace() {
        this(8);
    }

    public ThreadLocalParkSpace(int initialSize) {
        initialSize = Math.min(8, initialSize);
        items = new Item[initialSize];
        for (int i = 0; i < initialSize; i++) {
            items[i] = new Item();
        }
    }

    protected int tryParkReplace(int index, T item) {
        int size = items.length;
        int tries = size;
        while (tries >= 0) {

            index = slidingIndex.accumulateAndGet(0, (current, discard) -> {
                return (current + 1) % size;
            });
            Item container = items[index];
            if (container.thread.compareAndSet(null, Thread.currentThread())) {
                container.item = item;
                reservedIndex.set(index);
                return index;
            } else { // not null
                Thread refThread = container.thread.get();
                if (!refThread.isAlive() && container.thread.compareAndSet(refThread, Thread.currentThread())) {//replaced
                    container.item = item;
                    reservedIndex.set(index);
                    return index;
                }
                tries--;

            }
        }
        return index;
    }

    public int park(T item) {
        Objects.requireNonNull(item);
        int index = reservedIndex.get();
        if (index >= 0) {
            if (park(index, item)) {
                return index;
            }
        }
        for (;;) {

            int prevWrites = 0;
            try {
                lock.readLock().lock();
                prevWrites = writes;
                index = tryParkReplace(index, item);
            } finally {
                lock.readLock().unlock();
            }

            if (index >= 0) {
                return index;
            }

            try {
                // grow
                lock.writeLock().lock();
                if (prevWrites == writes) { // other thread maybe grew array while we were waiting for lock, try again
                    int oldSize = items.length;
                    int newSize = (int) Math.round(oldSize * 1.5);
                    int actualNewSize = oldSize + Math.min(16, newSize - oldSize); // conservative grow
                    items = Arrays.copyOf(items, actualNewSize);
                    items[oldSize] = new Item(Thread.currentThread(), item);
                    for (int i = oldSize + 1; i < actualNewSize; i++) {
                        items[i] = new Item();
                    }
                    writes++;
                    return oldSize;
                }

            } finally {
                lock.writeLock().unlock();
            }
        }

    }

    public boolean park(int index, T item) {
        Objects.requireNonNull(item);
        lock.readLock().lock();
        try {
            Item cont = items[index];
            if (cont.thread.get() == Thread.currentThread()) {
                cont.item = item;
                return true;
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }

    }

    public boolean unpark(int index) {
        if (index < 0) {
            if ((index = reservedIndex.get()) < 0) {
                return false;
            }
        }
        if (items[index].thread.compareAndSet(Thread.currentThread(), null)) {
            return true;
        }
        return false;
    }

    private Stream<Item> getAliveContainers() {
        return Stream.of(items).filter(f -> f.isAlive());
    }

    public List<T> getPlacedItems() {
        lock.readLock().lock();
        try {
            return getAliveContainers().map(m -> (T) m.item).collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }

    }

    public List<Thread> getAliveThreads() {
        lock.readLock().lock();
        try {
            return getAliveContainers().map(m -> m.thread.get()).collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }

    }

    public Map<Thread, T> getAliveThreadItems() {
        lock.readLock().lock();
        try {
            return getAliveContainers().collect(Collectors.toMap(t -> t.thread.get(), t -> (T) t.item));
        } finally {
            lock.readLock().unlock();
        }

    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            int i = -1;

            @Override
            public boolean hasNext() {
                return i + 1 < items.length;
            }

            @Override
            public T next() {
                Item container = items[++i];
                return (T) container.item;
            }
        };
    }

}

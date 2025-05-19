package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayDeque;
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
import lt.lb.uncheckedutils.Checked;
import static lt.lb.uncheckedutils.concurrent.SafeOptAsync.DEBUG;
import static lt.lb.uncheckedutils.concurrent.SafeOptAsync.thread;

/**
 *
 * @author laim0nas100
 */
public class ThreadLocalParkSpace<T> implements Iterable<T> {

    private ThreadLocal<SpaceInfo> reserved = ThreadLocal.withInitial(() -> new SpaceInfo());

    private final AtomicInteger slidingIndex = new AtomicInteger(-1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);

    private static class SpaceInfo {

        private int index = -1;
        private int count = 0;
    }

    private static class Item {

        private final AtomicReference<Thread> thread;
        private volatile Object item;

        public Item() {
            thread = new AtomicReference(null);
        }

        public Item(Thread thread, Object val) {
            this.thread = new AtomicReference(thread);
            this.item = val;
        }

        private boolean isAlive() {
            Thread t = thread.get();
            return t != null && t.isAlive();
        }
    }

    private static class Volatile {

        public Item[] items;

        public int size() {
            return items.length;
        }
    }

    // make array volatile by pointer indirection
    private volatile Volatile array = new Volatile();

    public ThreadLocalParkSpace() {
        this(Checked.REASONABLE_PARALLELISM);
    }

    public ThreadLocalParkSpace(int initialSize) {
        initialSize = Math.max(Checked.REASONABLE_PARALLELISM, initialSize);
        if (initialSize % 2 != 0) {
            initialSize++;
        }
        array.items = new Item[initialSize];
        for (int i = 0; i < initialSize; i++) {
            array.items[i] = new Item();
        }
    }

    protected int tryParkReplace(int index, T item) {
        final int size = array.size();
        int tries = size;
        Thread currentThread = Thread.currentThread();
        index = slidingIndex.accumulateAndGet(0, (current, discard) -> {
            return (current + 1) % size;
        });
        while (tries >= 0) {
            tries--;

            Item container = array.items[index];

            Thread refThread = container.thread.get();
            if (refThread != currentThread && !container.isAlive() && container.thread.compareAndSet(refThread, currentThread)) {//replaced
                if (DEBUG) {
                    if (refThread == null) {
                        System.out.println("Parked new:" + index + thread());
                    } else {
                        System.out.println("Replaced park:" + index + thread());
                    }
                }
                container.item = item;
                SpaceInfo space = reserved.get();
                space.index = index;
                space.count = 1;
                return index;
            }
             index = (index + 1) % size;

        }
        return -1;
    }

    public int park(T item) {
        Objects.requireNonNull(item);
        if (reserved.get().count != 0) {//repeated park
            if (DEBUG) {
                System.out.println("Repeated park " + thread());
            }
            return -1;
        }
        int index = reserved.get().index;
        if (index >= 0) {
            if (park(index, item)) {
                return index;
            }
        }
        for (;;) {

            int prevLength = -1;
            try {
                lock.readLock().lock();
                prevLength = array.size();
                index = tryParkReplace(index, item);
            } finally {
                lock.readLock().unlock();
            }

            if (index >= 0) {
                return index;
            }

            try {
                // grow
                if (DEBUG) {
                    System.out.println("Try Grow ThreadSpace " + thread());
                }
                lock.writeLock().lock();
                int size = array.size();
                if (prevLength == size) { // other thread maybe grew array while we were waiting for lock, try again

                    int newSize = (int) Math.round(size * 2);
                    int actualNewSize = size + Math.min(1024, newSize - size); // conservative grow after a big array
                    if (DEBUG) {
                        System.out.println("Grow ThreadSpace to " + actualNewSize + " " + thread());
                    }
                    array.items = Arrays.copyOf(array.items, actualNewSize);
                    array.items[size] = new Item(Thread.currentThread(), item);
                    SpaceInfo space = reserved.get();
                    space.count = 1;
                    space.index = size;
                    for (int i = size + 1; i < actualNewSize; i++) {
                        array.items[i] = new Item();
                    }
                    slidingIndex.set(size-1);
                    return size;
                } else {
                    if (DEBUG) {
                        System.out.println("Failed to grow ThreadSpace " + prevLength + " != " + size + " " + thread());
                    }
                }

            } finally {
                lock.writeLock().unlock();
            }
        }

    }

    public boolean park(int index, T item) {
        Objects.requireNonNull(item);

        try {
            lock.readLock().lock();
            Item container = array.items[index];
            Thread currentThread = Thread.currentThread();
            if (container.thread.compareAndSet(currentThread, currentThread)) {
                if (DEBUG) {
                    System.out.println("repeated park:" + thread());
                }
                return false;
            } else if (container.thread.compareAndSet(null, currentThread)) {
                if (DEBUG) {
                    System.out.println("re-Parked:" + thread());
                }
                container.item = item;
                SpaceInfo space = reserved.get();

                space.count = 1;
                space.index = index;
                return true;
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }

    }

    public boolean unpark(int index) {
        SpaceInfo space = reserved.get();
        if (index < 0) {
            if ((index = space.index) < 0) {
                return false;
            }
        }
        Thread t = Thread.currentThread();
        Item container = array.items[index];
        if (space.count == 1 && container.thread.compareAndSet(t, null)) {
            container.item = null;
            space.count = 0;
            if (DEBUG) {
                System.out.println("unparked completely:" + thread());
            }
            return true;
        }
        return false;
    }

    private Stream<Item> getAliveContainers() {
        return Stream.of(array.items).filter(f -> f.isAlive());
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
                return i + 1 < array.items.length;
            }

            @Override
            public T next() {
                Item container = array.items[++i];
                return (T) container.item;
            }
        };
    }

}

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
        private final ArrayDeque item = new ArrayDeque<>(2);

        public Item() {
            thread = new AtomicReference(null);
        }

        public Item(Thread thread, Object val) {
            this.thread = new AtomicReference(thread);
            this.item.add(val);
        }

        private boolean isAlive() {
            Thread t = thread.get();
            return t != null && t.isAlive();
        }
    }

    private static class Volatile {

        public Item[] items;
    }

    // make array volatile by pointer indirection
    private volatile Volatile array = new Volatile();

    public ThreadLocalParkSpace() {
        this(Checked.REASONABLE_PARALLELISM);
    }

    public ThreadLocalParkSpace(int initialSize) {
        initialSize = Math.max(Checked.REASONABLE_PARALLELISM, initialSize);
        array.items = new Item[initialSize];
        for (int i = 0; i < initialSize; i++) {
            array.items[i] = new Item();
        }
    }

    protected int tryParkReplace(int index, T item) {
        int size = array.items.length;
        int tries = size;
        Thread currentThread = Thread.currentThread();
        while (tries >= 0) {
            tries--;
            index = slidingIndex.accumulateAndGet(0, (current, discard) -> {
                return (current + 1) % size;
            });
            Item container = array.items[index];

            Thread refThread = container.thread.get();
            if (refThread == null && container.thread.compareAndSet(refThread, currentThread)) {
                container.item.clear();
                container.item.add(item);
                SpaceInfo space = reserved.get();
                space.index = index;
                space.count = 1;
                if (DEBUG) {
                    System.out.println("Parked new:" + thread());
                }
                return index;
            } else { // not null

                if (refThread != currentThread && !container.isAlive() && container.thread.compareAndSet(refThread, currentThread)) {//replaced
                    if (DEBUG) {
                        System.out.println("Replaced park:" + thread());
                    }
                    container.item.clear();
                    container.item.add(item);
                    SpaceInfo space = reserved.get();
                    space.index = index;
                    space.count = container.item.size();
                    return index;
                }

            }
        }
        return -1;
    }

    public int park(T item) {
        Objects.requireNonNull(item);
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
                prevLength = array.items.length;
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
                    System.out.println("Try Grow ThreadSpace");
                }
                lock.writeLock().lock();
                int size = array.items.length;
                if (prevLength == size) { // other thread maybe grew array while we were waiting for lock, try again

                    int newSize = (int) Math.round(size * 1.5);
                    int actualNewSize = size + Math.min(32, newSize - size); // conservative grow
                    if (DEBUG) {
                        System.out.println("Grow ThreadSpace to " + actualNewSize);
                    }
                    array.items = Arrays.copyOf(array.items, actualNewSize);
                    array.items[size] = new Item(Thread.currentThread(), item);
                    for (int i = size + 1; i < actualNewSize; i++) {
                        array.items[i] = new Item();
                    }
                    return size;
                } else {
                    if (DEBUG) {
                        System.out.println("Failed to grow ThreadSpace " + prevLength + " != " + size);
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
                    System.out.println("re-Parked:" + thread());
                }
                container.item.add(item);
                reserved.get().count = container.item.size();
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
            container.item.clear();
            space.count = 0;
            if (DEBUG) {
                System.out.println("unparked completely:" + thread());
            }
            return true;
        } else if (container.thread.compareAndSet(t, t)) {
            if (DEBUG) {
                System.out.println("unparked once:" + thread());
            }
            container.item.pollLast();
            space.count--;
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
                return (T) container.item.peekLast();
            }
        };
    }

}

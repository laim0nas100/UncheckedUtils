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
import lt.lb.uncheckedutils.Checked;
import static lt.lb.uncheckedutils.concurrent.SafeOptAsync.DEBUG;

/**
 *
 * @author laim0nas100
 */
public class ThreadLocalParkSpace<T> implements Iterable<T> {

    /**
     * for debug
     *
     * @return
     */
    static String thread() {
        Thread t = Thread.currentThread();
        return t.getName() + " " + t.getId();
    }

    private ThreadLocal<SpaceInfo> reserved = ThreadLocal.withInitial(() -> new SpaceInfo());

    private final AtomicInteger slidingIndex = new AtomicInteger(-1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final boolean nesting;

    private static class SpaceInfo {

        private int index = -1;
        private int count = 0;
    }

    private static class Item<T> {

        private final AtomicReference<Thread> thread;
        private volatile T item;

        public Item() {
            thread = new AtomicReference(null);
        }

        public Item(Thread thread, T val) {
            this.thread = new AtomicReference(thread);
            this.item = val;
        }

        private boolean isAlive() {
            Thread t = thread.get();
            return t != null && t.isAlive();
        }
    }

    private static class Volatile<T> {

        public Item<T>[] items;

        public int size() {
            return items.length;
        }
    }

    // make array volatile by pointer indirection
    private volatile Volatile<T> array = new Volatile<>();

    public ThreadLocalParkSpace() {
        this(Checked.REASONABLE_PARALLELISM);
    }

    public ThreadLocalParkSpace(int initialSize) {
        this(initialSize, true);
    }

    public ThreadLocalParkSpace(int initialSize, boolean nesting) {
        this.nesting = nesting;
        initialSize = Math.max(Checked.REASONABLE_PARALLELISM, initialSize);
        if (initialSize % 2 != 0) {
            initialSize++;
        }
        array.items = new Item[initialSize];
        for (int i = 0; i < initialSize; i++) {
            array.items[i] = new Item<>();
        }
    }

    protected int findParkReplace(T item) {
        final int size = array.size();
        int tries = size;
        Thread currentThread = Thread.currentThread();
        int index = slidingIndex.accumulateAndGet(0, (current, discard) -> {
            return (current + 1) % size;
        });
        while (tries >= 0) {
            tries--;

            Item<T> container = array.items[index];

            Thread refThread = container.thread.get();
            if (refThread != currentThread && !container.isAlive() && container.thread.compareAndSet(refThread, currentThread)) {//replaced
                if (DEBUG) {
                    if (refThread == null) {
                        System.out.println(thread() + " Parked new:" + index);
                    } else {
                        System.out.println(thread() + " Replaced park:" + index);
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
        SpaceInfo info = reserved.get();
        if (info.count > 0) {//repeated park
            if (nesting) {
                int c = ++info.count;
                if (DEBUG) {
                    System.out.println(thread() + " Repeated park allowed " + c);
                }
                return info.index;
            } else {
                if (DEBUG) {
                    System.out.println(thread() + " Repeated park rejected");
                }
                return -1;
            }
        }

        int index = info.index;
        if (index >= 0) {
            if (park(index, item)) {
                return index;
            }
        }
        for (;;) {
            //index is -1
            int prevLength = -1;
            try {
                lock.readLock().lock();
                prevLength = array.size();
                index = findParkReplace(item);
            } finally {
                lock.readLock().unlock();
            }

            if (index >= 0) {
                return index;
            }

            try {
                // grow
                if (DEBUG) {
                    System.out.println(thread() + " Try Grow ThreadSpace");
                }
                lock.writeLock().lock();
                int size = array.size();
                if (prevLength == size) { // other thread maybe grew array while we were waiting for lock, try again

                    int newSize = (int) Math.round(size * 2);
                    int actualNewSize = size + Math.min(1024, newSize - size); // conservative grow after a big array
                    if (DEBUG) {
                        System.out.println(thread() + " Grow ThreadSpace to " + actualNewSize);
                    }
                    array.items = Arrays.copyOf(array.items, actualNewSize);
                    array.items[size] = new Item(Thread.currentThread(), item);
                    SpaceInfo space = reserved.get();
                    space.count = 1;
                    space.index = size;
                    for (int i = size + 1; i < actualNewSize; i++) {
                        array.items[i] = new Item();
                    }
                    slidingIndex.set(size);
                    return size;
                } else {
                    if (DEBUG) {
                        System.out.println(thread() + " Failed to grow ThreadSpace " + prevLength + " != " + size);
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
                if (nesting) {
                    SpaceInfo info = reserved.get();
                    if (info.index == index) {
                        int c = ++info.count;
                        if (DEBUG) {
                            System.out.println(thread() + " repeated park allowd " + c);
                        }
                    }
                    return true;
                } else {
                    if (DEBUG) {
                        System.out.println(thread() + " repeated park rejected");
                    }
                    return false;
                }

            } else if (container.thread.compareAndSet(null, currentThread)) {
                if (DEBUG) {
                    System.out.println(thread() + " re-Parked");
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
                System.out.println(thread() + " unparked completely");
            }
            return true;
        } else if (nesting && space.count > 1 && container.thread.compareAndSet(t, t)) {
            int c = --space.count;
            if (DEBUG) {
                System.out.println(thread() + " unparked partialy " + c);
            }
            return true;
        }

        return false;
    }

    private Stream<Item<T>> getAliveContainers() {
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
            return getAliveContainers().collect(Collectors.toMap(t -> t.thread.get(), t -> t.item));
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
                Item<T> container = array.items[++i];
                return container.item;
            }
        };
    }

}

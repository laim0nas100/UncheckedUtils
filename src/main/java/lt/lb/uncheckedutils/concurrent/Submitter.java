package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import lt.lb.uncheckedutils.Checked;
import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public abstract class Submitter {

    /**
     * Nesting occurs when SafeOpt Async is created within SafeOpt Async, for
     * example <br>     {@link SafeOptAsync#flatMap(lt.lb.uncheckedutils.func.UncheckedFunction) }
     * During nesting, the parent thread must await the child thread in other to
     * proceed, so it is blocked, or nesting does not happen (work continues on
     * the same parent thread)
     */
    public static final int NESTING_LIMIT = Math.max(3, (int) (Checked.REASONABLE_PARALLELISM / Math.E) + 1);

    public abstract boolean continueInPlace(SafeOptAsync.AsyncWork task);

    public abstract void submit(SafeOptAsync.AsyncWork task);

    public boolean limited() {
        return false;
    }

    private static boolean insideCheck(ArrayDeque<SafeOptAsync.AsyncWork> stack, int nesting, SafeOptAsync.AsyncWork task) {
        if (stack == null) {
            return false;
        }
        int size = stack.size();
        return size > 0 && (size + 1 > nesting || stack.contains(task));
    }

    public static Submitter ofUnlimitedParallelism(final ExecutorService service, final int nesting) {
        Objects.requireNonNull(service);
        if (nesting < 0) {
            throw new IllegalArgumentException("Negative nesting");
        }
        return new Submitter() {

            private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = new ThreadLocal<>();

            @Override
            public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
                return insideCheck(inside.get(), nesting, task);
            }

            @Override
            public void submit(final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);
                ArrayDeque<SafeOptAsync.AsyncWork> current = inside.get();

                if (insideCheck(current, nesting, task)) {
                    try {
                        current.addLast(task);
                        task.run();
                    } finally {
                        current.removeLastOccurrence(task);
                    }
                    return;
                }
                startThread(current, task);

            }

            public void startThread(ArrayDeque<SafeOptAsync.AsyncWork> current, SafeOptAsync.AsyncWork task) {

                ArrayDeque<SafeOptAsync.AsyncWork> stack = current == null ? new ArrayDeque<>() : new ArrayDeque<>(current);
                stack.add(task);

                service.submit(() -> {

                    ArrayDeque<SafeOptAsync.AsyncWork> local = inside.get();
                    if (local == null) {
                        local = stack;
                        inside.set(local);
                    } else {
                        local.addAll(stack);
                    }
                    try {

                        task.run();

                    } finally {
                        Iterator<SafeOptAsync.AsyncWork> descendingIterator = stack.descendingIterator();
                        while (descendingIterator.hasNext()) {
                            local.removeLastOccurrence(descendingIterator.next());
                        }
                    }

                });
            }
        };
    }

    /**
     *
     * @param service the work-horse and thread spawner
     * @param parallelism how many threads to be expected and limit new ones to
     * prevent deadlocks (0 means in-place execution)
     * @param nesting how much nesting can there be (0 means in-place execution)
     * @return
     */
    public static Submitter ofLimitedParallelism(final ExecutorService service, final int parallelism, final int nesting) {
        Objects.requireNonNull(service);
        if (parallelism < 0) {
            throw new IllegalArgumentException("Negative parallelism");
        }
        if (nesting < 0) {
            throw new IllegalArgumentException("Negative nesting");
        }
        return new Submitter() {

            private final AtomicInteger freeThreads = new AtomicInteger(parallelism);
            private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> new ArrayDeque<>(nesting));

            @Override
            public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
                return (insideCheck(inside.get(), nesting, task) || freeThreads.get() <= 0);
            }

            @Override
            public boolean limited() {
                return true;
            }

            @Override
            public void submit(final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);

                ArrayDeque<SafeOptAsync.AsyncWork> current = inside.get();

                if (insideCheck(current, nesting, task) || freeThreads.get() <= 0) {
                    try {

                        current.addLast(task);
                        task.run();

                    } finally {
                        current.removeLastOccurrence(task);
                    }
                    return;
                }
                if (freeThreads.decrementAndGet() >= 0) {
                    ArrayDeque<SafeOptAsync.AsyncWork> inherited = new ArrayDeque<>(inside.get());
                    inherited.add(task);
                    service.submit(() -> {
                        ArrayDeque<SafeOptAsync.AsyncWork> local = inside.get();
                        try {
                            local.addAll(inherited);
                            task.run();

                        } finally {
                            freeThreads.incrementAndGet();
                            Iterator<SafeOptAsync.AsyncWork> descendingIterator = inherited.descendingIterator();
                            while (descendingIterator.hasNext()) {
                                local.removeLastOccurrence(descendingIterator.next());
                            }
                        }
                    });
                    return;

                }
                freeThreads.incrementAndGet();
                try {
                    current.addLast(task);
                    task.run();

                } finally {
                    current.removeLastOccurrence(task);
                }

            }
        };
    }

    public static final Submitter DEFAULT_POOL = createDefault();

    private static Submitter createDefault() {
        ExecutorService service = Checked.createDefaultExecutorService();
        if (Checked.VIRTUAL_EXECUTORS_METHOD.isPresent()) {//virtual online
            return ofUnlimitedParallelism(service, NESTING_LIMIT);
        }
        return ofLimitedParallelism(service, Checked.REASONABLE_PARALLELISM, 1);
    }

    public static final Submitter IN_PLACE = new Submitter() {
        @Override
        public void submit(SafeOptAsync.AsyncWork task) {
            task.run();
        }

        @Override
        public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
            return true;
        }
    };

    public static final Submitter NEW_THREAD = new Submitter() {
        private final int nesting = Math.min(2, NESTING_LIMIT);
        //always start new thread even if nested calls
        private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> null);

        @Override
        public void submit(SafeOptAsync.AsyncWork task) {
            Objects.requireNonNull(task);
            ArrayDeque<SafeOptAsync.AsyncWork> current = inside.get();
            if (insideCheck(current, nesting, task)) { // same context
                //just run
                try {
                    current.addLast(task);
                    task.run();
                } finally {
                    current.removeLastOccurrence(task);
                }
            } else {
                startThread(current, task);
            }
        }

        public void startThread(ArrayDeque<SafeOptAsync.AsyncWork> current, SafeOptAsync.AsyncWork task) {

            ArrayDeque<SafeOptAsync.AsyncWork> stack = current == null ? new ArrayDeque<>() : new ArrayDeque<>(current);
            stack.add(task);
            new Thread(() -> {
                try {

                    inside.set(stack);//is empty
                    task.run();
                } finally {
                    inside.get().clear();
                }

            }).start();
        }

        @Override
        public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
            return insideCheck(inside.get(), nesting, task);
        }
    };

}

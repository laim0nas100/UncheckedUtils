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
    public static final int NESTING_LIMIT = 8;

    public abstract boolean continueInPlace(SafeOptAsync.AsyncWork task);

    public abstract void submit(SafeOptAsync.AsyncWork task);

    /**
     * 
     * @param service the work-horse and thread spawner
     * @param parallelism how many threads to be expected and limit new ones to prevent deadlocks (0 means in-place execution)
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
                ArrayDeque<SafeOptAsync.AsyncWork> stack = inside.get();
                return (stack.contains(task) || stack.size() + 1 > nesting || freeThreads.get() <= 0);

            }

            @Override
            public void submit(final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);
                ArrayDeque<SafeOptAsync.AsyncWork> current = inside.get();

                if (current.contains(task) || current.size() + 1 > nesting || freeThreads.get() <= 0) {
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
                } else {
                    freeThreads.incrementAndGet();
                    try {
                        current.addLast(task);
                        task.run();
                    } finally {
                        current.removeLastOccurrence(task);
                    }
                }

            }
        };
    }

    public static Submitter ofUnlimitedParallelism(final ExecutorService service, final int nesting) {
        Objects.requireNonNull(service);
        if (nesting < 0) {
            throw new IllegalArgumentException("Negative nesting");
        }
        return new Submitter() {

            private ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> new ArrayDeque<>(nesting));

            @Override
            public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
                ArrayDeque<SafeOptAsync.AsyncWork> stack = inside.get();
                return (stack.contains(task) || stack.size() + 1 > nesting);

            }

            @Override
            public void submit(final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);
                ArrayDeque<SafeOptAsync.AsyncWork> current = inside.get();

                if (current.contains(task) || current.size() + 1 > nesting) {
                    try {
                        current.addLast(task);
                        task.run();
                    } finally {
                        current.removeLastOccurrence(task);
                    }
                    return;
                }
                ArrayDeque<SafeOptAsync.AsyncWork> inherited = new ArrayDeque<>(inside.get());
                inherited.add(task);

                service.submit(() -> {
                    ArrayDeque<SafeOptAsync.AsyncWork> local = inside.get();
                    try {
                        local.addAll(inherited);
                        task.run();

                    } finally {
                        Iterator<SafeOptAsync.AsyncWork> descendingIterator = inherited.descendingIterator();
                        while (descendingIterator.hasNext()) {
                            local.removeLastOccurrence(descendingIterator.next());
                        }
                    }

                });

            }
        };
    }

    public static final Submitter DEFAULT_POOL = createDefault();

    private static Submitter createDefault() {
        ExecutorService service = Checked.createDefaultExecutorService();
        if (Checked.VIRTUAL_EXECUTORS_METHOD.isPresent()) {// virtual threads are online
            return ofUnlimitedParallelism(service, NESTING_LIMIT);
        } else {
            int parallelism = Runtime.getRuntime().availableProcessors();
            return ofLimitedParallelism(service, parallelism, NESTING_LIMIT);
        }
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
        //always start new thread even if nested calls
        private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> new ArrayDeque<>(NESTING_LIMIT));

        @Override
        public void submit(SafeOptAsync.AsyncWork task) {
            Objects.requireNonNull(task);
            if (inside.get().contains(task)) { // same context
                //just run
                task.run();
            } else {

                ArrayDeque<SafeOptAsync.AsyncWork> stack = new ArrayDeque<>(inside.get());
                stack.add(task);
                new Thread(() -> {
                    try {
                        inside.get().addAll(stack);//is empty
                        task.run();
                    } finally {
                        inside.get().clear();
                    }

                }).start();
            }
        }

        @Override
        public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
            ArrayDeque<SafeOptAsync.AsyncWork> stack = inside.get();
            return (stack.contains(task) || stack.size() + 1 > NESTING_LIMIT);
        }
    };

}

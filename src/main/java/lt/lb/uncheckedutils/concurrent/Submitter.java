package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
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

    public abstract void submit(boolean locked, SafeOptAsync.AsyncWork task);

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

            private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> new ArrayDeque<>(nesting));

            @Override
            public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
                return insideCheck(inside.get(), nesting, task);
            }

            @Override
            public void submit(boolean locked, final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);
                int submits = task.submits.incrementAndGet();
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
                startThread(submits, current, task);

            }

            public void startThread(int submits, ArrayDeque<SafeOptAsync.AsyncWork> current, SafeOptAsync.AsyncWork task) {
                if (submits > 2) {//one locked one waiting for lock inside new thread
                    task.submits.decrementAndGet();
                    return;
                }

                ArrayDeque<SafeOptAsync.AsyncWork> inherited = new ArrayDeque<>(current);
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
        return ofUnlimitedParallelism(Checked.createDefaultExecutorService(), NESTING_LIMIT);
    }

    public static final Submitter IN_PLACE = new Submitter() {
        @Override
        public void submit(boolean locked, SafeOptAsync.AsyncWork task) {
            task.run();
        }

        @Override
        public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
            return true;
        }
    };

    public static final Submitter NEW_THREAD = new Submitter() {
        private final int nesting = NESTING_LIMIT;
        //always start new thread even if nested calls
        private final ThreadLocal<ArrayDeque<SafeOptAsync.AsyncWork>> inside = ThreadLocal.withInitial(() -> new ArrayDeque<>(nesting));

        @Override
        public void submit(boolean locked, SafeOptAsync.AsyncWork task) {
            Objects.requireNonNull(task);
            int submits = task.submits.incrementAndGet();
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
                startThread(submits, current, task);
            }
        }

        public void startThread(int submits, ArrayDeque<SafeOptAsync.AsyncWork> current, SafeOptAsync.AsyncWork task) {

            if (submits > 2) {//one locked one waiting for lock inside new thread
                task.submits.decrementAndGet();
                return;
            }

            ArrayDeque<SafeOptAsync.AsyncWork> stack = new ArrayDeque<>(current);
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

        @Override
        public boolean continueInPlace(SafeOptAsync.AsyncWork task) {
            return insideCheck(inside.get(), nesting, task);
        }
    };

}

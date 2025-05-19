package lt.lb.uncheckedutils.concurrent;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import static lt.lb.uncheckedutils.concurrent.SafeOptAsync.DEBUG;
import lt.lb.uncheckedutils.SafeOpt;

/**
 *
 * @author laim0nas100
 */
public class SafeScope {

    private static AtomicLong debugCounter = DEBUG ? new AtomicLong(0) : null;

    public String name = DEBUG ? "SafeScope_" + debugCounter.incrementAndGet() : "";

    protected final ConcurrentLinkedDeque<SafeOpt> completed;
    protected final ConcurrentLinkedDeque<SafeScope> childScope = new ConcurrentLinkedDeque<>();
    protected final CountDownLatch countDown;

    public final Submitter submitter;

    public final CancelPolicy cp;

    /**
     * How many completions required to cancel this scope, only enabled via
     * completion listener
     */
    public final int requiredComplete;

    private SafeScope(int arg, Submitter sub, CancelPolicy cp, int requiredComplete) {
        this.submitter = Objects.requireNonNull(sub);
        this.cp = cp;
        this.requiredComplete = requiredComplete;
        if (requiredComplete > 0) {
            Objects.requireNonNull(cp, "CancelPolicy must be provided to enable completion cancellation");
            this.countDown = new CountDownLatch(requiredComplete);
            this.completed = new ConcurrentLinkedDeque<>();
        } else {
            this.countDown = null;
            this.completed = null;
        }
    }

    public SafeScope(Submitter sub, CancelPolicy cp, int requiredComplete) {
        this(0, sub, cp, requiredComplete);
    }

    public SafeScope(CancelPolicy cp, int requiredComplete) {
        this(Submitter.DEFAULT_POOL, cp, requiredComplete);
    }

    public SafeScope(Submitter sub, CancelPolicy cp) {
        this(sub, cp, -1);
    }

    public SafeScope(CancelPolicy cp) {
        this(Submitter.DEFAULT_POOL, cp, -1);
    }

    public SafeScope() {
        this(Submitter.DEFAULT_POOL, null, -1);
    }

    public SafeScope childScope() {
        return childScope(-1);
    }

    public SafeScope childScope(int required) {
        SafeScope safeScope = new SafeScope(0, submitter, CancelPolicy.fromParent(cp), required);
        this.childScope.add(safeScope);
        return safeScope;
    }

    public <T> SafeOptAsync<T> of(T value) {
        return new SafeOptAsync<>(submitter, SafeOpt.ofNullable(value), cp);
    }

    public <T> SafeOptAsync<T> ofUnpinnable(T value) {
        return new SafeOptAsync<>(Submitter.NEW_THREAD, SafeOpt.ofNullable(value), cp);
    }

    public boolean isCancelled() {
        return cp == null ? false : cp.cancelled();
    }

    public void cancel(Throwable err) {
        if (cp == null) {
            return;
        }
        cp.cancel(err);
    }

    public void cancelOnCompletion(SafeOpt completed) {
        if (cp == null) {
            return;
        }
        if (DEBUG) {
            System.out.println("Cancel on completion " + name);
        }
        cp.cancelOnCompletion(completed);
    }

    public Collection<SafeOpt> getCompleted() {
        return completed.stream().filter(f -> f != null).collect(Collectors.toList());
    }

    protected long awaitCompletionRecursive(long nanos, Long snapShot, boolean recursive, boolean timed) throws InterruptedException {
        if (countDown != null) {
            if (timed) {
                snapShot = snapShot == null ? System.nanoTime() : snapShot;
                boolean await = countDown.await(nanos, TimeUnit.NANOSECONDS);
                if (!await) {
                    return -1;
                }
                long newSnaphot = System.nanoTime();
                nanos -= newSnaphot - snapShot;
                snapShot = newSnaphot;
            } else {
                countDown.await();
            }
        }
        if (recursive) {
            for (SafeScope child : childScope) {
                if (child == null) {
                    continue;
                }
                nanos = child.awaitCompletionRecursive(nanos, snapShot, recursive, timed);
                if (timed && nanos < 0) {
                    return nanos;
                }
                snapShot = null;
            }
        }

        return nanos;
    }

    public boolean awaitCompletion(long time, TimeUnit unit) throws InterruptedException {
        return awaitCompletionRecursive(unit.toNanos(time), null, false, true) >= 0;
    }

    public boolean awaitCompletionWithChildren(long time, TimeUnit unit) throws InterruptedException {
        return awaitCompletionRecursive(unit.toNanos(time), null, true, true) >= 0;
    }

    public void awaitCompletion() throws InterruptedException {
        awaitCompletionRecursive(1, null, false, false);
    }

    public void awaitCompletionWithChildren() throws InterruptedException {
        awaitCompletionRecursive(1, null, true, false);
    }

    public static class ScopeCompleteAction<T> implements Consumer<T>, Runnable {

        public final SafeScope scope;
        public final SafeOpt<T> safeOpt;

        public ScopeCompleteAction(SafeScope scope, SafeOpt<T> safeOpt) {
            this.scope = scope;
            this.safeOpt = safeOpt;
        }

        @Override
        public void accept(T t) {
            logic();
        }

        @Override
        public void run() {
            logic();
        }

        protected void logic() {
            SafeScope current = scope;
            if (current.countDown != null) {
                current.completed.add(safeOpt);
                current.countDown.countDown();
                if (current.countDown.getCount() <= 0) {
                    current.cancelOnCompletion(safeOpt);
                }
            }
        }

    }

    public <T> Function<SafeOpt<T>, SafeOpt<T>> completionListener() {
        return completionListener(false);
    }

    public <T> Function<SafeOpt<T>, SafeOpt<T>> completionListener(final boolean allowEmpty) {
        if (countDown == null) {// completion is not relevant
            return Function.identity();
        }
        return safeOpt -> {
            ScopeCompleteAction<T> completeAction = new ScopeCompleteAction<>(this, safeOpt);
            if (allowEmpty) {
                return safeOpt.peekOrElse(completeAction, completeAction);
            } else {
                return safeOpt.peek(completeAction);
            }

        };
    }

}

package lt.lb.uncheckedutils.concurrent;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public class SafeScope {

    public volatile SafeScope parent;
    protected ConcurrentLinkedDeque<SafeOpt> completed = new ConcurrentLinkedDeque<>();
    protected CountDownLatch countDown;

    public final Submitter submitter;

    public final CancelPolicy cp;

    /**
     * How many completions required to cancel this scope, only enabled via
     * completion listener
     */
    public final int requiredComplete;

    public SafeScope(Submitter sub, CancelPolicy cp, int requiredComplete) {
        this.submitter = Objects.requireNonNull(sub);
        this.cp = cp;
        this.requiredComplete = requiredComplete;
        if (requiredComplete > 0) {
            Objects.requireNonNull(cp, "CancelPolicy must be provided to enable completion cancellation");
            this.countDown = new CountDownLatch(requiredComplete);

        }
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
        cp.cancelOnCompletion(completed);
    }

    public Collection<SafeOpt> getCompleted() {
        return completed.stream().filter(f -> f != null).collect(Collectors.toList());
    }

    public void awaitCompletion() throws InterruptedException {
        if (countDown != null) {
            countDown.await();
        }
    }

    public void awaitCompletion(long time, TimeUnit unit) throws InterruptedException {
        if (countDown != null) {
            countDown.await(time, unit);
        }
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
            scope.completed.add(safeOpt);
            scope.countDown.countDown();
            if (scope.completed.size() >= scope.requiredComplete) {
                scope.cancelOnCompletion(safeOpt);
            }
        }

    }

    public <T> Function<SafeOpt<T>, SafeOpt<T>> completionListener() {
        return completionListener(false);
    }

    public <T> Function<SafeOpt<T>, SafeOpt<T>> completionListener(final boolean allowEmpty) {
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

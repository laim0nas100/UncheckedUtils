package lt.lb.uncheckedutils.concurrent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.SafeOptBase;
import lt.lb.uncheckedutils.SafeOptCollapse;

/**
 *
 * @author laim0nas100
 */
public class SafeOptAsync<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    public static final boolean DEBUG = false;

    public static String thread() {
        Thread t = Thread.currentThread();
        return t.getName() + " " + t.getId();
    }

    public static final CompletedFuture<SafeOpt> EMPTY = new CompletedFuture<>(SafeOpt.empty());

    public static class AsyncWork implements Runnable {

        protected final SafeOpt first;
        protected final CancelPolicy cp;
        protected final Deque<FutureTask<SafeOpt>> workQueue = new ArrayDeque<>();

        protected volatile int state = UNSTARTED;

        public static final int UNSTARTED = 0;
        public static final int ACTIVE = 1;
        public static final int EXITING = 2;

        public final ReentrantLock lock = new ReentrantLock(true);

        private FutureTask<SafeOpt> getNext() throws InterruptedException {
            try {

                lock.lock(); // dont interrupt, we need to process every submitted task anyway
                for (;;) {
                    FutureTask<SafeOpt> next = workQueue.poll();
                    if (next == null) {
                        state = EXITING;
                        return null;
                    }
                    if (next.isDone()) {
                        continue;
                    }
                    //found active
                    state = ACTIVE;
                    return next;
                }
            } finally {
                lock.unlock();
            }

        }

        public void addMaybeSubmit(Submitter submitter, FutureTask<SafeOpt> task) {
            lock.lock();
            try {
                workQueue.add(task);
                if (state == UNSTARTED || state == EXITING) {
                    submitter.submit(this);
                }
            } finally {
                lock.unlock();
            }
        }

        public AsyncWork(SafeOpt first, CancelPolicy cp) {
            this.first = first;
            this.cp = cp;
        }

        @Override
        public void run() {
            logic();
        }

        protected void logic() {
            int park = -1;
            if (cp != null) { // can be interrupted if using SafeScope by other related AsyncWorkers
                park = cp.parkIfSupported();
            }

            for (;;) {
                try {
                    FutureTask<SafeOpt> next = getNext();
                    if (next == null) {
                        break;
                    }

                    // not done
                    if (cp != null && cp.cancelled()) {
                        if (DEBUG) {
                            System.out.println("Cancelled without running");
                        }
                        next.cancel(cp.interruptableAwait);
                        continue;
                    }

                    next.run();
                    SafeOpt get = next.get();
                    if (cp != null && cp.cancelOnError && get.hasError()) {
                        cp.cancel(first, get.rawException());
                        if (DEBUG) {
                            System.out.println("Cancelled after running");
                        }
                    }

                } catch (CancellationException | ExecutionException | InterruptedException discard) {
                    if (DEBUG) {
                        System.out.println("Discarded:" + discard.getClass().getSimpleName() + " " + discard.getMessage());
                    }
                    //every FutureTask is a mapping to SafeOpt. SafeOpt never throws by desing
                    //only way to get here is by unlikely thread race condition if it is cancelled after checking isDone,
                    //even then it should be handled by collapse method

                }

            }
            if (park >= 0) {
                cp.unparkIfSupported(park);
            }
        }

        protected void await() {
            LockSupport.parkNanos(1);//spin wait
        }
    }

    @Override
    public SafeOpt<T> collapse() {
        if (complete != null) {
            return complete;
        }
        int park = -1;
        if (async.cp != null) {
            if (async.cp.cancelled()) {
                return complete = async.cp.getError();
            }
            park = async.cp.parkIfSupported();
        }
        try {
            if (submitter.limited()) { // resolve nasty nesting deadlocks
                while (complete == null) {
                    try {
                        complete = base.get(500, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException ex) {
                        boolean locked = false;
                        try {

                            async.lock.lockInterruptibly();
                            locked = true;
                            if (async.state == AsyncWork.ACTIVE) {
                                continue;
                            }
                            if (!base.isDone()) {//maybe done after lock await
                                async.run();
                            }

                        } finally {
                            if (locked) {
                                async.lock.unlock();
                            }
                        }
                    }
                }
            } else {
                complete = base.get();
            }

        } catch (InterruptedException | CancellationException cancelled) {
            if (async.cp != null) {
                complete = async.cp.getError();
            } else {
                complete = SafeOpt.error(cancelled);
            }
        } catch (ExecutionException ex) { // should not happen, since SafeOpt captures exceptions
            Throwable cause = ex.getCause();
            if (cause != null) {
                complete = SafeOpt.error(cause);
            } else {
                complete = SafeOpt.error(ex);
            }
        } finally {
            if (park >= 0) {
                async.cp.unparkIfSupported(park);
            }
        }
        return complete;
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (submitter.continueInPlace(async)) {
            return new SafeOptAsync<>(submitter, func.apply(collapse()), async);
        }

        FutureTask<SafeOpt<O>> futureTask = new FutureTask<>(() -> func.apply(collapse()));
        async.addMaybeSubmit(submitter, (FutureTask) futureTask);

        return new SafeOptAsync<>(submitter, futureTask, async);
    }

    @Override
    public <O> SafeOpt<O> functorCheap(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (base.isDone() || complete != null) {
            return new SafeOptAsync<>(submitter, func.apply(collapse()), async);
        } else {
            return functor(func);
        }
    }

    protected final Future<SafeOpt<T>> base;
    protected final Submitter submitter;
    protected final AsyncWork async;
    protected SafeOpt<T> complete;

    public SafeOptAsync(Submitter submitter, SafeOpt<T> complete) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.async = createWork(null);
    }

    public SafeOptAsync(Submitter submitter, SafeOpt<T> complete, CancelPolicy cp) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.async = createWork(cp);
    }

    protected SafeOptAsync(Submitter submitter, SafeOpt<T> complete, AsyncWork asyncWork) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.async = Objects.requireNonNull(asyncWork);
    }

    protected SafeOptAsync(Submitter submitter, Future<SafeOpt<T>> base, AsyncWork asyncWork) {
        this.submitter = Objects.requireNonNull(submitter);
        this.base = Objects.requireNonNull(base);
        this.async = Objects.requireNonNull(asyncWork);
    }

    protected AsyncWork createWork(CancelPolicy cp) {
        return new AsyncWork(this, cp);
    }

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return SafeOpt.empty();
        }
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException should not be present");
        }

        if (rawValue != null) {
            return new SafeOptAsync<>(submitter, SafeOpt.of(rawValue), async.cp);
        } else {
            return new SafeOptAsync<>(submitter, SafeOpt.error(rawException), async.cp); // only async when processing errors, otherwise same as empty
        }
    }

}

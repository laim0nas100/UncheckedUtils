package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import lt.lb.uncheckedutils.concurrent.CancelPolicy;
import lt.lb.uncheckedutils.concurrent.CompletedFuture;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author laim0nas100
 */
public class SafeOptAsync<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    public static final CompletedFuture<SafeOpt> EMPTY = new CompletedFuture<>(SafeOpt.empty());

    public static class AsyncWork implements Runnable {

        protected ConcurrentLinkedDeque<FutureTask<SafeOpt>> work = new ConcurrentLinkedDeque<>();
        protected AtomicInteger added = new AtomicInteger(0);

        protected AtomicReference<Thread> workThread = new AtomicReference<>(null);
        protected AtomicInteger queue = new AtomicInteger(0);

        protected final SafeOpt first;
        protected final CancelPolicy cp;

        public AsyncWork(SafeOpt first, CancelPolicy cp) {
            this.first = first;
            this.cp = cp;
        }

        @Override
        public void run() {

            if (workThread.compareAndSet(null, Thread.currentThread())) {
                try {
                    logic();
                } finally {
                    queue.decrementAndGet();
                    workThread.set(null);
                }
            } else {
                queue.decrementAndGet();
            }

        }

        protected void logic() {
            int park = -1;
            if (cp != null) { // can be interrupted if using SafeScope by other related AsyncWorkers
                park = cp.parkIfSupported();
            }

            while (added.get() > 0) {
                FutureTask<SafeOpt> next = work.poll();
                if (next == null) {
                    await();// do not kill thread, await due to congestion
                    continue;
                }
                // next non-null
                added.decrementAndGet();
                if (next.isDone()) {
                    continue;
                }

                // not done
                if (cp != null && cp.cancelled()) {
                    next.cancel(cp.interruptableAwait);
                    continue;
                }

                try {
                    next.run();
                    SafeOpt get = next.get();
                    if (cp != null && cp.cancelOnError && get.hasError()) {
                        cp.cancel(first, get.rawException());
                    }

                } catch (CancellationException | ExecutionException | InterruptedException discard) {
                    //every FutureTask is a mapping to SafeOpt. SafeOpt never throws by desing
                    //only way to get here is by unlikely thread race condition if it is cancelled after checking isDone,
                    //even then it should be handled by collapse method

                }

            }
            if (park >= 0) {
                cp.unparkIfSupported();
            }
        }

        public void wakeUp() {
            LockSupport.unpark(workThread.get());
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
            complete = base.get();

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
                async.cp.unparkIfSupported();
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

        async.added.incrementAndGet();
        FutureTask<SafeOpt<O>> futureTask = new FutureTask<>(() -> func.apply(collapse()));

        async.work.add((FutureTask) futureTask);
//        async.wakeUp();
        if (async.queue.get() <= 1) {
            async.queue.incrementAndGet();
            submitter.submit(async);
        } else {
            async.queue.decrementAndGet();
        }

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

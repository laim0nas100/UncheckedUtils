package lt.lb.uncheckedutils;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
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

        protected final CancelPolicy cp;
        protected ConcurrentLinkedDeque<FutureTask<SafeOpt>> work = new ConcurrentLinkedDeque<>();
        protected AtomicInteger added = new AtomicInteger(0);

        protected AtomicReference<Thread> workThread = new AtomicReference<>(null);
        protected AtomicInteger queue = new AtomicInteger(0);

        protected final SafeOpt first;

        public AsyncWork(SafeOpt first, CancelPolicy cp) {
            this.first = first;
            this.cp = cp;
        }

        @Override
        public void run() {
            queue.decrementAndGet();
            if (workThread.compareAndSet(null, Thread.currentThread())) {
                try {
                    logic();
                } finally {
                    workThread.set(null);
                }
            }

        }

        protected void logic() {
            int park = -1;
            if (cp != null) { // can be interrupted if using SafeScope by other related AsyncWorkers
                park = cp.parkIfSupported();
            }

            while (added.get() > 0) {
                Iterator<FutureTask<SafeOpt>> iterator = work.iterator();
                if (!iterator.hasNext()) {
//                    await();
                    continue;//spin wait, because added is not zero
                }
                FutureTask<SafeOpt> next = iterator.next();
                if (next == null) {
                    continue;
                }
                iterator.remove();
                added.decrementAndGet();
                if (cp != null && cp.cancelled()) {
                    next.cancel(cp.interruptableAwait);
                } else {
                    if (!next.isDone()) {

                        try {
                            next.run();
                            SafeOpt get = next.get();
                            if (cp != null && cp.cancelOnError && get.hasError()) {
                                cp.cancel(first, get.rawException());

                            }

                        } catch (CancellationException | ExecutionException | InterruptedException neverHappens) {

                        }
                    }

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
            LockSupport.parkNanos(1000_000_000);//spin wait 1000 milliseconds
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
        if (!isAsync || submitter.inside()) {
            return func.apply(collapse());
        }

        async.added.incrementAndGet();
        FutureTask<SafeOpt<O>> futureTask = new FutureTask<>(() -> func.apply(collapse()));

        async.work.add((FutureTask) futureTask);
//        async.wakeUp();
        if (async.queue.incrementAndGet() <= 2) {
            submitter.submit(async);
        } else {
            async.queue.decrementAndGet();
        }

        return new SafeOptAsync<>(submitter, futureTask, isAsync, async);
    }

    protected final Future<SafeOpt<T>> base;
    protected final Submitter submitter;
    protected final boolean isAsync;
    protected final AsyncWork async;
    protected SafeOpt<T> complete;

    public SafeOptAsync(Submitter submitter, SafeOpt<T> complete) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.isAsync = true;
        this.async = createWork(null);
    }

    public SafeOptAsync(Submitter submitter, SafeOpt<T> complete, CancelPolicy cp) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.isAsync = true;
        this.async = createWork(cp);
    }

    protected SafeOptAsync(Submitter submitter, SafeOpt<T> complete, AsyncWork asyncWork) {
        this.submitter = Objects.requireNonNull(submitter);
        this.complete = Objects.requireNonNull(complete);
        this.base = (Future) EMPTY;
        this.isAsync = true;
        this.async = Objects.requireNonNull(asyncWork);
    }

    protected SafeOptAsync(Submitter submitter, Future<SafeOpt<T>> base, boolean isAsync, AsyncWork asyncWork) {
        this.submitter = Objects.requireNonNull(submitter);
        this.base = Objects.requireNonNull(base);
        this.isAsync = isAsync || submitter.inside();
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
            return new SafeOptAsync<>(submitter, SafeOpt.of(rawValue), async);
        } else {
            return new SafeOptAsync<>(submitter, SafeOpt.error(rawException), async); // only async when processing errors, otherwise same as empty
        }
    }

    /**
     * Not {@inheritDoc}
     */
    @Override
    public SafeOpt<Throwable> getError() {
        return functor(f -> f.getError());
    }

}

package lt.lb.uncheckedutils;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import lt.lb.uncheckedutils.concurrent.CancelPolicy;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author Lemmin
 */
public class SafeOptAsyncUnpinnable<T> extends SafeOptAsync<T> {

    public static class AsyncPersistantWork extends AsyncWork {

        protected AtomicReference<SafeOpt> last = new AtomicReference<>(null);

        public AsyncPersistantWork(SafeOpt first, CancelPolicy cp) {
            super(first, cp);
        }

        @Override
        public void run() {
            if (workThread.compareAndSet(null, Thread.currentThread())) {
                try {
                    while (shouldStayAlive()) {
                        logic();
                        await();
                    }
                } finally {
                    workThread.set(null);
                }
            }

        }

        public boolean shouldStayAlive() {
            if (last == null) {
                return true;
            }
            SafeOpt get = last.get();
            if (get == null) {
                return false;
            }
//            if (get instanceof SafeOptAsyncUnpinnable) {
//                SafeOptAsyncUnpinnable unpinnable = (SafeOptAsyncUnpinnable) get;
//                if (unpinnable.complete != null) {
//                    return false;
//                }
//            }
            return true;
        }

    }

    public SafeOptAsyncUnpinnable(SafeOpt<T> complete) {
        super(Submitter.NEW_THREAD, complete);
    }

    public SafeOptAsyncUnpinnable(SafeOpt<T> complete, CancelPolicy cp) {
        super(Submitter.NEW_THREAD, complete, cp);
    }

    protected SafeOptAsyncUnpinnable(Submitter sub, Future<SafeOpt<T>> base, boolean isAsync, AsyncPersistantWork asyncWork) {
        super(sub, base, isAsync, asyncWork);
//        submitter.submit(getWork());
        //assume work was submitted
    }

    public SafeOptAsyncUnpinnable(Submitter submitter, SafeOpt<T> complete, AsyncWork asyncWork) {
        super(submitter, complete, asyncWork);
        //assume work was submitted
    }

    @Override
    protected AsyncWork createWork(CancelPolicy cp) {
        return new AsyncPersistantWork(this, cp);
    }

    protected AsyncPersistantWork getWork() {
        return (AsyncPersistantWork) async;
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {

        Objects.requireNonNull(func, "Functor is null");
        if (!isAsync || submitter.inside()) {
            return func.apply(collapse());
        }

        AsyncPersistantWork asWork = getWork();
        SafeOpt[] last = new SafeOpt[1];

        FutureTask<SafeOpt<O>> futureTask = new FutureTask<>(() -> {
            try {
                return func.apply(collapse());
            } finally {
                if (asWork.last.compareAndSet(last[0], null)) {// looking for cleanup event
                    asWork.wakeUp();
                }
            }

        });
        asWork.added.incrementAndGet();
        asWork.work.add((FutureTask) futureTask);

        SafeOptAsyncUnpinnable<O> safeOpt = new SafeOptAsyncUnpinnable<>(submitter, futureTask, isAsync, asWork);
        last[0] = safeOpt;
        if (asWork.last.compareAndSet(null, safeOpt)) {//need submittion
            submitter.submit(async);
        } else {
            asWork.last.set(safeOpt);
            asWork.wakeUp();
        }

        return safeOpt;
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
            return new SafeOptAsyncUnpinnable<>(SafeOpt.of(rawValue), getWork().cp);
        } else {
            return new SafeOptAsyncUnpinnable<>(SafeOpt.error(rawException), getWork().cp);
        }
    }

}

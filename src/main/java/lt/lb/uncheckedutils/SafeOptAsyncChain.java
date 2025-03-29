package lt.lb.uncheckedutils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import lt.lb.uncheckedutils.concurrent.AtomicArray;
import lt.lb.uncheckedutils.concurrent.CancelPolicy;
import lt.lb.uncheckedutils.concurrent.CompletedFuture;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author laim0nas100
 */
public class SafeOptAsyncChain<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    public static final SafeOpt CANCELLED = SafeOpt.error(new PassableException("Cancelled"));

    private static final boolean DEBUG = false;

    public static class SingleCompletion {

        public final Function<SafeOpt, SafeOpt> func;
        public final CompletableFuture<SafeOpt> result = new CompletableFuture<>();
        public final AtomicBoolean initiated = new AtomicBoolean(false);
        protected SafeOpt completed;

        public SingleCompletion(Function<SafeOpt, SafeOpt> func) {
            this.func = Objects.requireNonNull(func);
        }

        public SafeOpt complete(SafeOpt val) {
            if (initiated.compareAndSet(false, true)) {
                completed = func.apply(val);
                if(completed == null){
                    throw new IllegalStateException("Completed function return null");
                }
                result.complete(completed);
            }
            return completed;
        }

        public SafeOpt cancel() {
            if (initiated.compareAndSet(false, true)) {
                completed = CANCELLED;
                result.complete(CANCELLED);
            }
            return CANCELLED;
        }

        public SafeOpt cancel(SafeOpt cancelOpt) {
            if (initiated.compareAndSet(false, true)) {
                completed = cancelOpt == null ? CANCELLED : cancelOpt;
                result.complete(completed);
            }
            return completed;
        }

    }

    public static class Chain extends AtomicArray<SingleCompletion> {

        /**
         * DEBUG VARAIBLES
         */
        static AtomicLong idGen = new AtomicLong(0);
        final long id;
        public final static Collection<String> _debug_threadIds = new LinkedBlockingDeque<>();

        public final static AtomicInteger _debug_maxSize = new AtomicInteger();

        final Collection<String> threads = new ArrayList<>();

        /**
         * State
         */
        public static final int FORK_AT = 100;

        public final Future<SafeOpt> base;

        public final AtomicBoolean running = new AtomicBoolean(false);
        public final AtomicInteger runningSubmittions = new AtomicInteger(0);

        public final int forkAt;

        public Chain(Future<SafeOpt> base, int forkAt, CancelPolicy cancelledRef) {
            super(forkAt + 2, cancelledRef);
            this.base = base;
            this.forkAt = forkAt;
            id = DEBUG ? idGen.incrementAndGet() : 0L;
        }

        public Chain(Future<SafeOpt> base) {
            this(base, FORK_AT, null);
        }

        public Chain(Future<SafeOpt> base, CancelPolicy cancelledRef) {
            this(base, FORK_AT, cancelledRef);
        }

        @Override
        public void add(SingleCompletion value) {
            super.add(value);

            if (DEBUG) {
                _debug_maxSize.accumulateAndGet(size(), (c, s) -> {
                    return Math.max(c, s);
                });
            }
        }

        public Chain startNew(int index) {
            if (index > size()) {
                throw new IllegalArgumentException("New chain index bigger than chain size:" + index + " " + getLastWriteIndex());
            }
            SingleCompletion newBase = read(index); // is new base
            return new Chain(newBase.result, this.forkAt, cp);
        }

        public void run() {
            int last = getLastWriteIndex();

            //snapshot lastWrite and size
            if (last >= 0 && last + 1 >= size()) {
                return;
            }

            SafeOpt[] current = new SafeOpt[1];
            if (isCancelled()) {//do not assign
            } else if (last < 0) {
                current[0] = awaitDone(base);
            } 
             current[0] = awaitDone(base);
//            else {
//                current[0] = awaitDone(read(last).result);
//            }

            if (DEBUG) {
                threads.add(Thread.currentThread().getName());
                Chain._debug_threadIds.add(id + " " + threads);
            }

            int i = 0;

            for (; i < size(); i++) {//during running, new functors can be supplied

                final int in = i;
                write(i, func -> {
                    if (isCancelled()) {
                        if (cp != null) {
                            func.cancel(cp.getError());
                        } else {
                            func.cancel();
                        }

                    } else {
                        SafeOpt complete = func.complete(current[0]);
                        current[0] = complete;
                        if (cp != null) {
                            if (complete.hasError() && cp.cancelOnError) {
                                cp.cancel(complete.rawException());
                                if (cp.interruptableAwait) {
                                    cp.interruptParkedThreads();
                                }
                            }
                        }

                    }

                    return func;
                });
            }
        }

    }

    protected SafeOpt<T> resolve(boolean onlyTryWork) {
        if (index < 0 || chain == null) {
            return awaitDone((Future) base);
        }
        if (!onlyTryWork && chain.isCancelled()) {
            return CANCELLED;
        }
        if (!onlyTryWork && chain.getLastWriteIndex() >= index) { // this stage is completed
            return awaitDone(chain.read(index).result);
        }
        for (int rep = 0; rep < 100; rep++) {
//            if (!onlyTryWork && chain.getLastWriteIndex() >= index) { // this stage is completed
//                return awaitCast(chain.read(index).result, chain);
//            }

            if (chain.running.compareAndSet(false, true)) {

                try {
                    while (true) {
                        if (!onlyTryWork && chain.isCancelled()) {
                            return CANCELLED;
                        }
                        int lastWrite = chain.getLastWriteIndex();

                        //snapshot lastWrite and size
                        if (lastWrite >= 0 && lastWrite + 1 >= chain.size()) {
                            break;
                        }

                        SafeOpt[] current = new SafeOpt[1];
                        if (chain.isCancelled()) {//do not assign
                        } else if (lastWrite < 0) {
                            current[0] = awaitDone(chain.base);
                        } 
                         current[0] = awaitDone(chain.base);
//                        else {
//                            current[0] = awaitDone(chain.read(lastWrite).result);
//                        }

                        if (DEBUG) {
                            chain.threads.add(Thread.currentThread().getName());
                            Chain._debug_threadIds.add(chain.id + " " + chain.threads);
                        }

                        int i = 0;

                        for (; i < chain.size(); i++) {//during running, new functors can be supplied

                            chain.write(i, func -> {
                                if (chain.isCancelled()) {
                                    if (chain.cp != null) {
                                        func.cancel(chain.cp.getError());
                                    } else {
                                        func.cancel();
                                    }

                                } else {
                                    SafeOpt complete = func.complete(current[0]);
                                    current[0] = complete;
                                    if (chain.cp != null) {
                                        if (complete.hasError() && chain.cp.cancelOnError) {
                                            chain.cp.cancel(complete.rawException());
                                            if (chain.cp.interruptableAwait) {
                                                chain.cp.interruptParkedThreads();
                                            }
                                        }
                                    }

                                }

                                return func;
                            });
                        }
                    }

                } finally {
                    chain.running.set(false);
                }

            }
        }
        if (onlyTryWork) {
            return null;
        }
        if (chain.isCancelled()) {
            return CANCELLED;
        }

        return awaitCast(chain.read(index).result, chain);

    }

    static <A> SafeOpt<A> awaitDone(Future<SafeOpt> future) {
        if (!future.isDone()) {
            return SafeOpt.error(new IllegalStateException("Future is not done"));
        }
        try {
            return (SafeOpt) future.get();
        } catch (InterruptedException | ExecutionException e) {
            return SafeOpt.error(e);
        }
    }

    static <A> SafeOpt<A> awaitCast(Future<SafeOpt> future, Chain chain) {
        return await((Future) future, chain);
    }

    static <A> SafeOpt<A> await(Future<SafeOpt<A>> future, Chain chain) {
        try {
            if (chain == null || future.isDone()) {
                return (SafeOpt) future.get();
            }
        } catch (InterruptedException | ExecutionException ex) {// should never happen
            return SafeOpt.error(ex);
        }

        int parked = -1;
        if (chain.cp != null && chain.cp.interruptableAwait) { // to be interruped
            parked = chain.cp.parkIfSupported();
        }
        SafeOpt<A> toReturn = null;
        boolean keepTrying = true;
        int tries = 5;
        while (keepTrying && tries > 0) {
            tries--;
            try {

                toReturn = future.get(1, TimeUnit.SECONDS);
                keepTrying = false;
            } catch (InterruptedException ex) {
                keepTrying = false;
                if (chain.cp != null && chain.isCancelled()) {
                    toReturn = chain.cp.getError();
                } else {
                    toReturn = SafeOpt.error(ex);
                }
            } catch (ExecutionException ex) {
                keepTrying = false;
                Throwable cause = ex.getCause();
                if (cause != null) {
                    toReturn = SafeOpt.error(cause);
                } else {
                    toReturn = SafeOpt.error(ex);
                }
            } catch (TimeoutException ex) {
                if (chain.cp != null && chain.isCancelled()) {
                    keepTrying = false;
                    toReturn = chain.cp.getError();
                }
            }
        }

        if (parked >= 0) {
            chain.cp.unparkIfSupported();
        }
        if (tries <= 0) {
            return SafeOpt.error(new PassableException("Out of tries, deadlock"));
        }
        return toReturn;
    }

    @Override
    public SafeOpt<T> collapse() {
        return resolve(false);
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (!isAsync) {
            return func.apply(collapse());
        } else {
            if (chain.forkAt > 0 && chain.forkAt <= index + 1) {

                int newIndex = 0;
                SingleCompletion completion = new SingleCompletion((Function) func);
                Chain newChain = chain.startNew(index);
                newChain.add(completion);

                if (DEBUG) {
                    Chain._debug_threadIds.add(chain.id + " fork to " + newChain.id);
                }

                SafeOptAsyncChain safeOpt = new SafeOptAsyncChain<>(submitter, (Future) completion.result, newIndex, true, newChain);
                SafeOptAsyncChain me = this;
//                me.resolve(true);
                chain.run();
                newChain.run();
//                submitter.submit(() -> {
//                    chain.run();
//                    newChain.run();
//                    return null;
//                });
                return safeOpt;

            } else {// no fork
                SingleCompletion completion = new SingleCompletion((Function) func);
                chain.add(completion);
//                chain.run();
                SafeOptAsyncChain<O> produce = produce(completion);
                submitter.submit(() -> {
                    chain.run();
                    return null;
                });
                return produce;
            }

        }
    }

    protected final Future<SafeOpt<T>> base;

    protected final Submitter submitter;
    protected final boolean isAsync;
    protected final Chain chain;
    protected final int index;

    protected SafeOptAsyncChain(Submitter submitter, Future<SafeOpt<T>> base, int index, boolean isAsync, Chain chain) {
        this.submitter = Objects.requireNonNull(submitter);
        this.base = Objects.requireNonNull(base);
        this.isAsync = isAsync;
        this.index = Math.max(index, -1);

        if (!isAsync) {
            this.chain = null;
        } else {
            if (chain != null) {
                this.chain = chain;
            } else {
                this.chain = new Chain((Future) base);
            }
        }

    }

    protected <A> SafeOptAsyncChain<A> produce(SafeOpt<A> safe) {
        return new SafeOptAsyncChain<>(submitter, new CompletedFuture<>(safe), -1, true, null);
    }

    protected <A> SafeOptAsyncChain<A> produce(SingleCompletion completion) {
        return new SafeOptAsyncChain<>(submitter, (Future) completion.result, index + 1, true, chain);
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
            return produce(SafeOpt.of(rawValue));
        } else {
            return produce(SafeOpt.error(rawException));
        }
    }

    public static <A> SafeOptAsyncChain<A> ofNullable(A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsyncChain<>(Submitter.DEFAULT_POOL, completedFuture, -1, true, new Chain((Future) completedFuture));
    }

    public static <A> SafeOptAsyncChain<A> ofNullable(ExecutorService service, A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsyncChain<>(Submitter.ofExecutorService(service), completedFuture, -1, true, new Chain((Future) completedFuture));
    }

    public static <A> SafeOptAsyncChain<A> ofNullable(Submitter service, A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsyncChain<>(service, completedFuture, -1, true, new Chain((Future) completedFuture));
    }

    public static <A> SafeOptAsyncChain<A> ofNullable(Submitter service, A value, CancelPolicy cp) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsyncChain<>(service, completedFuture, -1, true, new Chain((Future) completedFuture, cp));
    }

}

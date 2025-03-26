package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lt.lb.uncheckedutils.concurrent.AtomicArray;
import lt.lb.uncheckedutils.concurrent.CompletedFuture;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author laim0nas100
 */
public class SafeOptAsync<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {

    public static final CompletedFuture<SafeOpt> EMPTY_COMPLETED_FUTURE = new CompletedFuture<>(SafeOpt.empty());

    public static class SingleCompletion {

        public final int index;
        public final Function<SafeOpt, SafeOpt> func;
        public final CompletableFuture<SafeOpt> result = new CompletableFuture<>();
        public final AtomicBoolean initiated = new AtomicBoolean(false);
        protected SafeOpt completed;

        public SingleCompletion(int index, Function<SafeOpt, SafeOpt> func) {
            this.index = index;
            this.func = Objects.requireNonNull(func);
        }

        public SafeOpt complete(SafeOpt val) {
            if (initiated.compareAndSet(false, true)) {
                completed = func.apply(val);
                result.complete(completed);
            }
            return completed;
        }

    }

    public static class Chain extends AtomicArray<SingleCompletion> {
        
        public static AtomicInteger maxSize = new AtomicInteger();

        public final Future<SafeOpt> base;

        public final AtomicReference<Thread> running = new AtomicReference<>();

        public Chain(Future<SafeOpt> base) {
            super();
            this.base = base;
        }

        @Override
        public void add(SingleCompletion value) {
            super.add(value); 
            
            maxSize.accumulateAndGet(size(), (c,s)->{
                return Math.max(c, s);
            });
        }
        
        

    }

    protected SafeOpt<T> resolve() {
        if (index < 0 || chain == null) {
            return await(base);
        }

        for (int rep = 0; rep < 100; rep++) {
            if (chain.getLastWriteIndex() >= index) { // this stage is completed
                return awaitCast(chain.read(index).result);
            }

            if (chain.running.compareAndSet(null, Thread.currentThread())) {
                try {
                    while (true) {
                        int lastWrite = chain.getLastWriteIndex();
                        int size = chain.size();

                        //snapshot lastWrite and size
                        if (lastWrite >= 0 && lastWrite + 1 >= size) {
                            break;
                        }

                        SafeOpt[] current = new SafeOpt[1];
                        if (lastWrite < 0) {
                            current[0] = awaitCast(chain.base);
                        } else {
                            current[0] = awaitCast(chain.read(lastWrite).result);
                        }

                        int i = lastWrite + 1;

                        for (; i < size; i++) {//during running, new functors can be supplied
                            chain.write(i, func -> {
                                current[0] = func.complete(current[0]);

                                return func;
                            });
                        }
                    }

                } finally {
                    chain.running.set(null);
                }

            }
        }

        return awaitCast(chain.read(index).result);

    }

    static <A, B> SafeOpt<A> awaitCast(Future<SafeOpt> future) {
        return await((Future) future);
    }

    static <A> SafeOpt<A> await(Future<SafeOpt<A>> future) {
        try {
            return (SafeOpt) future.get();
        } catch (InterruptedException ex) {
            return SafeOpt.error(ex);
        } catch (ExecutionException ex) { // should not happen, since SafeOpt captures exceptions
            Throwable cause = ex.getCause();
            if (cause != null) {
                return SafeOpt.error(cause);
            } else {
                return SafeOpt.error(ex);
            }
        }
    }

    @Override
    public SafeOpt<T> collapse() {
        return resolve();
    }

    @Override
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (!isAsync) {
            return func.apply(collapse());
        } else {
            SingleCompletion completion = new SingleCompletion(index + 1, (Function) func);
            chain.add(completion);

            SafeOptAsync<O> produce = produce(completion);
//            if (chain.running.get() == null) {
                submitter.submit(() -> {
                    return produce.resolve();
                });
//            }
            return produce;

        }
    }

    protected volatile Future<SafeOpt<T>> base;

    protected final Submitter submitter;
    protected final boolean isAsync;
    protected final Chain chain;
    protected final int index;

    protected SafeOptAsync(Submitter submitter, Future<SafeOpt<T>> base, int index, boolean isAsync, Chain chain) {
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

    protected <A> SafeOptAsync<A> produce(SafeOpt<A> safe) {
        return new SafeOptAsync<>(submitter, new CompletedFuture<>(safe), -1, true, null);
    }

    protected <A> SafeOptAsync<A> produce(SingleCompletion completion) {
        return new SafeOptAsync<>(submitter, (Future) completion.result, index + 1, true, chain);

    }

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return new SafeOptAsync<>(submitter, (Future) EMPTY_COMPLETED_FUTURE, -1, false, null);
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

    public static <A> SafeOptAsync<A> ofNullable(A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsync<>(Submitter.DEFAULT_POOL, completedFuture, -1, true, new Chain((Future) completedFuture));
    }

    public static <A> SafeOptAsync<A> ofNullable(ExecutorService service, A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsync<>(Submitter.ofExecutorService(service), completedFuture, -1, true, new Chain((Future) completedFuture));
    }

    public static <A> SafeOptAsync<A> ofNullable(Submitter service, A value) {
        CompletedFuture<SafeOpt<A>> completedFuture = new CompletedFuture<>(SafeOpt.ofNullable(value));
        return new SafeOptAsync<>(service, completedFuture, -1, true, new Chain((Future) completedFuture));
    }

}

package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import lt.lb.uncheckedutils.concurrent.CompletedFuture;

/**
 *
 * @author laim0nas100
 */
public class SafeOptAsyncOld<T> extends SafeOptBase<T> implements SafeOptCollapse<T> {
    
    public static final CompletedFuture<SafeOpt> EMPTY = new CompletedFuture<>(SafeOpt.empty());
    
    @Override
    public SafeOpt<T> collapse() {
        try {
            return base.get();
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
    public <O> SafeOpt<O> functor(Function<SafeOpt<T>, SafeOpt<O>> func) {
        Objects.requireNonNull(func, "Functor is null");
        if (!isAsync) {
            return new SafeOptAsyncOld<>(executor, new CompletedFuture<>(func.apply(collapse())), isAsync);
        }
        FutureTask<SafeOpt<O>> future = new FutureTask<>(() -> func.apply(collapse()));
        executor.execute(future);
        return new SafeOptAsyncOld<>(executor, future, isAsync);
    }
    
    protected final Future<SafeOpt<T>> base;
    protected final Executor executor;
    protected final boolean isAsync;
    
    protected SafeOptAsyncOld(Executor exe, Future<SafeOpt<T>> base) {
        this(exe, base, true);
    }
    
    protected SafeOptAsyncOld(Executor exe, Future<SafeOpt<T>> base, boolean isAsync) {
        this.executor = Objects.requireNonNull(exe);
        this.base = Objects.requireNonNull(base);
        this.isAsync = isAsync;
    }
    
    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return new SafeOptAsyncOld<>(executor, (Future) EMPTY, false); // no point to do async work when empty
        }
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException should not be present");
        }
        if (rawValue != null) {
            return new SafeOptAsyncOld<>(executor, new CompletedFuture<>(SafeOpt.of(rawValue)), true);
        } else {
            return new SafeOptAsyncOld<>(executor, new CompletedFuture<>(SafeOpt.error(rawException)), false); // only async when processing errors, otherwise same as empty
        }
    }
    
}

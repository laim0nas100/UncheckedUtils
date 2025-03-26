package lt.lb.uncheckedutils;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import lt.lb.uncheckedutils.concurrent.CompletedFuture;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author laim0nas100
 */
@Deprecated
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
        Future<SafeOpt<O>> submit = isAsync ? submitter.submit(() -> func.apply(collapse())) : new CompletedFuture<>(func.apply(collapse()));
        return new SafeOptAsyncOld<>(submitter, submit, isAsync);
    }

    protected final Future<SafeOpt<T>> base;
    protected final Submitter submitter;
    protected final boolean isAsync;

    protected SafeOptAsyncOld(Submitter submitter, Future<SafeOpt<T>> base) {
        this(submitter, base, true);
    }

    protected SafeOptAsyncOld(Submitter submitter, Future<SafeOpt<T>> base, boolean isAsync) {
        this.submitter = Objects.requireNonNull(submitter);
        this.base = Objects.requireNonNull(base);
        this.isAsync = isAsync;
    }

    @Override
    public <A> SafeOpt<A> produceNew(A rawValue, Throwable rawException) {
        if (rawValue == null && rawException == null) {
            return new SafeOptAsyncOld<>(submitter, (Future) EMPTY, false); // no point to do async work when empty
        }
        if (rawValue != null && rawException != null) {
            throw new IllegalArgumentException("rawValue AND rawException should not be present");
        }
        if (rawValue != null) {
            return new SafeOptAsyncOld<>(submitter, new CompletedFuture<>(SafeOpt.of(rawValue)), true);
        } else {
            return new SafeOptAsyncOld<>(submitter, new CompletedFuture<>(SafeOpt.error(rawException)), false); // only async when processing errors, otherwise same as empty
        }
    }

}

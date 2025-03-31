package lt.lb.uncheckedutils.concurrent;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lt.lb.uncheckedutils.Checked;
import lt.lb.uncheckedutils.SafeOptAsync;

/**
 *
 * @author laim0nas100
 */
public abstract class Submitter {

    public abstract boolean inside();

    public abstract void submit(SafeOptAsync.AsyncWork task);

    public static Submitter ofExecutorService(final ExecutorService service) {
        Objects.requireNonNull(service);
        return new Submitter() {
            ThreadLocal<Boolean> inside = ThreadLocal.withInitial(() -> false);

            @Override
            public boolean inside() {
                return inside.get();
            }

            @Override
            public void submit(final SafeOptAsync.AsyncWork task) {
                Objects.requireNonNull(task);
                if (inside.get()) {
                    //just run
                    task.run();
                } else {
                    service.submit(() -> {
                        try {
                            inside.set(true);
                            task.run();
                        } finally {
                            inside.set(false);
                        }

                    });
                }
            }
        };
    }

    public static final Submitter DEFAULT_POOL = ofExecutorService(Checked.createDefaultExecutorService());

    public static final Submitter IN_PLACE = new Submitter() {
        @Override
        public void submit(SafeOptAsync.AsyncWork task) {
            task.run();
        }

        @Override
        public boolean inside() {
            return true;
        }
    };

}

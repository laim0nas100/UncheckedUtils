package test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lt.lb.uncheckedutils.CancelException;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.PassableException;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.SafeOptAsyncChain;
import lt.lb.uncheckedutils.concurrent.CancelPolicy;
import lt.lb.uncheckedutils.concurrent.SafeScope;
import lt.lb.uncheckedutils.concurrent.Submitter;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.ThrowableTypeAssert;
import org.junit.Test;

/**
 *
 * @author laim0nas100
 */
public class SafeOptTest {

    public static class NullInt {

        public Integer get() {
            return null;
        }
    }

    @Test
    public void test() {
        SafeOpt<Number> num = SafeOpt.of(10L).select(Long.class);

        assertThat(num.isPresent()).isTrue();

        SafeOpt<Integer> map = SafeOpt.of(10).map(m -> m * 10);
        Integer expected = 10 * 10;
        assertThat(map.get()).isEqualTo(expected);

        SafeOpt<String> empty = SafeOpt.empty().map(m -> m + "");

        NullInt nullInt = new NullInt();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.getError().isEmpty()).isTrue();
        SafeOpt<Object> errored = SafeOpt.error(new PassableException("Failed"));
        assertThat(errored.isEmpty()).isTrue();
        assertThat(errored.getError().isPresent()).isTrue();

        SafeOpt<Integer> mapNull = map.map(m -> nullInt.get()); // supposed to be null
        SafeOpt<Integer> mapEx = map.map(m -> 0 + nullInt.get());
        ThrowableTypeAssert<NoSuchElementException> noSuchElement = Assertions.assertThatExceptionOfType(NoSuchElementException.class);
        noSuchElement.isThrownBy(() -> mapNull.get());
        ThrowableTypeAssert<NestedException> nestedException = Assertions.assertThatExceptionOfType(NestedException.class);
        nestedException.isThrownBy(() -> {
            Integer ok = mapEx.asOptional().get() + 0; // exception from SafeOpt invokes first
        });

        Assertions.assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
            try {
                mapEx.asOptional().get();
            } catch (NestedException ex) {
                throw ex.getCause();
            }
        });
        ThrowableTypeAssert<NestedException> nested = Assertions.assertThatExceptionOfType(NestedException.class);
        ThrowableTypeAssert<RuntimeException> runtime = Assertions.assertThatExceptionOfType(RuntimeException.class);

        nested.isThrownBy(() -> mapEx.throwIfErrorAsNested());
        nested.isThrownBy(() -> mapEx.get());
        runtime.isThrownBy(() -> mapEx.throwIfErrorRuntime());

        assertThat(map.flatMapOpt(m -> Optional.ofNullable(m)).get()).isEqualTo(expected);
        assertThat(map.flatMap(m -> SafeOpt.ofNullable(m)).get()).isEqualTo(expected);
        assertThat(map.flatMapOpt(m -> SafeOpt.ofNullable(m).asOptional()).get()).isEqualTo(expected);
        assertThat(map.flatMap(m -> SafeOpt.error(new PassableException("Some error"))).getError().select(PassableException.class).isPresent()).isTrue();

    }

    @Test
    public void testLazy() {
        List<String> states1 = new ArrayList<>();
        List<String> states2 = new ArrayList<>();
        SafeOpt<Integer> lazy = SafeOpt.ofLazy("10")
                .map(Integer::parseInt)
                .filter(f -> {
                    states1.add("filter");
                    return true;
                })
                .map(m -> {
                    states1.add("map");
                    return m;
                })
                .flatMap(m -> {
                    states1.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states1.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states1.add("peek");
                });

        assertThat(states1).isEmpty();
        Integer result = lazy.chain(m -> {
            return m.orNull();
        });
        assertThat(states1).containsExactly("filter", "map", "flatMap", "flatMapOpt", "peek");
        lazy
                .filter(f -> {
                    states2.add("filter");
                    return true;
                })
                .map(m -> {
                    states2.add("map");
                    return m;
                })
                .flatMap(m -> {
                    states2.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states2.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states2.add("peek");
                });
        assertThat(states2).containsExactlyElementsOf(states1);
        lazy.orNull();
        assertThat(states2).containsExactlyElementsOf(states1); //ensure no double inserts
        List<String> stateError = new ArrayList<>();
        SafeOpt<Integer> peekError = SafeOpt.ofLazy("NaN").map(Integer::parseInt)
                .filter(f -> {
                    stateError.add("filter");
                    return true;
                })
                .peekError(error -> {
                    stateError.add("error");
                });

        assertThat(stateError).isEmpty();
        peekError.orNull();// collapse
        assertThat(stateError).containsExactly("error");
    }

    @Test
    public void testAsync() {
        Collection<String> states1 = new LinkedBlockingDeque<>();
        Collection<String> states2 = new LinkedBlockingDeque<>();
        SafeOpt<Integer> lazy = SafeOpt.ofAsync("10")
                .map(Integer::parseInt)
                .filter(f -> {

                    states1.add("filter");
                    return true;
                })
                .map(m -> {
                    states1.add("map");
                    return m;
                })
                .flatMap(m -> {
                    states1.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states1.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states1.add("peek");
                });

        lazy.orNull();
        assertThat(states1).containsExactly("filter", "map", "flatMap", "flatMapOpt", "peek");
        lazy
                .filter(f -> {
                    states2.add("filter");
                    return true;
                })
                .map(m -> {
                    states2.add("map");
                    Thread.sleep(500);
                    return m;
                })
                .flatMap(m -> {
                    states2.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states2.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states2.add("peek");
                }).orNull();

        assertThat(states2).containsSequence(states1); //ensure no double inserts
        List<String> stateError = new ArrayList<>();
        SafeOpt<Integer> peekError = SafeOpt.ofAsync("NaN").map(Integer::parseInt)
                .filter(f -> {
                    stateError.add("filter");
                    return true;
                })
                .peekError(error -> {
                    stateError.add("error");
                });

        peekError.getError().map(err -> {
            System.out.println("Using error 1");
            return err;
        })
                .map(err -> {
                    System.out.println("Using error 2");
                    return err;
                }).orNull();

        peekError.orNull();// collapse
        assertThat(stateError).containsExactly("error");

    }

    public void testAsyncReal(ExecutorService service, boolean inside) {
        Collection<String> states1 = new LinkedBlockingDeque<>();
        Collection<String> states2 = new LinkedBlockingDeque<>();
        SafeOpt<Integer> lazy = SafeOpt.ofAsync(service, "10")
                .map(Integer::parseInt)
                .filter(f -> {
                    states1.add("filter");
                    return true;
                })
                .map(m -> {
                    states1.add("map");
                    return m;
                })
                .flatMap(m -> {
                    states1.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states1.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states1.add("peek");
                });
        lazy.orNull();
        SafeOpt<Integer> peek = lazy
                .filter(f -> {
                    states2.add("filter");
                    return true;
                })
                .map(m -> {
                    states2.add("map");
                    return m;
                })
                .flatMap(m -> {
                    states2.add("flatMap");
                    return SafeOpt.of(m);
                })
                .flatMapOpt(m -> {
                    states2.add("flatMapOpt");
                    return Optional.of(m);
                })
                .peek(m -> {
                    states2.add("peek");
                });

        Collection<String> stateError = new LinkedBlockingDeque<>();
        SafeOpt<Integer> peekError = SafeOpt.ofAsync(service, "NaN").map(Integer::parseInt)
                .filter(f -> {
                    stateError.add("filter"); // must not include
                    return true;
                })
                .peekError(error -> {
                    stateError.add("error");
                });
        peekError.orNull();// collapse
        SafeOpt<Throwable> error = peekError.getError();
        SafeOpt<Throwable> map1 = error.map(err -> {
            stateError.add("Using error 1");
            return err;
        });

        SafeOpt<Throwable> map2 = map1.map(err -> {
            stateError.add("Using error 2");
            return err;
        });
        Throwable orNull = map2.orNull();

        assertThat(orNull).isNotNull();

        peek.orNull();
        assertThat(states1).containsExactly("filter", "map", "flatMap", "flatMapOpt", "peek");
        assertThat(states1).containsSequence(states2);

        assertThat(stateError).containsExactly("error", "Using error 1", "Using error 2");

    }

    public static void benchTest() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        ExecutorService other = Executors.newFixedThreadPool(8);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i % 10 > 3) {
                Future e = pool.submit(() -> {
                    new SafeOptTest().testAsyncReal(pool, true);
                });
                futures.add(e);
            } else {
//                Future<?> submit = other.submit(() -> {
                new SafeOptTest().testAsyncReal(pool, false);
//                });
//                futures.add(submit);

            }

        }
        for (Future f : futures) {
            f.get();
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.DAYS);
        other.shutdown();
        other.awaitTermination(1, TimeUnit.DAYS);
        printDebug();
    }

    public static void printDebug() {
        List<String> sorted = SafeOptAsyncChain.Chain._debug_threadIds.stream().sorted().toList();
        List<String> distinct = sorted.stream().distinct().toList();

        System.out.println("Max chain size:" + SafeOptAsyncChain.Chain._debug_maxSize.get());
//        System.out.println("Sorted:" + sorted.size());
        System.out.println("Sorted, distinct:" + distinct.size());
//        for (String s : sorted) {
//            System.out.println(s);
//        }
//        System.out.println("XXXXXX");

        for (String s : distinct) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) throws Exception {

//        new SafeOptTest().testAsyncReal();
        printDebug();
        new SafeOptTest().testCancel();

        ThreadFactory factory = Thread.ofVirtual().factory();
    }

    @Test
    public void testCancel() throws Exception {
        SafeScope scope = new SafeScope();
        scope.cp = new CancelPolicy(true, true, 16);
//        ExecutorService pool = Executors.newFixedThreadPool(12);
//        scope.submitter = Submitter.ofExecutorService(pool);

        scope.submitter = Submitter.DEFAULT_POOL;

        SafeOpt<String> val1 = scope.of(10)
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 1");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 2");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 3");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 4");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 5");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 6");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 7");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 8");
                    return m + 1;
                })
                .map(m -> {
                    Thread.sleep(1000);
                    System.out.println("Sleep 9");
                    return m + 1;
                })
                .map(m -> String.valueOf(m));
        SafeOpt<Integer> val2 = scope.of("NaN").map(m -> {
            Thread.sleep(3300);
            System.out.println("FAIL");
//            throw new PassableException("No reason lol");
            return Integer.parseInt(m);
        });

        System.out.println("Waiting for finish");
        try {
            System.out.println(val1.throwAny());
        } catch (Exception ex) {
            System.out.println(ex.getClass());
            ex.printStackTrace();
        }

//         System.out.println(val1.throwIfErrorUnwrapping(CancelException.class));
        Assertions.assertThatExceptionOfType(CancelException.class).isThrownBy(() -> {
            val1.throwAnyOrNull();
        });
//        pool.shutdown();
    }

    @Test
    public void testAsyncReal() throws InterruptedException, ExecutionException {
        System.out.println("Testing bench");;
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        ExecutorService other = Executors.newFixedThreadPool(16);
        ArrayList<Future> futures = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            if (i % 10 >= 1) {
                Future<?> submit = other.submit(() -> {
                    new SafeOptTest().testAsyncReal(pool, true);
                });
                futures.add(submit);
            } else {
//                Future<?> submit = other.submit(() -> {
                new SafeOptTest().testAsyncReal(pool, true);
//                });
//                futures.add(submit);
            }

        }
        for (Future f : futures) {
            f.get();
        }
        System.out.println("Benched in:" + (System.currentTimeMillis() - start));
        pool.shutdown();
        other.shutdown();
        pool.awaitTermination(1, TimeUnit.DAYS);
        other.awaitTermination(1, TimeUnit.DAYS);

    }
}

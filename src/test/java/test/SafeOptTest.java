package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import lt.lb.uncheckedutils.CancelException;
import lt.lb.uncheckedutils.Checked;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.PassableException;
import lt.lb.uncheckedutils.SafeOpt;
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
                    return SafeOpt.ofAsync(m).filter(f -> true);
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

    public void testAsyncReal(boolean inside) {
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
                    return SafeOpt.ofAsync(m).filter(f -> true).map(i -> {
                        Thread.sleep(200);
                        return i;
                    });
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
        SafeOpt<Integer> peekError = SafeOpt.ofAsync("NaN").map(Integer::parseInt)
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

    public static void benchTestas() throws Exception {
//        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService other = Checked.createDefaultExecutorService();
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            if (i % 10 > -1) {
                Future e = other.submit(() -> {
                    new SafeOptTest().testAsyncReal(true);
                });
                futures.add(e);
            } else {
//                Future<?> submit = other.submit(() -> {
                new SafeOptTest().testAsyncReal(false);
//                });
//                futures.add(submit);

            }

        }
        for (Future f : futures) {
            f.get();
        }
    }

    @Test
    public void testCancelOnFinish() throws Exception {
        int completion = 5;
        SafeScope scope = new SafeScope(new CancelPolicy(true, true, true), completion);
//        ExecutorService pool = Executors.newFixedThreadPool(12);
//        scope.submitter = Submitter.ofExecutorService(pool);

        List<SafeOpt<Integer>> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            SafeOpt<Integer> chain = scope.of(i)
                    .map(f -> {
                        Thread.sleep(f * 1000);
                        return f;
                    })
                    .chain(scope.completionListener());
            list.add(chain);
        }

        System.out.println("Waiting for finish:" + list.size());

        try {
//            for (int i = 0; i < list.size(); i++) {
//                System.out.println("Awaited:" + list.get(i).throwAnyGet());
//            }
            scope.awaitCompletion();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            throw ex;
        }

        System.out.println("Completed:" + scope.getCompleted().size());
        System.out.println(scope.getCompleted());

        Assertions.assertThat(scope.getCompleted().size()).isEqualTo(completion);
        List<Integer> collect = scope.getCompleted().stream().map(m -> (int) m.get()).collect(Collectors.toList());
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < completion; i++) {
            expected.add(i);
        }
        Assertions.assertThat(scope.getCompleted().size()).isEqualTo(completion);
        Assertions.assertThat(collect).containsSequence(expected);

    }

    @Test
    public void testCancel() throws Exception {
        SafeScope scope = new SafeScope(new CancelPolicy(true, true, true));
//        ExecutorService pool = Executors.newFixedThreadPool(12);
//        scope.submitter = Submitter.ofExecutorService(pool);

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
                .peek(val -> {
                    System.out.println("Async consumed value:" + val);
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
            System.out.println("FAIL NOW");
            if (true) {
                throw new RuntimeException("Explicit failure");
            }
            return Integer.parseInt(m);
        });

        SafeOpt<Integer> val3 = scope.of("NaN").map(m -> {
            System.out.println("Try fail later");
            Thread.sleep(5500);
            System.out.println("Should not FAIL here");
            throw new RuntimeException("We failed again...");
        });

        System.out.println("Waiting for finish");
        try {
//            System.out.println(val1.throwAnyOrNull());
            System.out.println(val2.throwAnyOrNull());
//            System.out.println(val3.throwAnyOrNull());
        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
        }

//         System.out.println(val1.throwIfErrorUnwrapping(CancelException.class));
        Assertions
                .assertThatExceptionOfType(CancelException.class
                ).isThrownBy(() -> {
                    val1.throwAnyOrNull();
                }
                );
        Assertions
                .assertThatExceptionOfType(CancelException.class
                ).isThrownBy(() -> {
                    val3.throwAnyOrNull();
                }
                );
//        pool.shutdown();
    }

    @Test
    public void testCancelNested() throws Exception {
        SafeScope scope = new SafeScope(new CancelPolicy(true, true, true));
//        ExecutorService pool = Executors.newFixedThreadPool(12);
//        scope.submitter = Submitter.ofExecutorService(pool);

        AtomicInteger nested = new AtomicInteger(0);

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
                .peek(val -> {
                    System.out.println("Async consumed value:" + val);
                })
                .flatMap(m -> {
                    return scope.ofUnpinnable(m).peek(v -> {
                        nested.incrementAndGet();
                        Thread.sleep(2000);//should cancel async also
                        nested.incrementAndGet();
                    });
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
            System.out.println("FAIL NOW");
            if (true) {
                throw new RuntimeException("Explicit failure");
            }
            return Integer.parseInt(m);
        });

        SafeOpt<Integer> val3 = scope.of("NaN").map(m -> {
            System.out.println("Try fail later");
            Thread.sleep(5500);
            System.out.println("Should not FAIL here");
            throw new RuntimeException("We failed again...");
        });

        System.out.println("Waiting for finish");
        try {
//            System.out.println(val1.throwAnyOrNull());
            System.out.println(val2.throwAnyOrNull());
//            System.out.println(val3.throwAnyOrNull());
        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
        }
        Assertions.assertThat(nested.get()).isEqualTo(1);

//         System.out.println(val1.throwIfErrorUnwrapping(CancelException.class));
        Assertions
                .assertThatExceptionOfType(CancelException.class
                ).isThrownBy(() -> {
                    val1.throwAnyOrNull();
                }
                );
        Assertions
                .assertThatExceptionOfType(CancelException.class
                ).isThrownBy(() -> {
                    val3.throwAnyOrNull();
                }
                );
//        pool.shutdown();
    }

    @Test
    public void testAsyncBench() throws InterruptedException, ExecutionException {
        System.out.println("Testing bench");
        long start = System.currentTimeMillis();
        ExecutorService other = Checked.createDefaultExecutorService();
        ArrayList<Future> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i % 10 >= 0) {
                Future<?> submit = other.submit(() -> {
                    new SafeOptTest().testAsyncReal(true);
                });
                futures.add(submit);
            } else {
//                Future<?> submit = other.submit(() -> {
                new SafeOptTest().testAsyncReal(true);
//                });
//                futures.add(submit);
            }

        }
        for (Future f : futures) {
            f.get();
        }
        System.out.println("Benched in:" + (System.currentTimeMillis() - start));
        other.shutdown();

    }

    public static void pinnedSleep(long sleep) throws InterruptedException {

        Object ob = new Object();
        synchronized (ob) {
            Thread.sleep(sleep);
        }
    }

    public static void cmdSleep(int seconds) throws IOException, InterruptedException {
        Process start = new ProcessBuilder("ping", "-n", String.valueOf(seconds), "127.0.0.1").start();
        BufferedReader is = new BufferedReader(new InputStreamReader(start.getInputStream()));
        String read;
        while ((read = is.readLine()) != null) {
//            System.out.println(read);
        }

    }

    /**
     * Some long read function to enable pinning
     *
     * @throws IOException
     */
    public static void read() throws IOException {

        String dir = "C:\\files\\YT\\spec";
        List<Path> list = Files.list(Paths.get(dir)).collect(Collectors.toList());
        for (Path file : list) {
            SeekableByteChannel newByteChannel = Files.newByteChannel(file);
            boolean r = true;
            int capacity = 100000;
            while (r) {
                ByteBuffer allocate = ByteBuffer.allocate(capacity);
                int read = newByteChannel.read(allocate);
                r = read == capacity;

            }
        }

    }

    public static ExecutorService logger;

    public static void asyncPrint(String str) {
        if (logger != null) {
            logger.execute(() -> {
                System.out.println(str);
            });
        } else {
            System.out.println(str);
        }
    }

    public static long getId() {
        return Thread.currentThread().getId();
    }

    public static SafeOpt<Integer> nestedPeek(boolean async,int current, int deep, int linear, int split, int iter) {
        if (current >= deep) {
            return SafeOpt.of(0);
        }
        SafeOpt<Integer> first = async ? SafeOpt.ofAsync(0) : SafeOpt.ofAsyncUnpinnable(0);
        SafeOpt<Integer> peek = first.map(m -> {
            Thread.sleep(10);
            return m;
        });
//        peek.get();
        for (int i = 0; i < linear; i++) {
            final int l = i;
            peek = peek.peek(m -> {
                asyncPrint(Thread.currentThread().getName() + " " + getId() + " iter_" + iter + " nested_" + current + " linear_" + l);
                LockSupport.parkNanos(1_000_000);
            });
        }
        for (int i = 0; i < split; i++) {
            final int l = i;
            peek = peek.map(m -> {
                asyncPrint(Thread.currentThread().getName() + " " + getId() + " iter_" + iter + " nested_" + current + " split_" + l);
                return m;

            }).flatMap(m -> nestedPeek(async,current + 1, deep, linear, split - 1, iter));
        }
        return peek.map(m -> {
//            Thread.sleep(10);
            return m;
        }).flatMap(m -> nestedPeek(async,current + 1, deep, linear, split, iter));

    }

    public static void main(String[] args) throws Exception {

//        new SafeOptTest().testAsyncReal();
//        benchTest();
        if (true) {
            logger = Executors.newSingleThreadExecutor();
            asyncPrint(Thread.currentThread().getName() + " Start");
            List<SafeOpt> peeks = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                peeks.add(nestedPeek(false,0, 15, 5, 1, i));
            }

//            SafeOpt.ofAsync(0).peek(m -> {
//                Thread.sleep(1000);
//                System.out.println(Thread.currentThread().getName() + " " + getId() + " after");
//            });
            for (SafeOpt peek : peeks) {
                peek.get();
            }
            asyncPrint(Thread.currentThread().getName() + " End");
            asyncPrint(Checked.REASONABLE_PARALLELISM+" "+Submitter.NESTING_LIMIT);
            logger.shutdown();

//            new Thread(() -> {
//                Checked.checkedRun(()->{
//                   Thread.sleep(5000);
//                });
//            }).start();
//            read();
            return;
        }
        ExecutorService def = Checked.createDefaultExecutorService();
        ArrayList<Future> futures = new ArrayList<>();

        for (int i = 0; i < 20000; i++) {
            final int in = i;
            Future<?> submit = def.submit(() -> {
                SafeOpt.ofAsync(in).flatMap(m -> {
                    if (in % 1000 == 0) {
                        return SafeOpt.ofAsyncUnpinnable(m).map(n -> {
                            read();
                            return n;
                        });

                    }
                    return SafeOpt.ofAsync(m).peek(v -> {
                        Thread.sleep(20000);
                    });
                }).peek(m -> {

                    System.out.println(in);
                }).get();
            });
            futures.add(submit);

        }
        for (Future f : futures) {
            f.get();
        }
//        for (int i = 0; i < 50; i++) {
//            System.out.println("TeestAsyncReal:"+i);
//                new SafeOptTest().testAsyncReal(true);
//
//        }

//        ThreadFactory factory = Thread.ofVirtual().factory();
    }
}

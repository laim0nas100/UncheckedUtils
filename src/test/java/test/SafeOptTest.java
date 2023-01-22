package test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lt.lb.uncheckedutils.NestedException;
import lt.lb.uncheckedutils.PassableException;
import lt.lb.uncheckedutils.SafeOpt;
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
        List<String> states1 = new ArrayList<>();
        List<String> states2 = new ArrayList<>();
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

    public static void main(String[] args) {
        new SafeOptTest().testAsync();
    }
}

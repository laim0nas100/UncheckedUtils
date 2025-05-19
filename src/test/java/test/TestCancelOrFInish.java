package test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static lt.lb.uncheckedutils.SafeOptAsync.thread;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.concurrent.CancelPolicy;
import lt.lb.uncheckedutils.concurrent.SafeScope;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 *
 * @author Lemmin
 */
public class TestCancelOrFInish {

    @Test
    public void testCancelOnFinish() throws Exception {
        int completion = 5;
        SafeScope scope = new SafeScope(new CancelPolicy(true, true, true), completion);
//        ExecutorService pool = Executors.newFixedThreadPool(12);
//        scope.submitter = Submitter.ofExecutorService(pool);

        List<SafeOpt<Integer>> list = new ArrayList<>();
        for (int i = 1; i < 10; i++) {
            SafeOpt<Integer> chain = scope.of(i)
                    .map(f -> {
                        try {
                            System.out.println("Waiting " + f + " " + thread());
                            Thread.sleep(f * 1000);
                        } catch (InterruptedException inte) {
                            System.out.println("Interrupted " + thread());
                            throw inte;
                        }
                        return f;
                    })
                    .map(f -> {

                        System.out.println("Received " + f + " " + thread());
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
            Thread.sleep(3000);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            throw ex;
        }

        System.out.println("Completed:" + scope.getCompleted().size());
        System.out.println(scope.getCompleted());

        assertScope(scope, completion);

    }
    
    public void assertScope(SafeScope scope, int expected){
        Assertions.assertThat(scope.getCompleted().size()).isEqualTo(expected);
        List<Integer> collect = scope.getCompleted().stream().map(m -> (int) m.get()).collect(Collectors.toList());
        List<Integer> expectedSeq = new ArrayList<>();
        for (int i = 1; i <= expected; i++) {
            expectedSeq.add(i);
        }
        Assertions.assertThat(collect).containsSequence(expectedSeq);
    }

    public static void submit(SafeScope scope, int amount) {
        for (int i = 1; i < amount; i++) {
            SafeOpt<Integer> chain = scope.ofUnpinnable(i)
                    .map(f -> {
                        System.out.println(scope.name + " waiting:" + f);
                        Thread.sleep(f * 1000);

                        return f;
                    })
                    .map(f -> {
                        System.out.println(scope.name + " completed:" + f);
                        return f;
                    })
                    .chain(scope.completionListener());
        }
    }

    @Test
    public void testNestedScopes() throws Exception {
        SafeScope scope = new SafeScope(new CancelPolicy());

        SafeScope a = scope.childScope(2);
        SafeScope b = scope.childScope(5);
        SafeScope c = scope.childScope(4);

        submit(a, 10);
        submit(b, 10);
         submit(c, 10);

        scope.awaitCompletionWithChildren();
        assertScope(a, 2);
        assertScope(b, 5);
        assertScope(c, 4);

    }
}

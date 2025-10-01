package test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lt.lb.uncheckedutils.SafeOpt;
import lt.lb.uncheckedutils.concurrent.Submitter;

/**
 *
 * @author laim0nas100
 */
public class NotStuckTest {

    public static void dprint(Object string) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + string);
    }

    public static final int parallelism = 4;
    public static final int loop = parallelism;
    public static ExecutorService service = new ThreadPoolExecutor(parallelism, parallelism, 1, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    public static Submitter submitter = Submitter.ofLimitedParallelism(service, parallelism, 1);

    public static void main(String[] args) throws Exception {

        List<SafeOpt<Integer>> listDepend = new ArrayList();
        List<SafeOpt<Integer>> list1 = new ArrayList();
        dprint("Submit finals");
        for (int i = 0; i < loop; i++) {

            SafeOpt<Integer> map = SafeOpt.ofAsync(submitter, i).map(m -> {
                dprint("Start final:" + m);
                Thread.sleep(2000);
                dprint("Get dependency:" + m);
                Integer value = listDepend.get(m).get();
                dprint("End final:" + m + " with value " + value);
                return value;
            });
            list1.add(map);
            dprint("Add final:" + i);
        }
        Thread.sleep(1000);
        dprint("Submit dependencies");
        for (int i = 0; i < loop; i++) {
            listDepend.add(SafeOpt.ofAsync(submitter, i));
            dprint("Add dependecy:" + i);
        }
        for (int i = 0; i < listDepend.size(); i++) {
            SafeOpt<Integer> dep = listDepend.get(i);

            SafeOpt<Integer> mapped = dep.map(m -> { // should be in place, because no more thread slots
                dprint("Start dependency:" + m);
                Thread.sleep(300);
                dprint("End dependency:" + m);
                return m + 10;
            });
            listDepend.set(i, mapped);
        }
        dprint("Mapped dependencies");
        List<Integer> collect = list1.stream().map(m -> m.orNull()).collect(Collectors.toList());
        dprint(collect);
        service.shutdown();

    }

}

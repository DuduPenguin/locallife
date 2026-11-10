import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀压测：N 个不同用户同时抢 M 张券。
 *
 * <p>用 JDK17 自带的 HttpClient，不依赖任何第三方库。
 * <p>运行方式（项目根目录）：
 * <pre>
 *   java script\SeckillBench.java 200
 *   java script\SeckillBench.java 200 sync     # 同步版（默认）
 *   java script\SeckillBench.java 200 lua      # Lua+MQ 版
 * </pre>
 *
 * <p>跑之前先执行 script\prepare_tokens.ps1 准备 300 个用户的登录态。
 */
public class SeckillBench {

    private static final String HOST = "http://localhost:8080";
    private static final long VOUCHER_ID = 2L;
    private static final long USER_START = 1001L;

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        String mode = args.length > 1 ? args[1] : "sync";
        String path = "lua".equalsIgnoreCase(mode)
                ? "/voucher-order/seckill/" + VOUCHER_ID
                : "/voucher-order/seckill-sync/" + VOUCHER_ID;
        String url = HOST + path;

        System.out.printf("target : %s%nthreads: %d%n%n", url, threads);

        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newFixedThreadPool(threads))
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Long> costs = Collections.synchronizedList(new ArrayList<>(threads));
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final long userId = USER_START + i;
            pool.submit(() -> {
                try {
                    start.await();
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .header("authorization", "bench_" + userId)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    long t = System.currentTimeMillis();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    costs.add(System.currentTimeMillis() - t);
                    String body = resp.body();
                    if (body != null && body.contains("\"code\":200")) {
                        ok.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (Exception e) {
                    error.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        long begin = System.currentTimeMillis();
        start.countDown();
        done.await();
        long totalMs = System.currentTimeMillis() - begin;
        pool.shutdown();

        Collections.sort(costs);
        System.out.println("================ RESULT ================");
        System.out.printf("total   : %d ms%n", totalMs);
        System.out.printf("qps     : %.0f req/s%n", threads * 1000.0 / Math.max(totalMs, 1));
        System.out.printf("ok(200) : %d%n", ok.get());
        System.out.printf("fail    : %d%n", fail.get());
        System.out.printf("error   : %d%n", error.get());
        if (!costs.isEmpty()) {
            System.out.printf("P50         : %d ms%n", costs.get(costs.size() / 2));
            System.out.printf("P95         : %d ms%n", costs.get((int) (costs.size() * 0.95)));
            System.out.printf("max         : %d ms%n", costs.get(costs.size() - 1));
        }
        System.out.println("=======================================");
        System.out.println();
        System.out.println("now check db for oversell:");
        System.out.printf("  SELECT stock FROM tb_seckill_voucher WHERE voucher_id=%d;%n", VOUCHER_ID);
        System.out.printf("  SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id=%d;%n", VOUCHER_ID);
    }
}

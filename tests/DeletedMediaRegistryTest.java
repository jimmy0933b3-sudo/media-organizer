package tw.mediaorganizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class DeletedMediaRegistryTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        DeletedMediaRegistry registry = new DeletedMediaRegistry();
        String photo = "content://media/external/images/media/123";
        String video = "content://media/external/video/media/456";
        check(!registry.contains(photo), "not deleted initially");
        registry.record(photo);
        check(registry.contains(photo), "confirmed deletion is visible to scans");
        check(!registry.contains(video), "unrelated video stays available");
        registry.record(photo);
        check(registry.size() == 1, "duplicates do not inflate removal state");
        registry.record(null);
        registry.record("");
        check(registry.size() == 1, "invalid URIs are ignored");

        // Concurrent scanning and verified deletion must share state immediately.
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> writers = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            final int worker = t;
            Thread th = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                for (int j = 0; j < 200; j++) registry.record("content://media/" + worker + "/" + j);
            });
            writers.add(th);
            th.start();
        }
        start.countDown();
        for (Thread th : writers) th.join();
        check(registry.size() == 601, "concurrent deletion acknowledgements are preserved");
        for (int t = 0; t < 3; t++)
            for (int j = 0; j < 200; j++)
                check(registry.contains("content://media/" + t + "/" + j), "media stays hidden across callbacks");
        System.out.println("DeletedMediaRegistryTest PASS (" + checks + " cases)");
    }
}

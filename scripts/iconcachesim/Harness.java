// In the package under test: cacheLoaderPool is package-private, and the crash was about what
// that pool does after shutdown, so the check has to be able to see it.
package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reproduces the reported icon cache crash against the shipped class.
 *
 * The report was a RejectedExecutionException thrown on a pool worker while browsing Modrinth,
 * which killed the app. The cause is that loading one icon is a CYCLE of pool tasks rather than a
 * single task: a cache miss submits a download, and the download submits the read back again to
 * deliver the result. Both submissions run on a pool thread, so once the pool has been shut down
 * the default rejection policy throws where nothing can catch it.
 *
 * So the thing to prove is not that shutdown works. It is that a task submitted FROM A WORKER
 * after shutdown does not throw, which is exactly the sequence the stack trace showed.
 */
public class Harness {
    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        ModIconCache cache = new ModIconCache();

        // 1. The reported sequence: a worker resubmits after the pool has been shut down.
        final AtomicReference<Throwable> thrown = new AtomicReference<>(null);
        final CountDownLatch ran = new CountDownLatch(1);
        cache.cacheLoaderPool.execute(new Runnable() {
            @Override public void run() {
                try {
                    // Shut down from inside the worker, then resubmit, which is what
                    // DownloadImageTask does when the activity went away mid-download.
                    cache.shutdown();
                    cache.cacheLoaderPool.execute(new Runnable() {
                        @Override public void run() { }
                    });
                } catch (Throwable t) {
                    thrown.set(t);
                } finally {
                    ran.countDown();
                }
            }
        });
        check(ran.await(5, TimeUnit.SECONDS), "the worker should have run");
        check(thrown.get() == null,
                "resubmitting after shutdown threw " + thrown.get());

        // 2. Submitting from the outside after shutdown is equally harmless: getImage is called
        // from a LaunchedEffect that can fire while the activity is being torn down.
        Throwable outside = null;
        try {
            cache.cacheLoaderPool.execute(new Runnable() {
                @Override public void run() { }
            });
        } catch (Throwable t) {
            outside = t;
        }
        check(outside == null, "submitting after shutdown from outside threw " + outside);

        // 3. shutdown must not be the interrupting kind: a worker partway through writing the
        // cache file has to be allowed to finish, or the file it leaves is a truncated image.
        ModIconCache second = new ModIconCache();
        final AtomicReference<Boolean> finished = new AtomicReference<>(false);
        final AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        second.cacheLoaderPool.execute(new Runnable() {
            @Override public void run() {
                started.countDown();
                try {
                    Thread.sleep(400);
                    finished.set(true);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                } finally {
                    done.countDown();
                }
            }
        });
        check(started.await(5, TimeUnit.SECONDS), "the long task should have started");
        second.shutdown();
        check(done.await(5, TimeUnit.SECONDS), "the long task should have completed");
        check(!interrupted.get(), "shutdown interrupted work that was already running");
        check(finished.get(), "work in flight at shutdown should be allowed to finish");

        // 4. The pool gives its threads back on its own, which is what the shutdown was added
        // for in the first place. Core threads that never time out are the leak.
        ModIconCache third = new ModIconCache();
        check(third.cacheLoaderPool.allowsCoreThreadTimeOut(),
                "core threads must time out, or a cache whose owner never shuts it down leaks ten");

        System.out.println("checked resubmission after shutdown, outside submission, "
                + "non-interrupting shutdown and core thread timeout");
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("icon cache OK");
    }
}

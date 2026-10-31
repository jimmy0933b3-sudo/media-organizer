package tw.mediaorganizer;

import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI thumbnails are independent from the slower similarity/face scan.
 * A MediaStore thumbnail is requested whenever the scan's cached JPEG does not exist yet.
 */
final class ThumbnailLoader {
    private final Activity activity;
    private final ContentResolver resolver;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> memory;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WeakReference<ImageView>>> waiting =
            new ConcurrentHashMap<>();
    private final java.util.Set<String> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> failures = new ConcurrentHashMap<>();
    private volatile boolean closed;

    ThumbnailLoader(Activity activity) {
        this.activity = activity;
        this.resolver = activity.getContentResolver();
        final int maxKb = (int) Math.max(4096, Math.min(32768,
                Runtime.getRuntime().maxMemory() / 1024 / 12));
        memory = new LruCache<String, Bitmap>(maxKb) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    void bind(ImageView target, Scanner.Item item) {
        String key = item.uri.toString();
        target.setTag(key);
        Bitmap cached = memory.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        if (closed) return;

        Long lastFailure = failures.get(key);
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < 12000) return;
        waiting.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(new WeakReference<>(target));
        if (!running.add(key)) return;

        try {
            workers.execute(() -> {
                Bitmap decoded = null;
                try {
                    if (!closed) decoded = decode(item);
                    if (decoded != null && !closed) {
                        memory.put(key, decoded);
                        failures.remove(key);
                    } else {
                        failures.put(key, System.currentTimeMillis());
                    }
                } catch (Exception | OutOfMemoryError e) {
                    failures.put(key, System.currentTimeMillis());
                } finally {
                    List<WeakReference<ImageView>> targets = waiting.remove(key);
                    running.remove(key);
                    if (targets != null && !closed && !activity.isDestroyed()) {
                        activity.runOnUiThread(() -> {
                            Bitmap bitmap = memory.get(key);
                            if (bitmap == null || bitmap.isRecycled() || closed) return;
                            for (WeakReference<ImageView> reference : targets) {
                                ImageView view = reference.get();
                                if (view != null && key.equals(view.getTag())) view.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            waiting.remove(key);
            running.remove(key);
        }
    }

    private Bitmap decode(Scanner.Item item) throws Exception {
        File thumb = item.thumb;
        if (thumb != null && thumb.isFile()) {
            Bitmap cachedJpeg = BitmapFactory.decodeFile(thumb.getAbsolutePath());
            if (cachedJpeg != null) return cachedJpeg;
        }
        try {
            // MediaStore can return thumbnails before Scanner.analyze() has reached an item,
            // including after a process restart when the previous JPEG cache was removed.
            Bitmap bitmap = resolver.loadThumbnail(item.uri, new Size(256, 256), null);
            if (bitmap != null) return bitmap;
        } catch (Exception ignored) {
            // Some providers do not implement loadThumbnail. Still images get a bounded fallback.
        }
        if (item.video) return null;
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.uri),
                (decoder, info, source) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float scale = Math.min(1f, 256f / Math.max(1, Math.max(width, height)));
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
    }

    void reset() {
        // Existing Bitmap objects may still be displayed; let Android manage their lifecycle.
        memory.evictAll();
        failures.clear();
    }

    void close() {
        closed = true;
        waiting.clear();
        running.clear();
        workers.shutdownNow();
        memory.evictAll();
    }
}

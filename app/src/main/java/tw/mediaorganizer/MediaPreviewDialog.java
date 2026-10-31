package tw.mediaorganizer;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Full-screen, offline MediaStore preview. Swipe UP for next, DOWN for previous.
 * Full-resolution originals are sampled to fit the device instead of loaded into
 * memory at camera resolution. Video posters have a separate native player action.
 */
final class MediaPreviewDialog {
    private final Activity activity;
    private final ArrayList<Scanner.Item> items;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Dialog dialog;
    private ImageView image;
    private ProgressBar progress;
    private TextView counter;
    private TextView name;
    private TextView error;
    private Button previous;
    private Button next;
    private Button playVideo;
    private Bitmap displayedBitmap;
    private volatile int generation;
    private int index;
    private boolean closed;
    private float startX;
    private float startY;

    MediaPreviewDialog(Activity activity, List<Scanner.Item> items, int firstIndex) {
        this.activity = activity;
        this.items = new ArrayList<>(items);
        this.index = Math.max(0, Math.min(this.items.size() - 1, firstIndex));
        this.dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        build();
    }

    void show() {
        if (items.isEmpty()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        showCurrent();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        return view;
    }

    private Button action(String value, Runnable runnable) {
        Button button = new Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setOnClickListener(v -> runnable.run());
        return button;
    }

    private void build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(12), dp(16), dp(12), dp(18));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button close = action("關閉 ✕", dialog::dismiss);
        header.addView(close, new LinearLayout.LayoutParams(dp(108), dp(50)));
        counter = label("", 15);
        counter.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(counter, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(header);

        FrameLayout stage = new FrameLayout(activity);
        stage.setBackgroundColor(Color.BLACK);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("媒體全螢幕預覽，上滑下一張，下滑上一張");
        image.setAdjustViewBounds(false);
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(activity);
        FrameLayout.LayoutParams loadingParams =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        stage.addView(progress, loadingParams);

        error = label("", 14);
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(24), dp(12), dp(24), dp(12));
        error.setVisibility(View.GONE);
        stage.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        stage.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float deltaY = event.getY() - startY;
                    float deltaX = event.getX() - startX;
                    if (Math.abs(deltaY) > dp(65) && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                        navigate(deltaY < 0 ? 1 : -1);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        name = label("", 13);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        name.setPadding(dp(8), dp(9), dp(8), dp(9));
        root.addView(name, new LinearLayout.LayoutParams(-1, -2));

        playVideo = action("▶ 播放這段影片", this::openVideoInPlayer);
        root.addView(playVideo, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout controls = new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        previous = action("↓ 上一張", () -> navigate(-1));
        next = action("↑ 下一張", () -> navigate(1));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(54), 1);
        left.setMargins(0, 0, dp(8), 0);
        controls.addView(previous, left);
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(controls);

        TextView hint = label("上滑下一張  ·  下滑上一張", 12);
        hint.setTextColor(0xffA9BBCD);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            closed = true;
            generation++;
            decoder.shutdownNow();
            releaseImage();
            items.clear();
        });
    }

    private void navigate(int direction) {
        int target = PreviewNavigation.move(index, items.size(), direction);
        if (target != index && target >= 0) {
            index = target;
            showCurrent();
        }
    }

    private void showCurrent() {
        if (closed || items.isEmpty()) return;
        int requestId = ++generation;
        Scanner.Item item = items.get(index);
        counter.setText((index + 1) + " / " + items.size());
        name.setText((item.video ? "影片 · " : "照片 · ") +
                (item.name == null ? "未命名" : item.name));
        previous.setEnabled(index > 0);
        next.setEnabled(index < items.size() - 1);
        playVideo.setVisibility(item.video ? View.VISIBLE : View.GONE);
        error.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        releaseImage();

        try {
            decoder.execute(() -> {
                if (requestId != generation || closed) return;
                Bitmap bitmap = null;
                try {
                    if (item.video) {
                        bitmap = activity.getContentResolver().loadThumbnail(
                                item.uri, new Size(1024, 1024), null);
                    } else {
                        final int maxDimension = Math.min(2304,
                                Math.max(activity.getResources().getDisplayMetrics().widthPixels,
                                        activity.getResources().getDisplayMetrics().heightPixels) * 2);
                        bitmap = ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(activity.getContentResolver(), item.uri),
                                (source, info, ignored) -> {
                                    int width = info.getSize().getWidth();
                                    int height = info.getSize().getHeight();
                                    float scale = Math.min(1f, maxDimension /
                                            (float) Math.max(1, Math.max(width, height)));
                                    source.setTargetSize(Math.max(1, Math.round(width * scale)),
                                            Math.max(1, Math.round(height * scale)));
                                    source.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                                });
                    }
                } catch (Exception | OutOfMemoryError ignored) {
                    // Missing originals and partial media permissions are expected edge cases.
                }
                final Bitmap decoded = bitmap;
                activity.runOnUiThread(() -> {
                    if (closed || requestId != generation || !dialog.isShowing()) {
                        if (decoded != null) decoded.recycle();
                        return;
                    }
                    progress.setVisibility(View.GONE);
                    if (decoded != null) {
                        displayedBitmap = decoded;
                        image.setImageBitmap(decoded);
                    } else {
                        error.setText(item.video
                                ? "影片封面無法讀取，仍可點下方「播放這段影片」。"
                                : "無法讀取這張照片，可能已移動、刪除或沒有讀取權限。");
                        error.setVisibility(View.VISIBLE);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            progress.setVisibility(View.GONE);
        }
    }

    private void releaseImage() {
        image.setImageDrawable(null);
        Bitmap old = displayedBitmap;
        displayedBitmap = null;
        if (old != null && !old.isRecycled()) old.recycle();
    }

    private void openVideoInPlayer() {
        if (items.isEmpty()) return;
        Scanner.Item item = items.get(index);
        if (!item.video) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(item.uri,
                    item.mime != null ? item.mime : "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, "找不到可播放影片的 App，或未取得檔案權限。", Toast.LENGTH_LONG).show();
        }
    }
}

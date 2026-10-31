package tw.mediaorganizer;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.VideoView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Full-screen, offline MediaStore preview. Swipe UP for next, DOWN for previous.
 * Full-resolution originals are sampled to fit the device instead of loaded into
 * memory at camera resolution. Videos play inside this dialog with direct horizontal seek.
 */
final class MediaPreviewDialog {
    private final Activity activity;
    private final ArrayList<Scanner.Item> items;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Dialog dialog;
    private ImageView image;
    private VideoView videoView;
    private TextView playbackTime;
    private boolean videoActive;
    private boolean videoPrepared;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (!closed && videoActive && videoPrepared) {
                updatePlaybackTime();
                clockHandler.postDelayed(this, 750L);
            }
        }
    };
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
        videoView = new VideoView(activity);
        videoView.setVisibility(View.GONE);
        stage.addView(videoView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(activity);
        FrameLayout.LayoutParams loadingParams =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        stage.addView(progress, loadingParams);

        error = label("", 14);
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(24), dp(12), dp(24), dp(12));
        error.setVisibility(View.GONE);
        stage.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // Transparent touch surface, drawn above the video. Never dim or fade
        // the picture while seeking; a gesture directly updates playback time.
        View gestures = new View(activity);
        gestures.setContentDescription("上滑下一個、下滑上一個；影片播放中向右快轉、向左倒退");
        gestures.setBackgroundColor(Color.TRANSPARENT);
        gestures.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dy = event.getY() - startY;
                    float dx = event.getX() - startX;
                    if (Math.abs(dx) > Math.abs(dy) * 1.2f
                            && Math.abs(dx) > dp(56) && videoActive && videoPrepared) {
                        seekBySwipe(dx);
                    } else if (Math.abs(dy) > dp(65)
                            && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        navigate(dy < 0 ? 1 : -1);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
        stage.addView(gestures, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        name = label("", 13);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        name.setPadding(dp(8), dp(9), dp(8), dp(9));
        root.addView(name, new LinearLayout.LayoutParams(-1, -2));

        playVideo = action("▶ 播放這段影片", this::toggleVideo);
        root.addView(playVideo, new LinearLayout.LayoutParams(-1, dp(48)));
        playbackTime = label("", 13);
        playbackTime.setTextColor(0xffC6D8E8);
        playbackTime.setGravity(Gravity.CENTER);
        playbackTime.setVisibility(View.GONE);
        root.addView(playbackTime, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout controls = new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        previous = action("↓ 上一張", () -> navigate(-1));
        next = action("↑ 下一張", () -> navigate(1));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(54), 1);
        left.setMargins(0, 0, dp(8), 0);
        controls.addView(previous, left);
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(controls);

        TextView hint = label("上滑下一個 · 下滑上一個；影片向左倒退、向右快轉", 12);
        hint.setTextColor(0xffA9BBCD);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            closed = true;
            generation++;
            decoder.shutdownNow();
            stopVideo();
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
        stopVideo();
        Scanner.Item item = items.get(index);
        counter.setText((index + 1) + " / " + items.size());
        name.setText((item.video ? "影片 · " : "照片 · ") +
                (item.name == null ? "未命名" : item.name));
        previous.setEnabled(index > 0);
        next.setEnabled(index < items.size() - 1);
        playVideo.setVisibility(item.video ? View.VISIBLE : View.GONE);
        playVideo.setText("▶ 播放這段影片");
        playbackTime.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
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

    private void toggleVideo() {
        if (items.isEmpty() || !items.get(index).video || closed) return;
        if (!videoActive) {
            startVideo();
        } else if (videoPrepared) {
            if (videoView.isPlaying()) {
                videoView.pause();
                playVideo.setText("▶ 繼續播放");
            } else {
                if (videoView.getDuration() > 0
                        && videoView.getCurrentPosition() >= videoView.getDuration() - 250) {
                    videoView.seekTo(0);
                }
                videoView.start();
                playVideo.setText("❚❚ 暫停");
            }
            updatePlaybackTime();
        }
    }

    private void startVideo() {
        Scanner.Item item = items.get(index);
        if (!item.video || closed) return;
        // Invalidate any thumbnail decode running on a background worker.
        generation++;
        videoActive = true;
        videoPrepared = false;
        videoView.setVisibility(View.VISIBLE);
        image.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        playbackTime.setText("影片載入中…");
        playbackTime.setVisibility(View.VISIBLE);
        playVideo.setEnabled(false);
        final int videoIndex = index;
        final int requestId = generation;
        videoView.setOnPreparedListener(mediaPlayer -> {
            if (closed || !videoActive || videoIndex != index || generation != requestId) return;
            videoPrepared = true;
            progress.setVisibility(View.GONE);
            playVideo.setEnabled(true);
            playVideo.setText("❚❚ 暫停");
            videoView.start();
            clockHandler.removeCallbacks(clockTicker);
            clockHandler.post(clockTicker);
            updatePlaybackTime();
        });
        videoView.setOnCompletionListener(mediaPlayer -> {
            if (!closed && videoActive && videoIndex == index && generation == requestId) {
                playVideo.setText("▶ 重新播放");
                updatePlaybackTime();
            }
        });
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            if (!closed && videoIndex == index && generation == requestId) {
                stopVideo();
                progress.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);
                error.setText("影片無法播放，可能是格式不支援或檔案讀取權限不足。");
                error.setVisibility(View.VISIBLE);
            }
            return true;
        });
        try {
            videoView.setVideoURI(item.uri);
        } catch (RuntimeException e) {
            stopVideo();
            progress.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            error.setText("無法開啟影片：" + e.getClass().getSimpleName());
            error.setVisibility(View.VISIBLE);
        }
    }

    private void seekBySwipe(float deltaX) {
        if (!videoActive || !videoPrepared) return;
        int duration = videoView.getDuration();
        if (duration <= 0) return;
        int target = VideoSwipeSeek.targetMs(videoView.getCurrentPosition(),
                duration, deltaX, activity.getResources().getDisplayMetrics().density);
        // No grey overlay or alpha animation; move the playhead immediately.
        videoView.seekTo(target);
        updatePlaybackTime(target, duration);
    }

    private void updatePlaybackTime() {
        if (!videoActive || !videoPrepared) return;
        updatePlaybackTime(videoView.getCurrentPosition(), videoView.getDuration());
    }

    private void updatePlaybackTime(int positionMs, int durationMs) {
        playbackTime.setText(formatTime(positionMs) + " / " + formatTime(durationMs));
    }

    private String formatTime(int millis) {
        int seconds = Math.max(0, millis / 1000);
        if (seconds >= 3600) return String.format(java.util.Locale.TAIWAN,
                "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        return String.format(java.util.Locale.TAIWAN, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void stopVideo() {
        clockHandler.removeCallbacks(clockTicker);
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (RuntimeException ignored) {
                // Not all video codecs initialize before the user changes items.
            }
            videoView.setVisibility(View.GONE);
        }
        videoActive = false;
        videoPrepared = false;
        if (playVideo != null) playVideo.setEnabled(true);
        if (playbackTime != null) playbackTime.setVisibility(View.GONE);
    }
}

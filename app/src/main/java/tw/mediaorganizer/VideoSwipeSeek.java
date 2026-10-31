package tw.mediaorganizer;

/**
 * Converts a horizontal swipe into an immediate video seek.
 * Right: forward; left: rewind. Every 88dp corresponds to 10 seconds.
 * No alpha changes, scrims or visual transition are required.
 */
final class VideoSwipeSeek {
    private VideoSwipeSeek() {}

    static int targetMs(int currentMs, int durationMs, float deltaPx, float density) {
        int safeCurrent = Math.max(0, currentMs);
        if (durationMs <= 0) return safeCurrent;
        int duration = Math.max(0, durationMs);
        safeCurrent = Math.min(duration, safeCurrent);
        float dp = Math.abs(deltaPx) / Math.max(0.5f, density);
        if (dp < 56f) return safeCurrent;

        long steps = Math.max(1L, Math.round(dp / 88f));
        long delta = Math.min(steps, 8640L) * 10000L;
        long candidate = (long) safeCurrent + (deltaPx >= 0 ? delta : -delta);
        return (int) Math.min(duration, Math.max(0L, candidate));
    }
}

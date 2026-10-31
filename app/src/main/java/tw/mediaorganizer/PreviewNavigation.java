package tw.mediaorganizer;

/** Bounds-safe navigation for a vertically swiped photo/video preview. */
final class PreviewNavigation {
    private PreviewNavigation() {}

    static int move(int current, int total, int direction) {
        if (total <= 0) return -1;
        long candidate = (long) current + (long) direction;
        return (int) Math.max(0L, Math.min((long) total - 1L, candidate));
    }
}

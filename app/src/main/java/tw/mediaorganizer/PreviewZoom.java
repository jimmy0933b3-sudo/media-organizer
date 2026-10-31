package tw.mediaorganizer;

/** Pure helpers used by the photo and video pinch-zoom preview. */
final class PreviewZoom {
    static final float MIN_SCALE = 1f;
    static final float MAX_SCALE = 4f;

    private PreviewZoom() {}

    static float clampScale(float zoom) {
        if (Float.isNaN(zoom) || Float.isInfinite(zoom)) return MIN_SCALE;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, zoom));
    }

    static float clampOffset(float offset, int viewSize, float scale) {
        if (Float.isNaN(offset) || Float.isInfinite(offset)) return 0f;
        float extra = Math.max(0f, viewSize * (clampScale(scale) - 1f) / 2f);
        return Math.max(-extra, Math.min(extra, offset));
    }
}

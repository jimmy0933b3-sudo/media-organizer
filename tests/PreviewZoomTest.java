package tw.mediaorganizer;

public final class PreviewZoomTest {
    private static int count;
    private static void equals(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.0001f)
            throw new AssertionError("expected " + expected + " got " + actual);
        count++;
    }
    public static void main(String[] args) {
        equals(1f, PreviewZoom.clampScale(0.4f));
        equals(1f, PreviewZoom.clampScale(Float.NaN));
        equals(1f, PreviewZoom.clampScale(Float.POSITIVE_INFINITY));
        equals(1.75f, PreviewZoom.clampScale(1.75f));
        equals(4f, PreviewZoom.clampScale(9f));
        equals(0f, PreviewZoom.clampOffset(1000f, 1000, 1f));
        equals(500f, PreviewZoom.clampOffset(900f, 1000, 2f));
        equals(-500f, PreviewZoom.clampOffset(-900f, 1000, 2f));
        equals(250f, PreviewZoom.clampOffset(250f, 1000, 2f));
        equals(1500f, PreviewZoom.clampOffset(5000f, 1000, 4f));
        equals(-1500f, PreviewZoom.clampOffset(-5000f, 1000, 4f));
        equals(0f, PreviewZoom.clampOffset(Float.NaN, 800, 2f));
        equals(0f, PreviewZoom.clampOffset(100f, 0, 3f));
        System.out.println("PreviewZoomTest PASS (" + count + " cases)");
    }
}

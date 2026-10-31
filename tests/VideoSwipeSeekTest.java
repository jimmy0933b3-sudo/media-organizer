package tw.mediaorganizer;

public final class VideoSwipeSeekTest {
    private static int tests = 0;
    private static void expect(int expected, int current, int duration, float px, float density) {
        int actual = VideoSwipeSeek.targetMs(current, duration, px, density);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but got " + actual
                + "; current=" + current + ", duration=" + duration
                + ", dx=" + px + ", density=" + density);
        }
        tests++;
    }

    public static void main(String[] args) {
        expect(30000, 30000, 120000, 50f, 1f); // Ignore accidental small movement.
        expect(40000, 30000, 120000, 90f, 1f); // Right = +10 sec.
        expect(20000, 30000, 120000, -90f, 1f); // Left = -10 sec.
        expect(60000, 30000, 120000, 264f, 1f); // Long swipe = +30 sec.
        expect(0, 2000, 120000, -500f, 1f); // Clamp at start.
        expect(120000, 115000, 120000, 500f, 1f); // Clamp at end.
        expect(30000, 30000, 120000, 100f, 2f); // Threshold scales with density.
        expect(40000, 30000, 120000, 180f, 2f);
        expect(30000, 30000, 0, 500f, 1f); // Not prepared.
        expect(0, -1000, 120000, -100f, 1f);
        expect(120000, 120000, 120000, 100f, 1f);
        System.out.println("VideoSwipeSeekTest PASS (" + tests + " cases)");
    }
}

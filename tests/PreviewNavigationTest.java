package tw.mediaorganizer;

public final class PreviewNavigationTest {
    private static int assertions;
    private static void assertPosition(int expected, int current, int total, int direction) {
        int actual = PreviewNavigation.move(current, total, direction);
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual
                    + ", current=" + current + ", total=" + total + ", direction=" + direction);
        }
        assertions++;
    }

    public static void main(String[] args) {
        assertPosition(-1, 0, 0, 1);
        assertPosition(0, 0, 1, 1);
        assertPosition(0, 0, 1, -1);
        assertPosition(1, 0, 3, 1);  // up -> next
        assertPosition(0, 1, 3, -1); // down -> previous
        assertPosition(2, 2, 3, 1);  // last item doesn't wrap
        assertPosition(0, 0, 3, -1); // first item doesn't wrap
        assertPosition(2, 1, 3, 100);
        assertPosition(0, 1, 3, -100);
        assertPosition(2, 1, 3, 1);
        System.out.println("PreviewNavigationTest PASS (" + assertions + " cases)");
    }
}

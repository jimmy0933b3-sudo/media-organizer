package tw.mediaorganizer;

public final class MediaManageFlowTest {
    private static int checks;
    private static void test(boolean actual, boolean expected, String message) {
        if (actual != expected) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        test(MediaManageFlow.supported(30), false, "Android 11 cannot request MANAGE_MEDIA");
        test(MediaManageFlow.supported(31), true, "Android 12 supports MANAGE_MEDIA");
        test(MediaManageFlow.supported(35), true, "Android 15 supports MANAGE_MEDIA");
        test(MediaManageFlow.offerDuringDelete(30, false, false), false, "no permission prompt on Android 11");
        test(MediaManageFlow.offerDuringDelete(31, false, false), true, "first delete offers one-time access");
        test(MediaManageFlow.offerDuringDelete(35, false, true), false, "user declined once");
        test(MediaManageFlow.offerDuringDelete(35, true, false), false, "already granted");
        test(MediaManageFlow.useSystemFallback(30, false), true, "Android 11 always uses system dialog");
        test(MediaManageFlow.useSystemFallback(31, false), true, "not granted uses Android consent");
        test(MediaManageFlow.useSystemFallback(35, true), false, "granted can avoid repeated prompts");
        if (MediaManageFlow.systemDeleteBatchLimit() != 100) throw new AssertionError("batch safety");
        checks++;
        System.out.println("MediaManageFlowTest PASS (" + checks + " cases)");
    }
}

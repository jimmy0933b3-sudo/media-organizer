package tw.mediaorganizer;

/** Pure decision logic for the optional one-time Android media management access. */
final class MediaManageFlow {
    static final int MIN_API = 31;

    private MediaManageFlow() { }

    static boolean supported(int sdk) { return sdk >= MIN_API; }

    static boolean offerDuringDelete(int sdk, boolean granted, boolean shownBefore) {
        return supported(sdk) && !granted && !shownBefore;
    }

    static boolean useSystemFallback(int sdk, boolean granted) {
        return !supported(sdk) || !granted;
    }

    static int systemDeleteBatchLimit() { return 100; }
}

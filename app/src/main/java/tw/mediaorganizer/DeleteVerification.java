package tw.mediaorganizer;

/** A successful Android result is not proof unless the MediaStore row is gone. */
final class DeleteVerification {
    private DeleteVerification() {}

    static boolean verified(boolean systemApproved, boolean querySucceeded, boolean rowStillExists) {
        return systemApproved && querySucceeded && !rowStillExists;
    }
}

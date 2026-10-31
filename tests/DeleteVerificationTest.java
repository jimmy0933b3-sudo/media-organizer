package tw.mediaorganizer;

public final class DeleteVerificationTest {
    private static int checks;
    private static void expect(boolean expected, boolean approved, boolean querySucceeded, boolean exists) {
        boolean actual=DeleteVerification.verified(approved,querySucceeded,exists);
        if(actual!=expected)throw new AssertionError("expected="+expected+
            " result="+actual+" approved="+approved+" query="+querySucceeded+" exists="+exists);
        checks++;
    }
    public static void main(String[] args) {
        expect(true,true,true,false);   // System approved and URI is gone.
        expect(false,true,true,true);   // System returned OK, file still exists.
        expect(false,true,false,false); // Query failed; never claim success.
        expect(false,false,true,false); // User cancelled; never claim success.
        expect(false,false,true,true);
        expect(false,false,false,false);
        System.out.println("DeleteVerificationTest PASS ("+checks+" cases)");
    }
}

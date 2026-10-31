package tw.mediaorganizer;
public final class PlayerProgressStyleTest {
 private static int count;
 private static void test(boolean ok,String name) {if(!ok)throw new AssertionError(name);count++;}
 public static void main(String[] args) {
  test(PlayerProgressStyle.scale(.5f)==.8f,"minimum size");
  test(PlayerProgressStyle.scale(3.0f)==2.5f,"maximum size");
  test(PlayerProgressStyle.scale(Float.NaN)==1f,"NaN fallback");
  test(PlayerProgressStyle.scale(Float.POSITIVE_INFINITY)==1f,"infinity fallback");
  test(PlayerProgressStyle.width(.2f)==.6f,"minimum width");
  test(PlayerProgressStyle.width(2f)==1f,"maximum width");
  test(PlayerProgressStyle.width(Float.NaN)==.96f,"width fallback");
  test(PlayerProgressStyle.trackDp(1f)==3f,"default thin track");
  test(PlayerProgressStyle.thumbDp(1f)==10f,"default thumb");
  test(PlayerProgressStyle.thumbDp(2f)==20f,"scaled thumb");
  System.out.println("PlayerProgressStyleTest PASS ("+count+" cases)");
 }
}

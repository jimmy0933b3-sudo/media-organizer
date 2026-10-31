package tw.mediaorganizer;
public final class PlayerGesturePolicyTest {
 private static int n;
 private static void check(boolean condition,String message){
  if(!condition)throw new AssertionError(message);n++;
 }
 public static void main(String[] args){
  check(PlayerGesturePolicy.centerPlayTap(500,440,1000,1000),"center tap");
  check(!PlayerGesturePolicy.centerPlayTap(500,900,1000,1000),"bottom progress zone not play toggle");
  check(!PlayerGesturePolicy.centerPlayTap(100,440,1000,1000),"left edge not play toggle");
  check(!PlayerGesturePolicy.centerPlayTap(900,440,1000,1000),"right edge not play toggle");
  check(!PlayerGesturePolicy.centerPlayTap(0,0,0,0),"invalid dimensions");
  check(PlayerGesturePolicy.horizontalSeek(true,false,80,10,18),"horizontal seeks");
  check(!PlayerGesturePolicy.horizontalSeek(false,false,80,10,18),"photos not seek");
  check(!PlayerGesturePolicy.horizontalSeek(true,true,80,10,18),"pinches not seek");
  check(!PlayerGesturePolicy.horizontalSeek(true,false,20,180,18),"vertical reserved for navigation");
  check(PlayerGesturePolicy.seekTarget(10000,120000,100)==16500,"swipe right seeks forward");
  check(PlayerGesturePolicy.seekTarget(10000,120000,-200)==0,"swipe left clamped to beginning");
  check(PlayerGesturePolicy.seekTarget(115000,120000,9999)==120000,"seek clamped to end");
  check(PlayerGesturePolicy.seekTarget(200,0,100)==0,"invalid duration safe");
  boolean[] mixed={false,true,false,true,true,false};
  check(PlayerGesturePolicy.adjacentVideo(mixed,1,1)==3,"skip photo next video");
  check(PlayerGesturePolicy.adjacentVideo(mixed,4,-1)==3,"previous video");
  check(PlayerGesturePolicy.adjacentVideo(mixed,4,1)==-1,"no following video");
  check(PlayerGesturePolicy.adjacentVideo(mixed,1,-1)==-1,"no earlier video");
  check(PlayerGesturePolicy.adjacentVideo(mixed,-1,1)==-1,"invalid index");
  check(PlayerGesturePolicy.autoHide(true,true,true),"playing video hides controls");
  check(!PlayerGesturePolicy.autoHide(true,true,false),"paused video shows controls");
  check(!PlayerGesturePolicy.autoHide(false,true,true),"photos controls stay");
  System.out.println("PlayerGesturePolicyTest PASS ("+n+" checks)");
 }
}

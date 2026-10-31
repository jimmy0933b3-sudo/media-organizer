package tw.mediaorganizer;

public final class FastScrollMathTest {
 private static int count=0;
 private static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);count++;}
 private static void eq(int expected,int actual,String msg){ok(expected==actual,msg+" expected "+expected+" got "+actual);}
 private static void close(float expected,float actual,String msg){ok(Math.abs(expected-actual)<0.001f,msg);}
 public static void main(String[] args){
  ok(!FastScrollMath.scrollable(400,500),"content shorter than viewport");
  ok(!FastScrollMath.scrollable(500,500),"equal height");
  ok(FastScrollMath.scrollable(2000,500),"long list");
  eq(100,FastScrollMath.thumbLength(2000,500,400,52),"proportional thumb");
  eq(52,FastScrollMath.thumbLength(100000,500,400,52),"minimum drag target");
  eq(400,FastScrollMath.thumbLength(200,500,400,52),"no scroll full rail");
  eq(0,FastScrollMath.thumbLength(1000,500,0,52),"zero layout");
  close(0.5f,FastScrollMath.fractionForOffset(750,2000,500),"middle position");
  close(1f,FastScrollMath.fractionForOffset(3000,2000,500),"bottom clamp");
  close(0f,FastScrollMath.fractionForOffset(-10,2000,500),"top clamp");
  eq(750,FastScrollMath.offsetForFraction(.5f,2000,500),"middle scroll");
  eq(1500,FastScrollMath.offsetForFraction(2f,2000,500),"bottom scroll");
  eq(0,FastScrollMath.offsetForFraction(-1f,2000,500),"top scroll");
  close(0.5f,FastScrollMath.fractionForDrag(172,12,400,100,10),"thumb drag midpoint");
  close(1f,FastScrollMath.fractionForDrag(4000,12,400,100,10),"drag below track clamp");
  close(0f,FastScrollMath.fractionForDrag(-100,12,400,100,10),"drag above track clamp");
  close(0f,FastScrollMath.fractionForDrag(100,12,100,100,50),"no travel");
  close(0f,FastScrollMath.clamp01(Float.NaN),"invalid fraction");
  System.out.println("FastScrollMathTest PASS ("+count+" checks)");
 }
}

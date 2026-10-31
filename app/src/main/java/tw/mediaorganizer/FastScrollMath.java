package tw.mediaorganizer;

/** Clamped fast-scroll geometry, kept separate from the Android view for tests. */
final class FastScrollMath {
 private FastScrollMath() {}

 static boolean scrollable(int contentRange, int viewportExtent) {
  return viewportExtent > 0 && contentRange > viewportExtent;
 }

 static float clamp01(float value) {
  if(Float.isNaN(value))return 0f;
  return Math.max(0f,Math.min(1f,value));
 }

 static int thumbLength(int contentRange,int viewportExtent,int trackLength,int minimum) {
  if(trackLength<=0)return 0;
  if(!scrollable(contentRange,viewportExtent))return trackLength;
  long proportional=(long)viewportExtent*trackLength/Math.max(contentRange,1);
  return (int)Math.max(0,Math.min(trackLength,Math.max(minimum,proportional)));
 }

 static float fractionForOffset(int offset,int contentRange,int viewportExtent) {
  if(!scrollable(contentRange,viewportExtent))return 0f;
  return clamp01((float)offset/(contentRange-viewportExtent));
 }

 static int offsetForFraction(float fraction,int contentRange,int viewportExtent) {
  if(!scrollable(contentRange,viewportExtent))return 0;
  return Math.round(clamp01(fraction)*(contentRange-viewportExtent));
 }

 static float fractionForDrag(float pointerY,float trackTop,int trackLength,
                              int thumbLength,float touchWithinThumb) {
  int travel=Math.max(0,trackLength-thumbLength);
  if(travel==0)return 0f;
  return clamp01((pointerY-trackTop-touchWithinThumb)/travel);
 }
}

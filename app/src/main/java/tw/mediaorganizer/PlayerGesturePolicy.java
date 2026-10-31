package tw.mediaorganizer;

/** Gesture rules and video navigation shared with the my-player style viewer. */
final class PlayerGesturePolicy {
 private PlayerGesturePolicy() { }

 static boolean centerPlayTap(float x,float y,float width,float height) {
  return width>0&&height>0&&x>=width*.25f&&x<=width*.75f
      &&y>=height*.28f&&y<=height*.64f;
 }

 static boolean horizontalSeek(boolean video,boolean multiFinger,float dx,float dy,float threshold) {
  return video&&!multiFinger&&Math.abs(dx)>threshold&&Math.abs(dx)>Math.abs(dy)*1.15f;
 }

 static int seekTarget(int startMs,int durationMs,float dx) {
  if(durationMs<=0)return 0;
  long position=(long)Math.max(0,startMs)+Math.round(dx*65f);
  return (int)Math.max(0,Math.min((long)durationMs,position));
 }

 static int adjacentVideo(boolean[] videoFlags,int current,int direction) {
  if(videoFlags==null||current<0||current>=videoFlags.length||direction==0)return -1;
  for(int i=current+(direction>0?1:-1);i>=0&&i<videoFlags.length;i+=direction>0?1:-1)
   if(videoFlags[i])return i;
  return -1;
 }

 static boolean autoHide(boolean video,boolean prepared,boolean playing) {
  return video&&prepared&&playing;
 }
}

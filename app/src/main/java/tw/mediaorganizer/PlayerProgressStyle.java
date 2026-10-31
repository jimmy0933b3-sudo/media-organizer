package tw.mediaorganizer;

/** Follows my-player's independently configurable progress-bar size and width. */
final class PlayerProgressStyle {
 static final float DEFAULT_SIZE=1f;
 static final float DEFAULT_WIDTH=.96f;
 private PlayerProgressStyle(){}
 static float clamp(float f,float lower,float upper,float fallback){
  return Float.isNaN(f)||Float.isInfinite(f)?fallback:Math.max(lower,Math.min(upper,f));
 }
 static float scale(float f){return clamp(f,.8f,2.5f,DEFAULT_SIZE);}
 static float width(float f){return clamp(f,.6f,1f,DEFAULT_WIDTH);}
 static float trackDp(float f){return 3f*scale(f);}
 static float thumbDp(float f){return 10f*scale(f);}
}

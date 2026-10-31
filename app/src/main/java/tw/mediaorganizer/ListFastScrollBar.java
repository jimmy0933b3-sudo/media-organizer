package tw.mediaorganizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A dedicated, touchable right-hand rail for long result lists.
 * It does not overlap media rows, so thumbnails and checkboxes remain clickable.
 */
final class ListFastScrollBar extends View {
 private final RecyclerView list;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF rect=new RectF();
 private final int trackColor=0xff304357;
 private final int thumbColor=0xff5DE1C4;
 private final int pressedColor=0xff9EF5E6;
 private final int inset,railWidth,thumbWidth,minThumb;
 private boolean dragging;
 private float touchWithinThumb;

 ListFastScrollBar(Context context,RecyclerView recycler) {
  super(context);
  list=recycler;
  float density=getResources().getDisplayMetrics().density;
  inset=Math.round(12*density);
  railWidth=Math.max(2,Math.round(3*density));
  thumbWidth=Math.max(6,Math.round(9*density));
  minThumb=Math.max(36,Math.round(52*density));
  setContentDescription("結果清單快速捲動滑條，可拖曳至頂端或底端");
  setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  list.addOnScrollListener(new RecyclerView.OnScrollListener(){
   @Override public void onScrolled(RecyclerView recycler,int dx,int dy){
    invalidate();
   }
   @Override public void onScrollStateChanged(RecyclerView recycler,int state){
    invalidate();
   }
  });
  list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->invalidate());
  recycler.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver(){
   @Override public void onChanged(){postInvalidate();}
   @Override public void onItemRangeInserted(int positionStart,int itemCount){postInvalidate();}
   @Override public void onItemRangeRemoved(int positionStart,int itemCount){postInvalidate();}
   @Override public void onItemRangeChanged(int positionStart,int itemCount){postInvalidate();}
   @Override public void onItemRangeMoved(int fromPosition,int toPosition,int itemCount){postInvalidate();}
  });
 }

 private int range(){return list.computeVerticalScrollRange();}
 private int extent(){return list.computeVerticalScrollExtent();}
 private int offset(){return list.computeVerticalScrollOffset();}
 private int trackLength(){return Math.max(0,getHeight()-inset*2);}
 private int thumbLength(){return FastScrollMath.thumbLength(range(),extent(),trackLength(),minThumb);}
 private boolean scrollable(){return FastScrollMath.scrollable(range(),extent());}
 private float thumbTop(){
  int thumb=thumbLength();
  return inset+(trackLength()-thumb)*FastScrollMath.fractionForOffset(offset(),range(),extent());
 }

 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  if(!scrollable()||trackLength()<=0)return;
  float cx=getWidth()/2f;
  float trackLeft=cx-railWidth/2f,trackRight=cx+railWidth/2f;
  paint.setColor(trackColor);
  rect.set(trackLeft,inset,trackRight,getHeight()-inset);
  canvas.drawRoundRect(rect,railWidth/2f,railWidth/2f,paint);
  float top=thumbTop();
  paint.setColor(dragging?pressedColor:thumbColor);
  rect.set(cx-thumbWidth/2f,top,cx+thumbWidth/2f,top+thumbLength());
  canvas.drawRoundRect(rect,thumbWidth/2f,thumbWidth/2f,paint);
 }

 @Override public boolean onTouchEvent(MotionEvent event){
  if(!scrollable())return false;
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    float top=thumbTop(),height=thumbLength(),y=event.getY();
    dragging=true;
    touchWithinThumb=y>=top&&y<=top+height?y-top:height/2f;
    getParent().requestDisallowInterceptTouchEvent(true);
    updateScroll(y);
    invalidate();
    return true;
   case MotionEvent.ACTION_MOVE:
    if(!dragging)return false;
    updateScroll(event.getY());
    return true;
   case MotionEvent.ACTION_UP:
    if(!dragging)return false;
    updateScroll(event.getY());
    dragging=false;
    invalidate();
    performClick();
    return true;
   case MotionEvent.ACTION_CANCEL:
    dragging=false;
    invalidate();
    return true;
   default:
    return super.onTouchEvent(event);
  }
 }

 private void updateScroll(float y){
  int contentRange=range(),viewport=extent();
  float fraction=FastScrollMath.fractionForDrag(
    y,inset,trackLength(),thumbLength(),touchWithinThumb);
  int target=FastScrollMath.offsetForFraction(fraction,contentRange,viewport);
  int delta=target-offset();
  if(delta!=0)list.scrollBy(0,delta);
  invalidate();
 }
 @Override public boolean performClick(){
  super.performClick();
  return true;
 }
 @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
  super.onInitializeAccessibilityNodeInfo(info);
  info.setClassName("android.widget.SeekBar");
  info.setScrollable(scrollable());
  if(scrollable()){
   info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
     AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
     0f,100f,100f*FastScrollMath.fractionForOffset(offset(),range(),extent())));
   info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
   info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
  }
 }
 @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
  if(scrollable()&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||
                   action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){
   list.scrollBy(0,(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*
                  Math.max(extent()/2,Math.round(72*getResources().getDisplayMetrics().density)));
   invalidate();
   return true;
  }
  return super.performAccessibilityAction(action,args);
 }
}

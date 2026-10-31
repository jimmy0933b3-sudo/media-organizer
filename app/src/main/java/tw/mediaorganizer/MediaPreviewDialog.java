package tw.mediaorganizer;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Fullscreen photo/video viewer with my-player-style controls and touch gestures.
 * Video starts on entry, hides overlays after two seconds of playback, offers
 * a scrubbable timeline and skips photos with previous/next video buttons.
 * No media is modified by the viewer.
 */
final class MediaPreviewDialog {
    private static final int ACCENT=0xff92A9FF;
    private final Activity activity;
    private Dialog dialog;
    private final ArrayList<Scanner.Item> items;
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private FrameLayout root,stage;
    private LinearLayout topBar,bottomBar;
    private ImageView image;
    private VideoView video;
    private ProgressBar loading;
    private TextView title,counter,error,seekHint,timeElapsed,timeTotal;
    private Button zoomReset;
    private SeekBar timeline;
    private Bitmap displayedBitmap;
    private boolean closed,videoActive,videoPrepared,draggingTimeline,draggingSeek,controlsShown=true;
    private boolean multiFingerGesture,landscape;
    private float zoomScale=1f,zoomX,zoomY,centroidX,centroidY;
    private float touchX,touchY,lastMotionX,lastMotionY;
    private int swipeOriginMs;
    private int index,generation,originalOrientation;
    private long previousTapTime;
    private float previousTapX,previousTapY;
    private Runnable pendingZoomTap;
    private ScaleGestureDetector zoomDetector;

    private final Runnable hideControls=()->{
        if(!closed&&dialog.isShowing()&&PlayerGesturePolicy.autoHide(
                currentIsVideo(),videoPrepared,video!=null&&video.isPlaying())) {
            controlsShown=false;
            updateControlVisibility();
        }
    };
    private final Runnable clockTicker=new Runnable(){
        @Override public void run(){
            if(closed||!videoActive||!videoPrepared)return;
            if(!draggingTimeline&&!draggingSeek)updateTimeline();
            if(!video.isPlaying()&& !controlsShown)revealControls();
            handler.postDelayed(this,250L);
        }
    };

    MediaPreviewDialog(Activity activity,List<Scanner.Item> collection,int firstIndex){
        this.activity=activity;
        items=new ArrayList<>(collection);
        index=Math.max(0,Math.min(Math.max(0,items.size()-1),firstIndex));
        originalOrientation=activity.getRequestedOrientation();
        preferences=activity.getSharedPreferences("media_organizer_playback",Activity.MODE_PRIVATE);
        dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        build();
    }

    void show(){
        if(items.isEmpty())return;
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // Follow my-player: allow sensor-based portrait/landscape only while viewing.
        try{activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);}
        catch(RuntimeException ignored){}
        showCurrent();
    }

    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){
        TextView v=new TextView(activity);
        v.setText(value);v.setTextSize(size);v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(activity);
        b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(4),0,dp(4),0);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private GradientDrawable overlay(boolean top){
        return new GradientDrawable(top?
            GradientDrawable.Orientation.TOP_BOTTOM:
            GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{0xeb000000,0x00000000});
    }
    private void build(){
        root=new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        stage=new FrameLayout(activity);
        stage.setBackgroundColor(Color.BLACK);
        stage.setClipChildren(true);
        stage.setClipToPadding(true);
        image=new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("照片與影片預覽，單指上下切換，雙指縮放");
        stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoView(activity);
        video.setVisibility(View.GONE);
        stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
        loading=new ProgressBar(activity);
        stage.addView(loading,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
        error=text("",14);error.setGravity(Gravity.CENTER);
        error.setPadding(dp(20),dp(16),dp(20),dp(16));
        error.setVisibility(View.GONE);
        stage.addView(error,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        root.addView(stage,new FrameLayout.LayoutParams(-1,-1));

        zoomDetector=new ScaleGestureDetector(activity,
                new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                float factor=detector.getScaleFactor();
                if(Float.isNaN(factor)||Float.isInfinite(factor))return true;
                float next=Math.max(1f,Math.min(5f,zoomScale*factor));
                if(Math.abs(next-zoomScale)<.001f)return true;
                float centerX=detector.getFocusX()-stage.getWidth()/2f;
                float centerY=detector.getFocusY()-stage.getHeight()/2f;
                zoomX=(zoomX-centerX)*(next/zoomScale)+centerX;
                zoomY=(zoomY-centerY)*(next/zoomScale)+centerY;
                zoomScale=next;
                applyZoom();
                return true;
            }
        });
        View gestures=new View(activity);
        gestures.setContentDescription("影片中央點按播放或暫停，左右滑動快轉或倒退，上下滑切換，雙指縮放");
        gestures.setOnTouchListener((v,e)->onGesture(e));
        stage.addView(gestures,new FrameLayout.LayoutParams(-1,-1));

        seekHint=text("",15);seekHint.setGravity(Gravity.CENTER);
        seekHint.setPadding(dp(16),dp(8),dp(16),dp(8));
        GradientDrawable hintBg=new GradientDrawable();
        hintBg.setColor(0xcc000000);hintBg.setCornerRadius(dp(16));seekHint.setBackground(hintBg);
        seekHint.setVisibility(View.GONE);
        stage.addView(seekHint,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));

        topBar=new LinearLayout(activity);topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(5),dp(4),dp(6),dp(6));topBar.setBackground(overlay(true));
        Button back=button("‹",dialog::dismiss);back.setTextSize(32);
        back.setContentDescription("返回清單");
        topBar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout titleBox=new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        title=text("",15);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBox.addView(title);
        counter=text("",12);counter.setTextColor(0xffCDD7E8);
        titleBox.addView(counter);
        topBar.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        zoomReset=button("1×",this::resetZoom);
        zoomReset.setContentDescription("重設縮放");
        zoomReset.setVisibility(View.GONE);
        topBar.addView(zoomReset,new LinearLayout.LayoutParams(dp(46),dp(44)));
        Button settings=button("⚙",this::showPlaybackSettings);
        settings.setContentDescription("影片進度條設定");
        topBar.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(topBar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        bottomBar=new LinearLayout(activity);
        bottomBar.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomBar.setBackground(overlay(false));
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        root.addView(bottomBar,bottomParams);
        dialog.setContentView(root);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{
            boolean wide=(r-l)>(b-t);
            if(bottomBar!=null&&(wide!=landscape)){
                landscape=wide;
                buildBottomControls();
            }
        });
        buildBottomControls();
        dialog.setOnDismissListener(d->{
            closed=true;generation++;
            handler.removeCallbacksAndMessages(null);
            decoder.shutdownNow();
            stopVideo();
            resetZoom();
            releaseImage();
            items.clear();
            try{activity.setRequestedOrientation(originalOrientation);}
            catch(RuntimeException ignored){}
        });
    }

    private boolean currentIsVideo(){
        return !items.isEmpty()&&index>=0&&index<items.size()&&items.get(index).video;
    }
    private int adjacentVideo(int direction){
        boolean[] flags=new boolean[items.size()];
        for(int i=0;i<flags.length;i++)flags[i]=items.get(i).video&&!items.get(i).removed;
        return PlayerGesturePolicy.adjacentVideo(flags,index,direction);
    }
    private void goVideo(int direction){
        int target=adjacentVideo(direction);
        if(target>=0){index=target;showCurrent();}
    }
    private void navigate(int direction){
        int target=PreviewNavigation.move(index,items.size(),direction);
        if(target>=0&&target!=index){index=target;showCurrent();}
    }

    private void buildBottomControls(){
        if(bottomBar==null)return;
        bottomBar.removeAllViews();
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(landscape?10:18),dp(landscape?8:20),
                dp(landscape?10:18),dp(landscape?5:18));
        if(!currentIsVideo()){
            if(!landscape){
                TextView instruction=text("上下滑切換照片／影片  ·  雙指縮放",11);
                instruction.setTextColor(0xffBAC5D6);instruction.setGravity(Gravity.CENTER);
                bottomBar.addView(instruction);
            }
            updateControlVisibility();
            return;
        }

        float size=PlayerProgressStyle.scale(preferences.getFloat("size",1f));
        float width=PlayerProgressStyle.width(preferences.getFloat("width",.96f));

        timeline=new SeekBar(activity);
        timeline.setMax(1000);
        timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        timeline.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66ffffff));
        timeline.setSplitTrack(false);
        int trackThickness=dp(PlayerProgressStyle.trackDp(size));
        GradientDrawable unplayedTrack=new GradientDrawable();
        unplayedTrack.setColor(0x77ffffff);
        unplayedTrack.setCornerRadius(dp(3));
        GradientDrawable playedTrack=new GradientDrawable();
        playedTrack.setColor(ACCENT);
        playedTrack.setCornerRadius(dp(3));
        android.graphics.drawable.ClipDrawable clippedTrack=
            new android.graphics.drawable.ClipDrawable(
                playedTrack,Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable progressDrawable=
            new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                unplayedTrack,clippedTrack});
        progressDrawable.setId(0,android.R.id.background);
        progressDrawable.setId(1,android.R.id.progress);
        progressDrawable.setLayerHeight(0,trackThickness);
        progressDrawable.setLayerHeight(1,trackThickness);
        progressDrawable.setLayerGravity(0,Gravity.CENTER_VERTICAL);
        progressDrawable.setLayerGravity(1,Gravity.CENTER_VERTICAL);
        timeline.setProgressDrawable(progressDrawable);
        timeline.setContentDescription("影片播放進度，可拖曳到任意位置");
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){
                draggingTimeline=true;revealControls();
            }
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(!fromUser||!videoPrepared)return;
                int duration=video.getDuration();
                if(duration>0){
                    int target=Math.round(duration*(progress/1000f));
                    video.seekTo(target);updatePlaybackTime(target,duration);
                }
                revealControls();
            }
            @Override public void onStopTrackingTouch(SeekBar bar){
                draggingTimeline=false;revealControls();updateTimeline();
            }
        });
        // Settings are in my-player's same ranges; scale the touch target and
        // the visible thumb without stealing touch from the playback stage.
        int thumb=dp(PlayerProgressStyle.thumbDp(size));
        GradientDrawable thumbShape=new GradientDrawable();
        thumbShape.setShape(GradientDrawable.OVAL);thumbShape.setColor(ACCENT);
        thumbShape.setSize(thumb,thumb);
        timeline.setThumb(thumbShape);
        int barHeight=dp(Math.max(30f,size*30f));
        if(landscape){
            LinearLayout row=new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(videoButton("|◀",()->goVideo(-1),adjacentVideo(-1)>=0,"上一部影片"),
                new LinearLayout.LayoutParams(dp(42),dp(42)));
            row.addView(videoButton(video.isPlaying()?"❚❚":"▶",this::toggleVideo,true,"播放或暫停"),
                new LinearLayout.LayoutParams(dp(46),dp(44)));
            row.addView(videoButton("▶|",()->goVideo(1),adjacentVideo(1)>=0,"下一部影片"),
                new LinearLayout.LayoutParams(dp(42),dp(42)));
            timeElapsed=timeLabel();row.addView(timeElapsed);
            LinearLayout barHolder=new LinearLayout(activity);barHolder.setGravity(Gravity.CENTER);
            barHolder.setWeightSum(1f);
            barHolder.addView(timeline,new LinearLayout.LayoutParams(0,barHeight,width));
            LinearLayout.LayoutParams weight=new LinearLayout.LayoutParams(0,barHeight,1);
            weight.setMargins(dp(2),0,dp(2),0);row.addView(barHolder,weight);
            timeTotal=timeLabel();row.addView(timeTotal);
            bottomBar.addView(row);
        }else{
            LinearLayout barHolder=new LinearLayout(activity);barHolder.setGravity(Gravity.CENTER);
            barHolder.addView(timeline,new LinearLayout.LayoutParams(-1,barHeight));
            LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(
                    Math.round(Math.max(1,activity.getResources().getDisplayMetrics().widthPixels-dp(36))*width),
                    barHeight);
            bottomBar.addView(barHolder,progressLp);
            LinearLayout timeRow=new LinearLayout(activity);timeRow.setGravity(Gravity.CENTER_VERTICAL);
            timeElapsed=timeLabel();timeTotal=timeLabel();
            timeRow.addView(timeElapsed);
            View spacer=new View(activity);timeRow.addView(spacer,new LinearLayout.LayoutParams(0,dp(14),1));
            timeRow.addView(timeTotal);
            bottomBar.addView(timeRow,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout buttons=new LinearLayout(activity);
            buttons.setGravity(Gravity.CENTER);
            buttons.addView(videoButton("|◀",()->goVideo(-1),adjacentVideo(-1)>=0,"上一部影片"),
                    new LinearLayout.LayoutParams(dp(56),dp(48)));
            buttons.addView(videoButton(video.isPlaying()?"❚❚":"▶",this::toggleVideo,true,"播放或暫停"),
                    new LinearLayout.LayoutParams(dp(88),dp(52)));
            buttons.addView(videoButton("▶|",()->goVideo(1),adjacentVideo(1)>=0,"下一部影片"),
                    new LinearLayout.LayoutParams(dp(56),dp(48)));
            bottomBar.addView(buttons);
        }
        updateTimeline();
        updateControlVisibility();
    }

    private Button videoButton(String label,Runnable action,boolean enabled,String description){
        Button b=button(label,()->{revealControls();action.run();});
        b.setEnabled(enabled);
        b.setContentDescription(description);
        b.setTextSize(label.equals("▶")||label.equals("❚❚")?25:19);
        if(!enabled)b.setTextColor(0xff777777);
        return b;
    }
    private TextView timeLabel(){
        TextView text=text("00:00",11);
        text.setPadding(dp(2),0,dp(2),0);
        return text;
    }
    private void updateControlVisibility(){
        int visible=controlsShown?View.VISIBLE:View.GONE;
        if(topBar!=null)topBar.setVisibility(visible);
        if(bottomBar!=null)bottomBar.setVisibility(visible);
    }
    private void revealControls(){
        controlsShown=true;updateControlVisibility();
        handler.removeCallbacks(hideControls);
        if(PlayerGesturePolicy.autoHide(currentIsVideo(),videoPrepared,video!=null&&video.isPlaying()))
            handler.postDelayed(hideControls,2000L);
    }
    private void showPlaybackSettings(){
        revealControls();
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(12),dp(18),dp(8));
        TextView sizeLabel=text("進度條粗細",15);sizeLabel.setTextColor(Color.BLACK);
        layout.addView(sizeLabel);
        SeekBar scaleBar=new SeekBar(activity);scaleBar.setMax(170);
        scaleBar.setProgress(Math.round((PlayerProgressStyle.scale(preferences.getFloat("size",1f))-.8f)*100f));
        layout.addView(scaleBar);
        TextView widthLabel=text("進度條寬度",15);widthLabel.setTextColor(Color.BLACK);
        layout.addView(widthLabel);
        SeekBar widthBar=new SeekBar(activity);widthBar.setMax(40);
        widthBar.setProgress(Math.round((PlayerProgressStyle.width(preferences.getFloat("width",.96f))-.6f)*100f));
        layout.addView(widthBar);
        new AlertDialog.Builder(activity)
          .setTitle("播放設定")
          .setView(layout)
          .setPositiveButton("儲存",(d,w)->{
              preferences.edit().putFloat("size",.8f+scaleBar.getProgress()/100f)
                       .putFloat("width",.6f+widthBar.getProgress()/100f).apply();
              buildBottomControls();revealControls();
          })
          .setNeutralButton("恢復預設",(d,w)->{
              preferences.edit().putFloat("size",1f).putFloat("width",.96f).apply();
              buildBottomControls();revealControls();
          })
          .setNegativeButton("取消",null).show();
    }
    private boolean onGesture(MotionEvent e){
        zoomDetector.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                multiFingerGesture=false;
                draggingSeek=false;
                touchX=e.getX();touchY=e.getY();lastMotionX=touchX;lastMotionY=touchY;
                swipeOriginMs=videoActive&&videoPrepared?Math.max(0,video.getCurrentPosition()):0;
                if(currentIsVideo())revealControls();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiFingerGesture=true;
                cancelSingleZoomTap();
                if(e.getPointerCount()>1){
                    centroidX=(e.getX(0)+e.getX(1))/2f;
                    centroidY=(e.getY(0)+e.getY(1))/2f;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()>1){
                    multiFingerGesture=true;
                    float x=(e.getX(0)+e.getX(1))/2f;
                    float y=(e.getY(0)+e.getY(1))/2f;
                    if(zoomScale>1f){
                        zoomX+=x-centroidX;zoomY+=y-centroidY;applyZoom();
                    }
                    centroidX=x;centroidY=y;
                    lastMotionX=x;lastMotionY=y;
                    return true;
                }
                if(multiFingerGesture){lastMotionX=e.getX();lastMotionY=e.getY();return true;}
                float dx=e.getX()-touchX,dy=e.getY()-touchY;
                if(currentIsVideo()&&videoPrepared&&
                        (draggingSeek||PlayerGesturePolicy.horizontalSeek(true,false,dx,dy,dp(18)))){
                    draggingSeek=true;
                    int duration=video.getDuration();
                    if(duration>0){
                        int target=PlayerGesturePolicy.seekTarget(swipeOriginMs,duration,dx);
                        video.seekTo(target);
                        updatePlaybackTime(target,duration);
                        if(timeline!=null)timeline.setProgress(Math.round(target*1000f/duration));
                        seekHint.setText(formatTime(target)+" / "+formatTime(duration));
                        seekHint.setVisibility(View.VISIBLE);
                    }
                }else if(zoomScale>1f){
                    // Use last pointer coordinates, not optional MotionEvent history.
                    // ACTION_MOVE events often have no historical samples at all.
                    if(!currentIsVideo())zoomX+=e.getX()-lastMotionX;
                    zoomY+=e.getY()-lastMotionY;
                    applyZoom();
                }
                lastMotionX=e.getX();lastMotionY=e.getY();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                multiFingerGesture=true;return true;
            case MotionEvent.ACTION_UP:
                seekHint.setVisibility(View.GONE);
                if(multiFingerGesture){multiFingerGesture=false;return true;}
                float endDx=e.getX()-touchX,endDy=e.getY()-touchY;
                if(draggingSeek){draggingSeek=false;revealControls();return true;}
                if(zoomScale<=1.01f&&Math.abs(endDy)>dp(85)&&
                        Math.abs(endDy)>Math.abs(endDx)*1.2f){
                    cancelSingleZoomTap();navigate(endDy<0?1:-1);
                }else if(Math.abs(endDx)<dp(10)&&Math.abs(endDy)<dp(10)){
                    handleTap(e.getX(),e.getY());
                }else{cancelSingleZoomTap();}
                return true;
            case MotionEvent.ACTION_CANCEL:
                seekHint.setVisibility(View.GONE);
                draggingSeek=false;multiFingerGesture=false;cancelSingleZoomTap();
                return true;
            default:return true;
        }
    }
    private void handleTap(float x,float y){
        long now=SystemClock.uptimeMillis();
        if(zoomScale>1.01f&&previousTapTime>0&&now-previousTapTime<=330L&&
                Math.hypot(x-previousTapX,y-previousTapY)<=dp(48)){
            cancelSingleZoomTap();resetZoom();revealControls();return;
        }
        if(zoomScale>1.01f){
            cancelSingleZoomTap();
            previousTapTime=now;previousTapX=x;previousTapY=y;
            pendingZoomTap=()->{
                pendingZoomTap=null;previousTapTime=0;
                singleTap(x,y);
            };
            handler.postDelayed(pendingZoomTap,310L);
        }else{
            previousTapTime=0;
            singleTap(x,y);
        }
    }
    private void singleTap(float x,float y){
        if(currentIsVideo()){
            if(PlayerGesturePolicy.centerPlayTap(x,y,stage.getWidth(),stage.getHeight()))
                toggleVideo();
            else revealControls();
        }else{
            controlsShown=!controlsShown;updateControlVisibility();
        }
    }
    private void cancelSingleZoomTap(){
        if(pendingZoomTap!=null){handler.removeCallbacks(pendingZoomTap);pendingZoomTap=null;}
        previousTapTime=0;
    }
    private void applyZoom(){
        zoomScale=Math.max(1f,Math.min(5f,zoomScale));
        float maxX=(zoomScale-1f)*stage.getWidth()/2f;
        float maxY=(zoomScale-1f)*stage.getHeight()/2f;
        zoomX=Math.max(-maxX,Math.min(maxX,zoomX));
        zoomY=Math.max(-maxY,Math.min(maxY,zoomY));
        image.setScaleX(zoomScale);image.setScaleY(zoomScale);
        image.setTranslationX(zoomX);image.setTranslationY(zoomY);
        video.setScaleX(zoomScale);video.setScaleY(zoomScale);
        video.setTranslationX(zoomX);video.setTranslationY(zoomY);
        if(zoomReset!=null)zoomReset.setVisibility(zoomScale>1.01f?View.VISIBLE:View.GONE);
    }
    private void resetZoom(){
        zoomScale=1f;zoomX=0f;zoomY=0f;applyZoom();
    }
    private void showCurrent(){
        if(closed||items.isEmpty())return;
        ++generation;
        cancelSingleZoomTap();
        stopVideo();
        resetZoom();
        Scanner.Item item=items.get(index);
        title.setText(item.name==null?"未命名":item.name);
        counter.setText((index+1)+" / "+items.size()+"   ·   "+(item.video?"影片":"照片"));
        controlsShown=true;
        error.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        image.setVisibility(View.VISIBLE);
        releaseImage();
        buildBottomControls();
        if(item.video)startVideo();
        else showPhoto(item,generation);
        updateControlVisibility();
    }
    private void showPhoto(Scanner.Item item,int requestId){
        try{
            decoder.execute(()->{
                if(requestId!=generation||closed)return;
                Bitmap bitmap=null;
                try{
                    final int maxDimension=Math.min(2304,
                        Math.max(activity.getResources().getDisplayMetrics().widthPixels,
                                 activity.getResources().getDisplayMetrics().heightPixels)*2);
                    bitmap=ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(activity.getContentResolver(),item.uri),
                        (source,info,ignored)->{
                            int width=info.getSize().getWidth(),height=info.getSize().getHeight();
                            float scale=Math.min(1f,maxDimension/(float)Math.max(1,Math.max(width,height)));
                            source.setTargetSize(Math.max(1,Math.round(width*scale)),
                                Math.max(1,Math.round(height*scale)));
                            source.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                }catch(Exception|OutOfMemoryError ignored){}
                Bitmap decoded=bitmap;
                activity.runOnUiThread(()->{
                    if(closed||!dialog.isShowing()||requestId!=generation){
                        if(decoded!=null)decoded.recycle();return;
                    }
                    loading.setVisibility(View.GONE);
                    if(decoded!=null){
                        displayedBitmap=decoded;image.setImageBitmap(decoded);
                    }else{
                        error.setText("照片無法讀取，可能已移動、刪除或沒有權限。");
                        error.setVisibility(View.VISIBLE);
                    }
                });
            });
        }catch(RejectedExecutionException ignored){loading.setVisibility(View.GONE);}
    }
    private void startVideo(){
        Scanner.Item media=items.get(index);
        int playbackIndex=index,requestId=generation;
        videoActive=true;videoPrepared=false;
        video.setVisibility(View.VISIBLE);
        image.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        video.setOnPreparedListener(mp->{
            if(closed||!videoActive||playbackIndex!=index||requestId!=generation)return;
            videoPrepared=true;
            loading.setVisibility(View.GONE);
            video.start();
            buildBottomControls();
            handler.removeCallbacks(clockTicker);
            handler.post(clockTicker);
            revealControls();
        });
        video.setOnCompletionListener(mp->{
            if(!closed&&videoActive&&playbackIndex==index&&requestId==generation){
                handler.removeCallbacks(hideControls);
                controlsShown=true;buildBottomControls();updateTimeline();
            }
        });
        video.setOnErrorListener((mp,what,extra)->{
            if(!closed&&playbackIndex==index&&requestId==generation){
                stopVideo();loading.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);
                error.setText("影片無法播放，可能格式不支援或沒有讀取權限。");
                error.setVisibility(View.VISIBLE);
                revealControls();
            }
            return true;
        });
        try{video.setVideoURI(media.uri);}
        catch(RuntimeException e){
            stopVideo();loading.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            error.setText("無法開啟影片："+e.getClass().getSimpleName());
            error.setVisibility(View.VISIBLE);
        }
    }
    private void toggleVideo(){
        if(!currentIsVideo()||closed||!videoPrepared)return;
        if(video.isPlaying())video.pause();
        else{
            if(video.getDuration()>0&&video.getCurrentPosition()>=video.getDuration()-250)video.seekTo(0);
            video.start();
        }
        buildBottomControls();
        revealControls();
    }
    private void updateTimeline(){
        if(!videoActive||!videoPrepared)return;
        int duration=video.getDuration();
        int position=Math.max(0,video.getCurrentPosition());
        updatePlaybackTime(position,duration);
        if(!draggingTimeline&&!draggingSeek&&timeline!=null&&duration>0)
            timeline.setProgress(Math.round(position*1000f/duration));
    }
    private void updatePlaybackTime(int position,int duration){
        if(timeElapsed!=null)timeElapsed.setText(formatTime(position));
        if(timeTotal!=null)timeTotal.setText(formatTime(duration));
    }
    private String formatTime(int ms){
        int sec=Math.max(0,ms/1000);
        if(sec>=3600)return String.format(Locale.TAIWAN,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);
        return String.format(Locale.TAIWAN,"%02d:%02d",sec/60,sec%60);
    }
    private void stopVideo(){
        handler.removeCallbacks(hideControls);
        handler.removeCallbacks(clockTicker);
        if(video!=null){
            try{video.stopPlayback();}catch(RuntimeException ignored){}
            video.setVisibility(View.GONE);
        }
        videoActive=false;videoPrepared=false;draggingTimeline=false;draggingSeek=false;
        timeline=null;timeElapsed=null;timeTotal=null;
    }
    private void releaseImage(){
        if(image!=null)image.setImageDrawable(null);
        Bitmap old=displayedBitmap;
        displayedBitmap=null;
        if(old!=null&&!old.isRecycled())old.recycle();
    }
}

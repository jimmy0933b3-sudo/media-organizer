package tw.mediaorganizer;
import android.app.*;
import android.app.Dialog;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import org.json.*;

import androidx.appcompat.app.AppCompatActivity;
public class MainActivity extends AppCompatActivity implements ScanService.ScanListener {
 private final int BG=0xff0B1220,CARD=0xff19283B,TEXT=0xffF4F8FF,ACCENT=0xff5DE1C4,BUTTON_BG=0xff26374C,TEXT_SEC=0xffADBED1;
 private final int STROKE=0xff304357,DANGER=0xffFFC0B5;
 private EditText searchInput;
 private final TextView[] statValues=new TextView[4];
 private FrameLayout root;
 private LinearLayout mainContent;
 private androidx.recyclerview.widget.RecyclerView list;
 private ItemAdapter adapter;
 private final android.util.LruCache<String, android.graphics.Bitmap> thumbCache = new android.util.LruCache<String, android.graphics.Bitmap>((int)(Runtime.getRuntime().maxMemory()/1024)/8){protected int sizeOf(String k,android.graphics.Bitmap v){return v.getByteCount()/1024;}};private TextView status,selection,srcLabel;
 private TextView[] tabButtons;
 private View footerContainer;
 private android.widget.Switch people,bgScan;private Spinner strictness,mediaTypeSpinner;private Button scan,move,deleteBtn,cancel;
 private final ExecutorService fileWorker=Executors.newSingleThreadExecutor();
 private volatile boolean dead,busy;private Scanner.Result result;
 private final LinkedHashSet<Scanner.Item> selected=new LinkedHashSet<>();private int mode=0,limit=20;
 private final Set<Scanner.Group> expandedGroups = new HashSet<>();
 private long lastProgress;
 private tw.mediaorganizer.OperationController op() { return tw.mediaorganizer.OperationController.getInstance(); }

 private final Set<String> sourceFilters=new TreeSet<>();
 private long resultGeneration;

 public static class MoveRecord {Uri uri;String oldPath,oldName,newPath,newName;Scanner.Item item;}
 public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  build();
  ScanService.setListener(this);
  if(ScanService.isScanning()){
   if(ScanService.getCurrentScanGeneration()>=resultGeneration){
    resultGeneration = ScanService.getCurrentScanGeneration();
   }
   setBusy(true);
   status.setText("背景掃描進行中…");
  }
 }

 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(TEXT);t.setTextSize(size);t.setPadding(0,dp(5),0,dp(5));return t;}
 private android.graphics.drawable.GradientDrawable surface(int fill,int radius,int border){
  android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
  d.setColor(fill);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);
  return d;
 }
 private Button button(String label,Runnable action){
  Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);
  b.setTextColor(TEXT);b.setBackground(surface(BUTTON_BG,14,STROKE));
  b.setPadding(dp(8),0,dp(8),0);
  b.setOnClickListener(v->{if(action!=null)action.run();});
  return b;
 }
 private View statCard(String caption,int index){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER_VERTICAL);
  card.setPadding(dp(9),dp(8),dp(5),dp(8));card.setBackground(surface(CARD,14,STROKE));
  TextView num=text("0",20);num.setTypeface(null,Typeface.BOLD);num.setTextColor(index==1?ACCENT:TEXT);
  num.setSingleLine(true);num.setEllipsize(android.text.TextUtils.TruncateAt.END);
  TextView name=text(caption,11);name.setTextColor(TEXT_SEC);card.addView(num);card.addView(name);
  statValues[index]=num;return card;
 }
 private int countGroups(List<Scanner.Group> groups){
  if(groups==null)return 0;
  int total=0;for(Scanner.Group g:groups){
   int visible=0;for(Scanner.Item i:g.items)if(!i.removed)visible++;
   if(visible>1)total++;
  }
  return total;
 }
 private void updateOverview(){
  if(result==null)return;
  int visible=0;for(Scanner.Item i:result.all)if(!i.removed)visible++;
  int[] counts={visible,countGroups(result.exact),countGroups(result.similar),countGroups(result.people)};
  for(int i=0;i<4;i++)if(statValues[i]!=null)statValues[i].setText(String.format(Locale.TAIWAN,"%,d",counts[i]));
  if(tabButtons!=null){
   String[] names={"重複","相似","人物","全部"};int[] values={counts[1],counts[2],counts[3],counts[0]};
   for(int i=0;i<4;i++)if(tabButtons[i]!=null)tabButtons[i].setText(names[i]+"\n"+String.format(Locale.TAIWAN,"%,d",values[i]));
  }
 }
 private void build(){
  root = new FrameLayout(this);
  root.setBackgroundColor(BG);

  LinearLayout mainContent = new LinearLayout(this);
  mainContent.setOrientation(1);
  mainContent.setPadding(dp(20),dp(16),dp(20),dp(12));

  root.setOnApplyWindowInsetsListener((v,insets)->{
      android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
      mainContent.setPadding(dp(16),dp(8)+i.top,dp(16),dp(8)+i.bottom);
      if (settingsScreen != null) {
          settingsScreen.setPadding(dp(16),dp(8)+i.top,dp(16),dp(8)+i.bottom);
      }
      return insets;
  });
  setContentView(root);

  this.mainContent = mainContent;
  root.addView(mainContent, new FrameLayout.LayoutParams(-1, -1));

  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0, 0, 0, dp(16));
  LinearLayout headerTitles=new LinearLayout(this);headerTitles.setOrientation(1);
  String verName="1.4";
  try{verName=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){}
  TextView title=text("相簿整理員",26);title.setTypeface(null,Typeface.BOLD);headerTitles.addView(title);
   TextView subtitle=text("私密・離線・安心整理",11);subtitle.setTextColor(ACCENT);headerTitles.addView(subtitle);
  header.addView(headerTitles,new LinearLayout.LayoutParams(0,-2,1));
  ImageView settingsBtn = new ImageView(this);
  settingsBtn.setId(R.id.btn_settings);
  settingsBtn.setContentDescription(getString(R.string.desc_settings));
  settingsBtn.setImageResource(R.drawable.ic_settings);
   settingsBtn.setColorFilter(TEXT);settingsBtn.setBackground(surface(CARD,14,STROKE));
  settingsBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
  android.util.TypedValue outValue = new android.util.TypedValue();
  if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
      settingsBtn.setBackgroundResource(outValue.resourceId);
  }
  settingsBtn.setOnClickListener(v -> showSettingsDialog());
  header.addView(settingsBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
  mainContent.addView(header);

  people=new android.widget.Switch(this);people.setChecked(true);
  bgScan=new android.widget.Switch(this);bgScan.setChecked(getPreferences(0).getBoolean("bg_scan",false));
  bgScan.setOnCheckedChangeListener((v,checked)->getPreferences(0).edit().putBoolean("bg_scan",checked).apply());

  mediaTypeSpinner=new Spinner(this);
  String[] mediaTypes={"全部","僅照片","僅影片"};
  ArrayAdapter<String> mediaAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,mediaTypes){public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(TEXT);return t;}};
  mediaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  mediaTypeSpinner.setAdapter(mediaAdapter);

  strictness=new Spinner(this);
  String[] labels={"嚴格（建議）","一般","寬鬆（較多誤判）"};
  ArrayAdapter<String> mediaAdapter2=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(TEXT);return t;}};
  mediaAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  strictness.setAdapter(mediaAdapter2);

  srcLabel=text(" 來源：全部媒體",12);

  LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(16),dp(11),dp(16),dp(11));
   android.graphics.drawable.GradientDrawable heroBackground=new android.graphics.drawable.GradientDrawable(
     android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff244B59,0xff163045,0xff15233A});
   heroBackground.setCornerRadius(dp(18));hero.setBackground(heroBackground);
   TextView heroTitle=text("給相簿一次大掃除",18);heroTitle.setTypeface(null,Typeface.BOLD);
   TextView heroTip=text("本機比對重複、相似與人物，原始照片不會上傳。",12);
   heroTip.setTextColor(0xffC9DEEB);heroTip.setMaxLines(2);
   hero.addView(heroTitle);hero.addView(heroTip);
   LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);heroLp.setMargins(0,0,0,dp(10));
   mainContent.addView(hero,heroLp);
   LinearLayout stats=new LinearLayout(this);stats.setWeightSum(4);
   String[] namesStat={"已掃描","重複組","相似組","人物組"};
   for(int i=0;i<4;i++){
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
     if(i>0)lp.setMargins(dp(5),0,0,0);
     stats.addView(statCard(namesStat[i],i),lp);
   }
   LinearLayout.LayoutParams statLp=new LinearLayout.LayoutParams(-1,-2);
   statLp.setMargins(0,0,0,dp(10));mainContent.addView(stats,statLp);

   LinearLayout actions=new LinearLayout(this);
  scan=button("開始掃描",this::requestScan);
  android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
  android.graphics.drawable.GradientDrawable gdDisabled = new android.graphics.drawable.GradientDrawable();
  gdDisabled.setColor(0xff555555);
  gdDisabled.setCornerRadius(dp(8));
  android.graphics.drawable.GradientDrawable gdEnabled = new android.graphics.drawable.GradientDrawable();
  gdEnabled.setColor(ACCENT);
  gdEnabled.setCornerRadius(dp(8));
  sld.addState(new int[]{-android.R.attr.state_enabled}, gdDisabled);
  sld.addState(new int[]{}, gdEnabled);
  scan.setBackground(sld);scan.setText("開始智慧掃描");scan.setTypeface(null,Typeface.BOLD);

  android.content.res.ColorStateList textColorStateList = new android.content.res.ColorStateList(
        new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
        new int[]{0xffaaaaaa, BG}
  );
  scan.setTextColor(textColorStateList);

  cancel=button("停止",()->{
   ScanService.cancelScan();
   status.setText("正在停止，已完成的掃描結果已保留…");
  });
  cancel.setVisibility(View.GONE);
  LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, dp(48), 1);
  scanParams.setMargins(0, 0, dp(12), 0);
  actions.addView(scan, scanParams);
  actions.addView(cancel, new LinearLayout.LayoutParams(dp(80), dp(48)));
  mainContent.addView(actions);

  status=text("準備就緒。開始掃描後可逐步檢視結果。",12);
   status.setTextColor(TEXT_SEC);status.setMaxLines(3);
   status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setPadding(dp(4),dp(8),dp(4),dp(9));
   mainContent.addView(status);

   searchInput=new EditText(this);
   searchInput.setId(R.id.search_input);
   searchInput.setSingleLine(true);searchInput.setTextSize(13);searchInput.setTextColor(TEXT);
   searchInput.setHintTextColor(TEXT_SEC);searchInput.setHint("搜尋檔名、資料夾");
   searchInput.setContentDescription("搜尋掃描結果");
   searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
   searchInput.setPadding(dp(14),0,dp(14),0);searchInput.setBackground(surface(CARD,14,STROKE));
   searchInput.addTextChangedListener(new android.text.TextWatcher(){
     public void beforeTextChanged(CharSequence value,int start,int count,int after){}
     public void onTextChanged(CharSequence value,int start,int before,int count){limit=20;expandedGroups.clear();render();}
     public void afterTextChanged(android.text.Editable value){}
   });
   mainContent.addView(searchInput,new LinearLayout.LayoutParams(-1,dp(46)));
   TextView listTitle=text("掃描結果  ·  點選縮圖預覽",12);listTitle.setTextColor(TEXT_SEC);
   LinearLayout.LayoutParams listTitleLp=new LinearLayout.LayoutParams(-1,-2);
   listTitleLp.setMargins(0,dp(5),0,dp(1));mainContent.addView(listTitle,listTitleLp);
  LinearLayout tabs=new LinearLayout(this);tabs.setWeightSum(4);
   tabs.setBackground(surface(CARD,14,STROKE));tabs.setPadding(dp(4),dp(4),dp(4),dp(4));
   String[] names={"重複","相似","人物","全部"};
   tabButtons=new TextView[4];
   for(int i=0;i<4;i++){
     final int idx=i;
     TextView tab=new TextView(this);tab.setText(names[i]+"\n0");
     tab.setGravity(Gravity.CENTER);tab.setTextSize(12);
     tab.setTextColor(i==mode?BG:TEXT_SEC);
     tab.setTypeface(null,i==mode?Typeface.BOLD:Typeface.NORMAL);
     tab.setBackground(surface(i==mode?ACCENT:CARD,11,0));
     tab.setContentDescription(names[i]+"分類");
     tab.setOnClickListener(v->{
       mode=idx;limit=20;
       for(int j=0;j<4;j++){
         tabButtons[j].setTextColor(j==idx?BG:TEXT_SEC);
         tabButtons[j].setTypeface(null,j==idx?Typeface.BOLD:Typeface.NORMAL);
         tabButtons[j].setBackground(surface(j==idx?ACCENT:CARD,11,0));
       }
       render();
     });
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
     if(i>0)lp.setMargins(dp(2),0,0,0);
     tabs.addView(tab,lp);tabButtons[i]=tab;
   }
   LinearLayout.LayoutParams tabsLp=new LinearLayout.LayoutParams(-1,-2);
   tabsLp.setMargins(0,0,0,dp(8));mainContent.addView(tabs,tabsLp);
  list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); adapter = new ItemAdapter(); list.setAdapter(adapter); mainContent.addView(list, new LinearLayout.LayoutParams(-1,0,1));
  selection=text("已選 0 個",14);

  LinearLayout footerContainer=new LinearLayout(this);
   footerContainer.setOrientation(1);footerContainer.setPadding(dp(12),dp(10),dp(12),dp(12));
   footerContainer.setBackground(surface(CARD,18,STROKE));
   LinearLayout selectionRow=new LinearLayout(this);selectionRow.setGravity(Gravity.CENTER_VERTICAL);
   selection.setTypeface(null,Typeface.BOLD);selectionRow.addView(selection,new LinearLayout.LayoutParams(0,-2,1));
   Button clear=button("清除選取",()->{selected.clear();render();});
   clear.setTextColor(TEXT_SEC);
   selectionRow.addView(clear,new LinearLayout.LayoutParams(dp(96),dp(42)));
   footerContainer.addView(selectionRow);
   LinearLayout footer=new LinearLayout(this);
   move=button("移動到資料夾",this::confirmMove);move.setTypeface(null,Typeface.BOLD);
   move.setTextColor(BG);move.setBackground(surface(ACCENT,14,0));
   deleteBtn=button("刪除所選",this::confirmDelete);
   deleteBtn.setTextColor(DANGER);deleteBtn.setBackground(surface(0xff422C36,14,0xff76505D));
   LinearLayout.LayoutParams moveParams=new LinearLayout.LayoutParams(0,dp(50),1);
   moveParams.setMargins(0,0,dp(8),0);footer.addView(move,moveParams);
   footer.addView(deleteBtn,new LinearLayout.LayoutParams(0,dp(50),1));
   footerContainer.addView(footer);footerContainer.setVisibility(View.GONE);
   this.footerContainer=footerContainer;mainContent.addView(footerContainer,new LinearLayout.LayoutParams(-1,-2));

  resultGeneration=ScanService.getGeneration();
  Scanner.Result cached=loadScanCache();
  if(cached!=null&&!cached.all.isEmpty()){
   result=cached;
   status.setText("已載入上次記錄\n" + cached.all.size() + " 個檔案 · " + cached.exact.size() + " 組重複 · " + cached.similar.size() + " 組相似");
  }
  render();

  loadOpState();
  if (op().fileBusy()) {
      if (op().isDeleting() && op().getPendingDeletes() != null && !op().getPendingDeletes().isEmpty()) {
          requestDeleteBatch();
      } else if (op().getPending() != null && !op().getPending().isEmpty()) {
          requestWrite();
      } else {
          op().setFileBusy(false);
      }
  } else {
      op().setFileBusy(false);
  }

  tw.mediaorganizer.OperationController.getInstance().getFileWorker().execute(() -> {
      try { promotePendingJournal(); } catch (Exception e) {}
  });
 }

 private void selectSourceFolder(){
  List<String> folders=getMediaSubfolders();
  if(folders.isEmpty()){
   Toast.makeText(this,"尚無媒體資料夾可選擇",Toast.LENGTH_SHORT).show();
   return;
  }
  String[] items=folders.toArray(new String[0]);
  boolean[] checked=new boolean[items.length];
  Set<String> tempFilters=new TreeSet<>(sourceFilters);
  for(int i=0;i<items.length;i++)checked[i]=tempFilters.contains(items[i]+"/");
  new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("選擇來源資料夾（可多選）").setMultiChoiceItems(items,checked,(dialog,which,isChecked)->{
   String path=items[which]+"/";
   if(isChecked)tempFilters.add(path);
   else tempFilters.remove(path);
  }).setPositiveButton("確定",(d,w)->{sourceFilters.clear();sourceFilters.addAll(tempFilters);updateSrcLabel();}).setNeutralButton("全選/全部媒體",(d,w)->{sourceFilters.clear();updateSrcLabel();}).setNegativeButton("取消",null).show();
 }

 private void updateSrcLabel(){
  if(sourceFilters.isEmpty())srcLabel.setText(" 來源：全部媒體");
  else if(sourceFilters.size()==1)srcLabel.setText(" 來源："+sourceFilters.iterator().next());
  else srcLabel.setText(" 來源：已選 "+sourceFilters.size()+" 個資料夾");
 }

 private List<String> getMediaSubfolders(){
  Set<String> subfolders=new TreeSet<>();
  try(android.database.Cursor c=getContentResolver().query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),new String[]{"relative_path"},"_size > 0 AND relative_path IS NOT NULL",null,null)){
   if(c!=null)while(c.moveToNext()){
    String p=c.getString(0);
    if(p!=null&&!p.isEmpty()){
     if(p.endsWith("/"))p=p.substring(0,p.length()-1);
     if(!p.isEmpty())subfolders.add(p);
    }
   }
  }catch(Exception e){}
  return new ArrayList<>(subfolders);
 }

 private boolean permission(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
 private boolean hasMediaPermission(){
  int mediaType=mediaTypeSpinner.getSelectedItemPosition();
  if(Build.VERSION.SDK_INT>=33){
   if(mediaType==1)return permission("android.permission.READ_MEDIA_IMAGES")||permission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
   if(mediaType==2)return permission("android.permission.READ_MEDIA_VIDEO")||permission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
   return (permission("android.permission.READ_MEDIA_IMAGES")||permission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")) &&
          (permission("android.permission.READ_MEDIA_VIDEO")||permission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"));
  }
  return permission("android.permission.READ_EXTERNAL_STORAGE");
 }

 private void requestScan(){
  if(busy||op().fileBusy())return;int mediaType=mediaTypeSpinner.getSelectedItemPosition();
  ArrayList<String> perms=new ArrayList<>();
  if(Build.VERSION.SDK_INT>=33){
   if(mediaType!=2&&!permission("android.permission.READ_MEDIA_IMAGES"))perms.add("android.permission.READ_MEDIA_IMAGES");
   if(mediaType!=1&&!permission("android.permission.READ_MEDIA_VIDEO"))perms.add("android.permission.READ_MEDIA_VIDEO");
   if(Build.VERSION.SDK_INT>=34&&!perms.isEmpty())perms.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
  }else if(!permission("android.permission.READ_EXTERNAL_STORAGE")){
   perms.add("android.permission.READ_EXTERNAL_STORAGE");
  }
  if(bgScan.isChecked()&&Build.VERSION.SDK_INT>=33&&!permission("android.permission.POST_NOTIFICATIONS")){
   perms.add("android.permission.POST_NOTIFICATIONS");
  }
  if(!perms.isEmpty()){
   requestPermissions(perms.toArray(new String[0]),41);return;
  }
  startScan();
 }

 private void startScan(){
  selected.clear();expandedGroups.clear();result=new Scanner.Result();render();setBusy(true);
  status.setText("掃描中，可即時查看結果與進行處理…");
  final boolean faces=people.isChecked();
  final boolean isBgScan=bgScan.isChecked();
  final int setting=strictness.getSelectedItemPosition();
  final int mediaType=mediaTypeSpinner.getSelectedItemPosition();
  final Set<String> snapshotFilters=new HashSet<>(sourceFilters);
  lastProgress=0;

  resultGeneration = ScanService.startScanJob(this, isBgScan, faces, setting, mediaType, snapshotFilters);
 }

 public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
  super.onRequestPermissionsResult(code,perms,grants);
  if(code==41){
   if(hasMediaPermission()){
    startScan();
   }else{
    status.setText("尚未取得媒體權限；請重新掃描並授權。");
   }
  }
 }

 private void setBusy(boolean b){
  busy=b;
  scan.setEnabled(!b&&!op().fileBusy());
  cancel.setEnabled(b);
  cancel.setVisibility(b ? View.VISIBLE : View.GONE);
  people.setEnabled(!b);
  bgScan.setEnabled(!b);
  strictness.setEnabled(!b);
  mediaTypeSpinner.setEnabled(!b);
  move.setEnabled(!selected.isEmpty()&&!op().fileBusy());
  deleteBtn.setEnabled(!selected.isEmpty()&&!op().fileBusy());
  if(b)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
 }

 public void onProgress(String s){
  ui(()->{
   long now=System.currentTimeMillis();
   if(now-lastProgress<250)return;
   lastProgress=now;
   status.setText(s);
  });
 }

 public void onPartialResult(Scanner.Result partial, long generation){
  ui(()->{
   if(generation<resultGeneration)return;
   resultGeneration=generation;
   result=partial;
   render();
  });
 }

 public void onComplete(Scanner.Result scanned, long generation){
  ui(()->{
   setBusy(false); if (cancel != null) cancel.setVisibility(View.GONE);
   if(generation<resultGeneration)return;
   resultGeneration=generation;
   result=scanned;
   saveScanCache(scanned);
   int mediaType=mediaTypeSpinner.getSelectedItemPosition();
   boolean full=Build.VERSION.SDK_INT<33||((mediaType!=2?permission("android.permission.READ_MEDIA_IMAGES"):true)&&(mediaType!=1?permission("android.permission.READ_MEDIA_VIDEO"):true));
   String summary="完成 · "+scanned.all.size()+" 個檔案 · "+scanned.exact.size()+" 組重複 · "+scanned.similar.size()+" 組相似 · "+scanned.people.size()+" 組人物";
   if(!sourceFilters.isEmpty())summary+=" (來源: 已選 "+sourceFilters.size()+" 個資料夾)";
   if(!full)summary+="\n目前只有部分媒體授權；結果不涵蓋全手機。";
   if(scanned.unreadable>0)summary+="\n讀取／解碼失敗 "+scanned.unreadable+" 次";
   if(scanned.faceFailures>0)summary+="\n人物分析失敗 "+scanned.faceFailures+" 個畫面";
   if(!scanned.faceError.isEmpty())summary+="\n"+scanned.faceError;
   status.setText(summary);
   render();
  });
 }

 public void onError(String message, long generation){
  ui(()->{
   setBusy(false); if (cancel != null) cancel.setVisibility(View.GONE);
   if(generation<resultGeneration)return;
   resultGeneration=generation;
   if(result!=null)saveScanCache(result);
   status.setText(message);
   render();
  });
 }

 private void ui(Runnable r){if(!dead)runOnUiThread(()->{if(!dead)r.run();});}

 private class ListItem {
     int type; Scanner.Group group; Scanner.Item item;
     String text; boolean checked;
     String thumbPath; boolean video;
     ListItem(int t, Scanner.Group g, Scanner.Item i, String s, boolean c) {
         type = t; group = g; item = i; text = s; checked = c;
         if (i != null) {
             thumbPath = i.thumb != null ? i.thumb.getPath() : null;
             video = i.video;
         }
     }
 }
 private class ItemAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
     List<ListItem> data = new ArrayList<>();
     void update(List<ListItem> newData) {
         androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
             public int getOldListSize() { return data.size(); }
             public int getNewListSize() { return newData.size(); }
             public boolean areItemsTheSame(int o, int n) {
                 ListItem oldItem = data.get(o), newItem = newData.get(n);
                 if (oldItem.type != newItem.type) return false;
                 if (oldItem.type == 2) return oldItem.item.uri.equals(newItem.item.uri);
                 if (oldItem.type == 1) return oldItem.group == newItem.group;
                 return oldItem.text.equals(newItem.text);
             }
                         public boolean areContentsTheSame(int o, int n) {
                 ListItem oldItem = data.get(o), newItem = newData.get(n);
                 if (oldItem.type == 2) {
                     boolean sameThumb = (oldItem.thumbPath == null && newItem.thumbPath == null) ||
                                         (oldItem.thumbPath != null && oldItem.thumbPath.equals(newItem.thumbPath));
                     boolean sameContent = oldItem.item.uri.equals(newItem.item.uri) &&
                                           oldItem.item.name.equals(newItem.item.name) &&
                                           oldItem.item.path.equals(newItem.item.path);
                     return oldItem.checked == newItem.checked && oldItem.video == newItem.video && sameThumb && sameContent;
                 }
                 return java.util.Objects.equals(oldItem.text,newItem.text);
             }
         });
                 data = newData;
        diffResult.dispatchUpdatesTo(this);
     }
     public int getItemViewType(int pos) { return data.get(pos).type; }
     public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
         if (viewType == 3) {
             LinearLayout empty = new LinearLayout(MainActivity.this); empty.setOrientation(1); empty.setGravity(Gravity.CENTER);
             empty.setPadding(dp(16),dp(36),dp(16),dp(36));empty.setBackground(surface(CARD,16,STROKE));
             ImageView icon = new ImageView(MainActivity.this); icon.setImageResource(R.drawable.ic_empty);
             empty.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
             TextView desc = text("", 14); desc.setGravity(Gravity.CENTER); desc.setTextColor(TEXT_SEC);desc.setLineSpacing(dp(4),1f);
             LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(16), 0, 0);
             empty.addView(desc, lp);
             return new RecyclerView.ViewHolder(empty) {};
         } else if (viewType == 0 || viewType == 4 || viewType == 5) {
             TextView t = text("", 15);
             t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
             if (viewType == 4 || viewType == 5) {
                 t.setPadding(dp(16), dp(16), dp(16), dp(16));
                 t.setTextColor(ACCENT);
                 t.setTypeface(null, android.graphics.Typeface.BOLD);
                 android.util.TypedValue outValue = new android.util.TypedValue();
                 MainActivity.this.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                 t.setBackgroundResource(outValue.resourceId);
             }
             return new RecyclerView.ViewHolder(t) {};
         } else if (viewType == 1) {
             LinearLayout card = new LinearLayout(MainActivity.this); card.setOrientation(1); card.setPadding(dp(10),dp(9),dp(10),dp(9)); card.setBackgroundColor(CARD);
             RecyclerView.LayoutParams cp = new RecyclerView.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(10)); card.setLayoutParams(cp);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(CARD);
                gd.setCornerRadius(dp(12));
                card.setBackground(gd);
                card.setPadding(dp(14),dp(12),dp(14),dp(12));
             TextView title = text("", 14); title.setTypeface(null,1); title.setId(101); card.addView(title);
             ImageView face = new ImageView(MainActivity.this); face.setId(102); face.setVisibility(View.GONE); card.addView(face, new LinearLayout.LayoutParams(dp(72),dp(72)));
             TextView faceLabel = text("此分組只代表至少一張抽樣人臉接近；多人、側臉或模糊畫面可能誤判。", 11); faceLabel.setId(103); faceLabel.setVisibility(View.GONE); card.addView(faceLabel);
             LinearLayout tools = new LinearLayout(MainActivity.this);
             Button checkAll=button("選取重複副本",null);checkAll.setId(104);
              LinearLayout.LayoutParams selectLp=new LinearLayout.LayoutParams(0,dp(44),1);
              selectLp.setMargins(0,0,dp(8),0);tools.addView(checkAll,selectLp);
             Button uncheckAll=button("清除此組選取",null);uncheckAll.setId(105);
              tools.addView(uncheckAll,new LinearLayout.LayoutParams(0,dp(44),1));
             card.addView(tools);
             return new RecyclerView.ViewHolder(card) {};
         } else {
             LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(6),dp(6),dp(9),dp(6));
              RecyclerView.LayoutParams itemLp=new RecyclerView.LayoutParams(-1,-2);
              itemLp.setMargins(0,0,0,dp(7));row.setLayoutParams(itemLp);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(CARD);
                gd.setCornerRadius(dp(14));gd.setStroke(dp(1),STROKE);
                row.setBackground(gd);
             CheckBox box = new CheckBox(MainActivity.this); box.setId(201); box.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
              row.addView(box,new LinearLayout.LayoutParams(dp(40),dp(82)));
             FrameLayout thumbContainer = new FrameLayout(MainActivity.this);
             ImageView thumb = new ImageView(MainActivity.this); thumb.setId(202); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
             thumbContainer.addView(thumb,new FrameLayout.LayoutParams(dp(82),dp(82)));
             ImageView videoIcon = new ImageView(MainActivity.this); videoIcon.setId(205); videoIcon.setImageResource(R.drawable.ic_video); videoIcon.setVisibility(View.GONE);
             FrameLayout.LayoutParams vidLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM | Gravity.START);
             vidLp.setMargins(dp(4), 0, 0, dp(4));
             thumbContainer.addView(videoIcon, vidLp);
             thumbContainer.setBackground(surface(0xff31485A,10,0));thumb.setClipToOutline(true);
              row.addView(thumbContainer,new LinearLayout.LayoutParams(dp(82),dp(82)));
             TextView label=text("",12);label.setId(203);label.setMaxLines(3); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
             LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(8),0,0,0); row.addView(label, lp);
             View marker=new View(MainActivity.this);marker.setId(204);marker.setBackground(surface(ACCENT,4,0));
              marker.setVisibility(View.GONE);marker.setClickable(false);
              row.addView(marker,new LinearLayout.LayoutParams(dp(4),dp(60)));
             return new RecyclerView.ViewHolder(row) {};
         }
     }
     public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int pos) {
         ListItem item = data.get(pos);
         if (item.type == 3) {
             LinearLayout v = (LinearLayout)holder.itemView;
             ((TextView)v.getChildAt(1)).setText(item.text);
         } else if (item.type == 0 || item.type == 4 || item.type == 5) {
             TextView t = (TextView)holder.itemView;
             t.setText(item.text);
             t.setOnClickListener(null);
             if (item.type == 4) {
                 t.setOnClickListener(v -> {
                     expandedGroups.add(item.group);
                     render();
                 });
             } else if (item.type == 5) {
                 t.setOnClickListener(v -> {
                     limit += 20;
                     render();
                 });
             }
         } else if (item.type == 1) {
             View v = holder.itemView;
             ((TextView)v.findViewById(101)).setText(item.text);
              Button quick=v.findViewById(104);
              boolean safeBulk=mode==0||mode==3;
              quick.setEnabled(safeBulk);
              quick.setText(mode==0?"選取重複副本":mode==3?"選取此檔":"請逐一檢視勾選");
             ImageView face = v.findViewById(102); TextView faceLabel = v.findViewById(103);
             if (mode == 2 && item.group.face != null) {
                 face.setVisibility(View.VISIBLE); faceLabel.setVisibility(View.VISIBLE);
                 face.setImageBitmap(BitmapFactory.decodeFile(item.group.face.getPath()));
             } else { face.setVisibility(View.GONE); faceLabel.setVisibility(View.GONE); }
             v.findViewById(104).setOnClickListener(btn -> {
                 if(!safeBulk)return;
                 if(mode==3){selected.addAll(item.group.items);}
                 else if(!item.group.items.isEmpty()){
                     List<Scanner.Item> active = new ArrayList<>();
                     for(Scanner.Item i : item.group.items) if(!i.removed) active.add(i);
                     if(active.size()>1)selected.addAll(active.subList(1,active.size()));
                 }
                 render();
             });
             v.findViewById(105).setOnClickListener(btn -> { selected.removeAll(item.group.items); render(); });
         } else {
             View v = holder.itemView;
             CheckBox box = v.findViewById(201); box.setOnCheckedChangeListener(null); box.setChecked(item.checked);
             box.setOnCheckedChangeListener((cb, checked) -> { if(checked) selected.add(item.item); else selected.remove(item.item); updateSelection(); item.checked = checked; v.findViewById(204).setVisibility(checked?View.VISIBLE:View.GONE); });
             ImageView thumb = v.findViewById(202); thumb.setImageBitmap(null);
             ImageView videoIcon = v.findViewById(205);
             if (videoIcon != null) videoIcon.setVisibility(item.item.video ? View.VISIBLE : View.GONE);
             thumb.setTag(item.item.uri.toString());
             if (item.item.thumb != null) {
                 android.graphics.Bitmap c = thumbCache.get(item.item.thumb.getPath());
                 if (c != null) thumb.setImageBitmap(c);
                 else {
                     final String currentTag = item.item.uri.toString();
                     tw.mediaorganizer.OperationController.getInstance().getThumbWorker().execute(() -> {
                         android.graphics.Bitmap b = smallThumb(item.item.thumb);
                         if(b != null) {
                             thumbCache.put(item.item.thumb.getPath(), b);
                             ui(() -> { if(currentTag.equals(thumb.getTag())) thumb.setImageBitmap(b); });
                         }
                     });
                 }
             } else {
                 thumb.setImageBitmap(null);
             }
             thumb.setOnClickListener(img -> preview(item.item));
             TextView label = v.findViewById(203);
             String textLabel = item.item.name + "\n" + format(item.item.size) + (item.item.video ? " · " + item.item.duration / 1000 + " 秒" : "") + "\n" + item.item.path;


             label.setText(textLabel);
             label.setOnClickListener(lbl -> preview(item.item));
             v.findViewById(204).setVisibility(item.checked?View.VISIBLE:View.GONE);
         }
     }

     @Override
     public void onViewRecycled(androidx.recyclerview.widget.RecyclerView.ViewHolder holder) {
         super.onViewRecycled(holder);
         if (holder.itemView != null) {
             android.widget.ImageView thumb = holder.itemView.findViewById(202);
             if (thumb != null) thumb.setTag(null);
         }
     }
public int getItemCount() { return data.size(); }
 }
 private void render() {
  updateSelection();updateOverview();
  List<ListItem> newData=new ArrayList<>();
  if(result==null||result.all.isEmpty()){
   newData.add(new ListItem(3,null,null,"點選「開始智慧掃描」，讓照片更好找。\n完全重複可以精確比對，相似與人物請自行預覽確認。",false));
   if(adapter!=null)adapter.update(newData);
   return;
  }
  String query=searchInput==null?"":searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
  List<Scanner.Group> groups=mode==0?result.exact:mode==1?result.similar:result.people;
  if(mode==3){
   groups=new ArrayList<>();
   for(Scanner.Item file:result.all)if(!file.removed){
    Scanner.Group single=new Scanner.Group(file.video?"影片":"照片");
    single.items.add(file);groups.add(single);
   }
  }
  int displayed=0;boolean more=false;
  for(Scanner.Group group:groups){
   List<Scanner.Item> active=new ArrayList<>();
   for(Scanner.Item file:group.items)if(!file.removed)active.add(file);
   if(active.size()<(mode==3?1:2))continue;
   if(!query.isEmpty()){
    List<Scanner.Item> matched=new ArrayList<>();
    for(Scanner.Item file:active){
     String name=file.name==null?"":file.name;
     String path=file.path==null?"":file.path;
     if(name.toLowerCase(Locale.ROOT).contains(query)||path.toLowerCase(Locale.ROOT).contains(query))matched.add(file);
    }
    if(matched.isEmpty())continue;
    active=matched;
   }
   if(displayed>=limit){more=true;break;}
   displayed++;
   long bytes=0;for(Scanner.Item file:active)bytes+=Math.max(0,file.size);
   int checked=0;for(Scanner.Item file:active)if(selected.contains(file))checked++;
   String label=group.title+"   ·   "+active.size()+" 個檔案   ·   "+format(bytes);
   if(checked>0)label+="   ·   已選 "+checked;
   newData.add(new ListItem(1,group,null,label,false));
   boolean expand=expandedGroups.contains(group);
   int count=0;
   for(Scanner.Item file:active){
    if(!expand&&count++>=12){
     newData.add(new ListItem(4,group,null,"展開此組全部 "+active.size()+" 個檔案",false));
     break;
    }
    newData.add(new ListItem(2,group,file,null,selected.contains(file)));
   }
  }
  if(more)newData.add(new ListItem(5,null,null,"顯示更多群組",false));
  if(displayed==0){
   String empty=query.isEmpty()?
     (mode==2?"尚無可比對的人物群組。可以啟用人物分析後重新掃描。":"這個分類目前沒有符合項目。可試試其他分類。"):
     "沒有找到符合「"+query+"」的檔案。請換個關鍵字試試。";
   newData.add(new ListItem(3,null,null,empty,false));
  }
  if(adapter!=null)adapter.update(newData);
 }
 private Bitmap smallThumb(File file){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;return BitmapFactory.decodeFile(file.getPath(),options);}
 private View settingsScreen;
 private void showSettingsDialog(){
  if (settingsScreen != null) {
      settingsScreen.setVisibility(View.VISIBLE);
      mainContent.setVisibility(View.GONE);
      return;
  }

  ScrollView scroll = new ScrollView(this);
  scroll.setBackgroundColor(BG);
  settingsScreen = scroll;

  LinearLayout rootLayout = new LinearLayout(this);
  rootLayout.setOrientation(LinearLayout.VERTICAL);
  rootLayout.setPadding(dp(16), dp(16), dp(16), dp(32));
  scroll.addView(rootLayout);

  // Header
  LinearLayout header = new LinearLayout(this);
  header.setGravity(Gravity.CENTER_VERTICAL);
  header.setPadding(0, 0, 0, dp(24));
  ImageView backBtn = new ImageView(this);
  backBtn.setId(R.id.btn_back);
  backBtn.setContentDescription(getString(R.string.desc_back));
  backBtn.setImageResource(R.drawable.ic_back);
  backBtn.setColorFilter(TEXT);backBtn.setBackground(surface(CARD,14,STROKE));
  backBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
  android.util.TypedValue outValueBack = new android.util.TypedValue();
  if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValueBack, true)) {
      backBtn.setBackgroundResource(outValueBack.resourceId);
  }
  backBtn.setOnClickListener(v -> {
      settingsScreen.setVisibility(View.GONE);
      mainContent.setVisibility(View.VISIBLE);
  });
  header.addView(backBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));

  TextView title = text("設定", 20);
  title.setTypeface(null, 1);
  LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
  titleLp.setMargins(dp(16), 0, 0, 0);
  header.addView(title, titleLp);

  rootLayout.addView(header);

  // Group: 掃描範圍
  rootLayout.addView(settingsTitle("掃描範圍"));
  LinearLayout scanScopeCard = createCard();
  if(srcLabel.getParent()!=null)((ViewGroup)srcLabel.getParent()).removeView(srcLabel);
  srcLabel.setTextSize(14);
  scanScopeCard.addView(srcLabel, new LinearLayout.LayoutParams(-1, -2));

  Button srcBtn = button("選擇來源資料夾", this::selectSourceFolder);
  LinearLayout.LayoutParams srcBtnLp = new LinearLayout.LayoutParams(-1, dp(42));
  srcBtnLp.setMargins(0, dp(12), 0, 0);
  scanScopeCard.addView(srcBtn, srcBtnLp);
  rootLayout.addView(scanScopeCard);

  // Group: 分析選項
  rootLayout.addView(settingsTitle("分析選項"));
  LinearLayout analysisCard = createCard();

  if(people.getParent()!=null)((ViewGroup)people.getParent()).removeView(people);
  people.setText("同時分析人物 (較耗時)");
  people.setTextColor(TEXT);
  analysisCard.addView(people, new LinearLayout.LayoutParams(-1, -2));

  if(bgScan.getParent()!=null)((ViewGroup)bgScan.getParent()).removeView(bgScan);
  bgScan.setText("鎖屏後繼續掃描");
  bgScan.setTextColor(TEXT);
  LinearLayout.LayoutParams bgScanLp = new LinearLayout.LayoutParams(-1, -2);
  bgScanLp.setMargins(0, dp(8), 0, 0);
  analysisCard.addView(bgScan, bgScanLp);

  TextView bgHint = text("開啟以在背景持續掃描，不因螢幕關閉中斷", 12);
  bgHint.setTextColor(TEXT_SEC);
  bgHint.setPadding(dp(32), 0, 0, 0);
  analysisCard.addView(bgHint);

  LinearLayout mBox = new LinearLayout(this); mBox.setGravity(Gravity.CENTER_VERTICAL);
  mBox.setPadding(dp(8), dp(16), 0, 0);
  mBox.addView(text("媒體種類：", 14));
  if(mediaTypeSpinner.getParent()!=null)((ViewGroup)mediaTypeSpinner.getParent()).removeView(mediaTypeSpinner);
  mBox.addView(mediaTypeSpinner, new LinearLayout.LayoutParams(-1, -2));
  analysisCard.addView(mBox);

  LinearLayout sBox = new LinearLayout(this); sBox.setGravity(Gravity.CENTER_VERTICAL);
  sBox.setPadding(dp(8), dp(16), 0, dp(8));
  sBox.addView(text("相似程度：", 14));
  if(strictness.getParent()!=null)((ViewGroup)strictness.getParent()).removeView(strictness);
  sBox.addView(strictness, new LinearLayout.LayoutParams(-1, -2));
  analysisCard.addView(sBox);

  rootLayout.addView(analysisCard);

  // Group: 工具
  rootLayout.addView(settingsTitle("工具"));
  LinearLayout toolsCard = createCard();

  Button undoBtn = button("復原上次移動", this::undo);
  toolsCard.addView(undoBtn, new LinearLayout.LayoutParams(-1, dp(42)));

  Button cacheBtn = button("清除縮圖快取", ()->{
    selected.clear();result=null;clearThumbs();status.setText("縮圖快取已清除，請重新掃描。");render();
  });
  LinearLayout.LayoutParams cacheBtnLp = new LinearLayout.LayoutParams(-1, dp(42));
  cacheBtnLp.setMargins(0, dp(12), 0, 0);
  toolsCard.addView(cacheBtn, cacheBtnLp);

  Button helpBtn = button("使用說明與隱私", this::help);
  LinearLayout.LayoutParams helpBtnLp = new LinearLayout.LayoutParams(-1, dp(42));
  helpBtnLp.setMargins(0, dp(12), 0, 0);
  toolsCard.addView(helpBtn, helpBtnLp);

  rootLayout.addView(toolsCard);

  // App Version
  String verName="1.4";
  try{verName=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){}
  TextView ver = text("相簿整理員 v" + verName, 12);
  ver.setTextColor(TEXT_SEC);
  ver.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(-1, -2);
  verLp.setMargins(0, dp(24), 0, dp(16));
  rootLayout.addView(ver, verLp);

  root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
  mainContent.setVisibility(View.GONE);
 }

 private TextView settingsTitle(String titleText) {
     TextView t = text(titleText, 14);
     t.setTextColor(ACCENT);t.setTypeface(null,Typeface.BOLD);
     t.setPadding(dp(8), dp(18), 0, dp(8));
     return t;
 }

 private LinearLayout createCard(){
  LinearLayout card=new LinearLayout(this);
  card.setOrientation(1);card.setBackground(surface(CARD,16,STROKE));
  card.setPadding(dp(16),dp(16),dp(16),dp(16));
  return card;
 }
 private String format(long bytes){return String.format(Locale.TAIWAN,"%.1f MB",bytes/1048576.0);}
 private void updateSelection(){
  long total=0;for(Scanner.Item i:selected)total+=i.size;
  selection.setText("已選 "+selected.size()+" 個 · "+format(total));
  if(scan!=null)scan.setEnabled(!busy&&!op().fileBusy());
  if(move!=null)move.setEnabled(!selected.isEmpty()&&!op().fileBusy());
  if(deleteBtn!=null)deleteBtn.setEnabled(!selected.isEmpty()&&!op().fileBusy());
  if(footerContainer!=null) footerContainer.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
 }
 private void preview(Scanner.Item item){try{Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(item.uri,item.mime!=null?item.mime:item.video?"video/*":"image/*");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);}catch(ActivityNotFoundException e){new androidx.appcompat.app.AlertDialog.Builder(this).setMessage("手機沒有可開啟此媒體的應用程式。").setPositiveButton("確定",null).show();}}
 private String validateDestination(String input){
  if(input==null)input="";input=input.trim();
  if(input.endsWith("/"))input=input.substring(0,input.length()-1);
  if(input.isEmpty()||input.equals(".")||input.equals("..")||input.matches(".*[*?\"<>|\\p{Cntrl}].*")||input.length()>120){status.setText("請填寫有效的資料夾名稱或路徑。");return null;}
  List<String> existing=getMediaSubfolders();
  boolean isExistingOrRoot=existing.contains(input)||input.contains("/")||Arrays.asList("DCIM","Pictures","Movies","Download","Documents","Music").contains(input);
  String target=isExistingOrRoot?input+"/":"DCIM/"+input+"/";
  getPreferences(0).edit().putString("folder",input).apply();return target;
 }
 private void confirmMove(){
  if(selected.isEmpty()||op().fileBusy())return;
  List<String> folders=getMediaSubfolders();
  folders.add("[ 新增/手動輸入資料夾… ]");
  String[] items=folders.toArray(new String[0]);
  new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("選擇移入的目的資料夾").setItems(items,(dialog,which)->{
   if(which==items.length-1){
    EditText input=new EditText(this);
    input.setHint("請輸入目的資料夾名稱");
    input.setText(getPreferences(0).getString("folder","整理收藏"));
    new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("輸入目的資料夾名稱").setView(input).setPositiveButton("確定",(d,w)->promptMoveConfirmation(input.getText().toString())).setNegativeButton("取消",null).show();
   }else{
    promptMoveConfirmation(items[which]);
   }
  }).setNegativeButton("取消",null).show();
 }
 private void promptMoveConfirmation(String folderName){
  String target=validateDestination(folderName);if(target==null)return;ArrayList<Scanner.Item> snapshot=new ArrayList<>(selected);
  new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("確認移動 "+snapshot.size()+" 個檔案").setMessage("目的地："+target+"\n移動後會離開原資料夾；不刪除重複檔。相似與人物分組請先確認。\n同名檔會加上識別碼，不覆蓋既有檔案。\n這次成功的移動會取代上次復原紀錄。").setNegativeButton("取消",null).setPositiveButton("移動",(d,w)->{op().setDestination(target);op().setPending(new ArrayList<>());for(Scanner.Item item:snapshot)if(!target.equals(item.path)){MoveRecord r=new MoveRecord();r.uri=item.uri;r.oldPath=item.path;r.oldName=item.name;r.newPath=target;r.newName=item.name;r.item=item;op().getPending().add(r);}op().setUndoing(false);op().setDeleting(false);op().setOperationHasSuccess(false);op().setOperationMoved(0);op().setOperationFailed(0);op().setFileBusy(true);backupJournal();updateSelection();requestWrite();}).show();
 }
 private void confirmDelete(){
  if(selected.isEmpty()||op().fileBusy())return;
  ArrayList<Scanner.Item> snapshot=new ArrayList<>(selected);
  long bytes=0;for(Scanner.Item item:snapshot)bytes+=Math.max(0,item.size);
  String warning="將刪除 "+snapshot.size()+" 個檔案（"+format(bytes)+"）。\n\n"
     +"這是真正刪除相簿中的原始檔案，不是從清單移除，App 無法復原。"
     +"\n相似照片或人物群組不代表內容完全重複，請逐一預覽確認。";
  new androidx.appcompat.app.AlertDialog.Builder(this)
    .setTitle("確認刪除原始檔案？")
    .setMessage(warning)
    .setNegativeButton("保留檔案",null)
    .setPositiveButton("確認刪除",(d,w)->beginDelete(snapshot))
    .show();
 }
 private void beginDelete(ArrayList<Scanner.Item> snapshot){
  if(snapshot.isEmpty()||busy||op().fileBusy())return;
  op().setPendingDeletes(snapshot);
  op().setDeleting(true);
  op().setFileBusy(true);
  op().setOperationDeleted(0);op().setOperationDeleteFailed(0);
  updateSelection();
  requestDelete();
 }
 private void requestDelete(){
  if(op().getPendingDeletes()==null||op().getPendingDeletes().isEmpty()){
   op().setFileBusy(false);updateSelection();
   status.setText("刪除完成 · 成功 "+op().getOperationDeleted()+" · 失敗 "+op().getOperationDeleteFailed());
   render();
   return;
  }
  tw.mediaorganizer.OperationController.getInstance().getFileWorker().execute(()->{
   java.util.List<Scanner.Item> directList=new java.util.ArrayList<>(op().getPendingDeletes());
   java.util.List<Scanner.Item> needPermissionList=new java.util.ArrayList<>();
   int directDeleted=0;
   for(Scanner.Item item:directList){
    boolean exists = false;
    boolean queryFailed = false;
    try (android.database.Cursor c = getContentResolver().query(item.uri, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
        if (c != null && c.moveToFirst()) exists = true;
    } catch(Exception e) { queryFailed = true; }
    if (queryFailed) {
        needPermissionList.add(item);
        continue;
    }
    if (!exists) {
        item.removed=true;
        ui(()->selected.remove(item));
        directDeleted++;
        continue;
    }
    try{
     int count=getContentResolver().delete(item.uri,null,null);
     if(count>0){
      item.removed=true;
      ui(()->selected.remove(item));
      directDeleted++;
     }else{
      needPermissionList.add(item);
     }
    }catch(Exception e){needPermissionList.add(item);}
   }
   final int d=directDeleted;
   ui(()->{
    op().setOperationDeleted(op().getOperationDeleted() + d);
    op().setPendingDeletes(needPermissionList);
    if(op().getPendingDeletes().isEmpty()){
     op().setFileBusy(false);updateSelection();
     clearOpState();
     saveScanCache(result);
     status.setText("刪除完成 · 成功 "+op().getOperationDeleted()+" · 失敗 "+op().getOperationDeleteFailed());
     render();
    }else{
     requestDeleteBatch();
    }
   });
  });
 }

    private void saveOpState() {
        try {
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("isDeleting", op().isDeleting());
            root.put("undoing", op().undoing());
            root.put("operationHasSuccess", op().operationHasSuccess());
            root.put("operationMoved", op().getOperationMoved());
            root.put("operationFailed", op().getOperationFailed());
            root.put("operationDeleted", op().getOperationDeleted());
            root.put("operationDeleteFailed", op().getOperationDeleteFailed());
            if (op().getPendingDeletes() != null && !op().getPendingDeletes().isEmpty()) {
                org.json.JSONArray deletes = new org.json.JSONArray();
                for (Scanner.Item i : op().getPendingDeletes()) deletes.put(i.uri.toString());
                root.put("pendingDeletes", deletes);
            }
            if (op().getPending() != null && !op().getPending().isEmpty()) {
                org.json.JSONArray moves = new org.json.JSONArray();
                for (MoveRecord r : op().getPending()) {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("uri", r.uri.toString());
                    if (r.oldPath != null) o.put("oldPath", r.oldPath);
                    if (r.oldName != null) o.put("oldName", r.oldName);
                    if (r.newPath != null) o.put("newPath", r.newPath);
                    if (r.newName != null) o.put("newName", r.newName);
                    moves.put(o);
                }
                root.put("pending", moves);
            }
            java.io.File tmp = new java.io.File(getFilesDir(), "op-state.tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                out.write(root.toString().getBytes("UTF-8"));
                out.getFD().sync();
            }
            tmp.renameTo(new java.io.File(getFilesDir(), "op-state.json"));
        } catch (Exception e) {}
    }

    private void loadOpState() {
        File f = new File(getFilesDir(), "op-state.json");
        if (!f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            JSONObject root = new JSONObject(out.toString("UTF-8"));

            op().setDeleting(root.optBoolean("isDeleting", false));
            op().setUndoing(root.optBoolean("undoing", false));
            op().setOperationHasSuccess(root.optBoolean("operationHasSuccess", false));
            op().setOperationMoved(root.optInt("operationMoved", 0));
            op().setOperationFailed(root.optInt("operationFailed", 0));
            op().setOperationDeleted(root.optInt("operationDeleted", 0));
            op().setOperationDeleteFailed(root.optInt("operationDeleteFailed", 0));

            if (root.has("pendingDeletes")) {
                op().setPendingDeletes(new java.util.ArrayList<>());
                JSONArray deletes = root.getJSONArray("pendingDeletes");
                for (int i = 0; i < deletes.length(); i++) {
                    Scanner.Item item = new Scanner.Item();
                    item.uri = Uri.parse(deletes.getString(i));
                    boolean exists = false;
                    try (android.database.Cursor c = getContentResolver().query(item.uri, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
                        if (c != null && c.moveToFirst()) exists = true;
                    } catch (Exception e) {}
                    if (!exists) { op().addOperationDeleted(1); continue; }
                    if (result != null) {
                        for (Scanner.Item existing : result.all) {
                            if (existing.uri.equals(item.uri)) { item = existing; break; }
                        }
                    }
                    op().getPendingDeletes().add(item);
                }
                if(op().getPendingDeletes().isEmpty()) { op().setPendingDeletes(null); } else { op().setFileBusy(true); }
            }

            if (root.has("pending")) {
                op().setPending(new java.util.ArrayList<>());
                JSONArray moves = root.getJSONArray("pending");
                for (int i = 0; i < moves.length(); i++) {
                    JSONObject mo = moves.getJSONObject(i);
                    MoveRecord r = new MoveRecord();
                    r.uri = Uri.parse(mo.getString("uri"));
                    r.oldPath = mo.optString("oldPath", null);
                    r.oldName = mo.optString("oldName", null);
                    r.newPath = mo.optString("newPath", null);
                    r.newName = mo.optString("newName", null);
                    boolean exists = false;
                    try (android.database.Cursor c = getContentResolver().query(r.uri, new String[]{MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            exists = true;
                            String currentPath = c.getString(0);
                            if (r.newPath != null && currentPath != null && r.newPath.equals(currentPath)) {
                                op().setOperationMoved(op().getOperationMoved() + 1);
                                continue;
                            }
                        }
                    } catch (Exception e) {}
                    if (!exists) { op().setOperationFailed(op().getOperationFailed() + 1); continue; }
                    if (result != null) {
                        for (Scanner.Item existing : result.all) {
                            if (existing.uri.equals(r.uri)) { r.item = existing; break; }
                        }
                    }
                    op().getPending().add(r);
                }
                if(op().getPending().isEmpty()) { op().setPending(null); } else { op().setFileBusy(true); }
            }
        } catch (Exception e) {}
    }

    private void clearOpState() {
        File f = new File(getFilesDir(), "op-state.json");
        if (f.exists()) f.delete();
    }

 private void requestDeleteBatch(){
  if(op().getPendingDeletes()==null||op().getPendingDeletes().isEmpty()){
   op().setFileBusy(false);
   clearOpState();
   saveScanCache(result);
   updateSelection();
   status.setText("刪除完成 · 成功 "+op().getOperationDeleted()+" · 失敗 "+op().getOperationDeleteFailed());
   render();
   return;
  }
  saveOpState();
  try{
   ArrayList<Uri> uris=new ArrayList<>();
   for(int i=0;i<Math.min(100,op().getPendingDeletes().size());i++)uris.add(op().getPendingDeletes().get(i).uri);
   startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(),uris).getIntentSender(),52,null,0,0,0);
  }catch(Exception e){
   status.setText("刪除已完成（直接刪除 "+op().getOperationDeleted()+" 個，需要授權或失敗 "+op().getPendingDeletes().size()+" 個）");
   op().setPendingDeletes(null);
   op().setFileBusy(false);
   saveScanCache(result);
   updateSelection();
   render();
  }
 }
 private void finishMoveOperation(){
  op().setPending(null);op().setFileBusy(false);
  if(!op().undoing()){
   if(op().getOperationMoved()==0)restoreJournalBackup();else clearJournalBackup();
   saveScanCache(result);
  }else{
   result=null;selected.clear();clearThumbs();
  }
  clearOpState();
  updateSelection();
  render();
 }
 private void requestWrite(){if(op().getPending()==null||op().getPending().isEmpty()){
  status.setText((op().undoing()?"復原":"移動")+"完成 · 成功 "+op().getOperationMoved()+" · 失敗 "+op().getOperationFailed()+(op().undoing()?"；請重新掃描更新分組。":"；可繼續勾選移到同一資料夾。"));
  clearOpState();
  finishMoveOperation();
  op().setOperationHasSuccess(false);op().setOperationMoved(0);op().setOperationFailed(0);
  return;
 }
  saveOpState();
  backupJournal();
  try{ArrayList<Uri> uris=new ArrayList<>();for(int i=0;i<Math.min(100,op().getPending().size());i++)uris.add(op().getPending().get(i).uri);startIntentSenderForResult(MediaStore.createWriteRequest(getContentResolver(),uris).getIntentSender(),51,null,0,0,0);}catch(Exception e){status.setText("無法取得移動授權："+e.getMessage());finishMoveOperation();}}
 public void onActivityResult(int request,int response,Intent data){super.onActivityResult(request,response,data);
  if(request==51){if(response==RESULT_OK)applyBatch();else{
   status.setText("未獲授權 · 已完成：成功 "+op().getOperationMoved()+" · 失敗 "+op().getOperationFailed()+"。未授權之檔案已保留可重試。");
   op().setPending(null);
   clearOpState();
   finishMoveOperation();
   op().setOperationHasSuccess(false);op().setOperationMoved(0);op().setOperationFailed(0);
  }}
  else if(request==52){if(response==RESULT_OK)applyDeleteBatch();else{
   status.setText("未獲刪除授權 · 已完成：成功 "+op().getOperationDeleted()+" · 失敗 "+op().getOperationDeleteFailed()+"。未授權之檔案已保留。");
   if(op().getOperationDeleted()>0)saveScanCache(result);
   op().setPendingDeletes(null);
   clearOpState();
   op().setFileBusy(false);updateSelection();render();
  }}
 }

 private void applyDeleteBatch(){
  if(op().getPendingDeletes()==null)return;
  final List<Scanner.Item> batch=new ArrayList<>(op().getPendingDeletes().subList(0,Math.min(100,op().getPendingDeletes().size())));
  for(Scanner.Item item:batch){item.removed=true;selected.remove(item);}
  op().getPendingDeletes().removeAll(batch);
  op().setOperationDeleted(op().getOperationDeleted() + batch.size());
  if(op().getPendingDeletes()!=null&&!op().getPendingDeletes().isEmpty()){
   requestDeleteBatch();
  }else{
   op().setPendingDeletes(null);
   op().setFileBusy(false);
   clearOpState();
   saveScanCache(result);
   status.setText("刪除完成 · 成功 "+op().getOperationDeleted()+" · 失敗 "+op().getOperationDeleteFailed());
   render();
  }
 }

 private void applyBatch(){
  if(op().getPending()==null)return;final java.util.List<MoveRecord> batch=new java.util.ArrayList<>(op().getPending().subList(0,Math.min(100,op().getPending().size())));
  tw.mediaorganizer.OperationController.getInstance().getFileWorker().execute(()->{int moved=0,failed=0;java.util.List<MoveRecord> successfulRecords=new java.util.ArrayList<>();for(MoveRecord r:batch){try{
    if(r.newPath==null||r.newPath.isEmpty()||(!op().undoing()&&(r.oldPath==null||r.oldPath.isEmpty())))throw new java.io.IOException("原資料夾紀錄不完整");

    boolean alreadyMoved = false;
    try (android.database.Cursor c = getContentResolver().query(r.uri, new String[]{MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
        if (c != null && c.moveToFirst()) {
            String currentPath = c.getString(0);
            String currentName = c.getString(1);
            if (currentPath != null && currentPath.equals(r.newPath) && currentName != null && currentName.equals(r.newName)) {
                alreadyMoved = true;
            }
        } else {
             throw new java.io.IOException("檔案不存在");
        }
    }
    if (alreadyMoved) { moved++; successfulRecords.add(r); if(r.item!=null)r.item.removed=true; continue; }

    String finalName=collision(r.uri,r.newPath,r.newName)?uniqueName(r.newName):r.newName;
    android.content.ContentValues values=new android.content.ContentValues();values.put(MediaStore.MediaColumns.RELATIVE_PATH,r.newPath);values.put(MediaStore.MediaColumns.DISPLAY_NAME,finalName);
    r.newName = finalName;
    if(!op().undoing()){appendJournalPending(r);}
    int updated=getContentResolver().update(r.uri,values,null,null);if(updated!=1)throw new java.io.IOException("檔案更新失敗");moved++;
    if(!op().undoing()){if(!op().operationHasSuccess()){op().setOperationHasSuccess(true);}}
    if(op().undoing()){removeJournal(r.uri);}
    if(r.item!=null){r.item.removed=true;}
    successfulRecords.add(r);
   }catch(Exception e){failed++;}}
   final int m=moved,f=failed;ui(()->{op().setOperationMoved(op().getOperationMoved() + m);op().setOperationFailed(op().getOperationFailed() + f);
    if(!op().undoing() && !successfulRecords.isEmpty()) {
     try { promotePendingJournal(); } catch(Exception e){}
    }
    if(op().getPending()!=null)op().getPending().removeAll(batch);
    if(!op().undoing())for(MoveRecord r:successfulRecords)if(r.item!=null)selected.remove(r.item);
    if(op().getPending()!=null&&!op().getPending().isEmpty()){status.setText("已處理 "+(op().getOperationMoved()+op().getOperationFailed())+" 個（成功 "+op().getOperationMoved()+"），繼續下一批…");requestWrite();}
    else{
     status.setText((op().undoing()?"復原":"移動")+"完成 · 成功 "+op().getOperationMoved()+" · 失敗 "+op().getOperationFailed()+(op().undoing()?"；請重新掃描更新分組。":"；可繼續勾選移到同一資料夾。"));
     finishMoveOperation();
     op().setOperationHasSuccess(false);op().setOperationMoved(0);op().setOperationFailed(0);
    }});
  });
 }
 public boolean collision(Uri self,String path,String name){try(android.database.Cursor c=getContentResolver().query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),new String[]{"_id"},"relative_path = ? AND _display_name = ?",new String[]{path,name},null)){if(c!=null)while(c.moveToNext())if(c.getLong(0)!=ContentUris.parseId(self))return true;}return false;}
 public String uniqueName(String name){int i=name.lastIndexOf('.');String id="_"+UUID.randomUUID().toString().substring(0,8);return i>0?name.substring(0,i)+id+name.substring(i):name+id;}
 private File journal(){return new File(getFilesDir(),"move-journal.json");}
 private File journalPending(){return new File(getFilesDir(),"move-journal-pending.json");}
 private File journalBackup(){return new File(getFilesDir(),"move-journal-backup.json");}
 private synchronized void backupJournal(){
  File jp=journalPending();
  File bk=journalBackup();
  if(jp.exists()) jp.renameTo(bk);
 }
 private synchronized void restoreJournalBackup(){
  File jp=journalPending();
  File bk=journalBackup();
  if(bk.exists() && !jp.exists()) bk.renameTo(jp);
 }
 private synchronized void clearJournalBackup(){
  File bk=journalBackup();
  if(bk.exists()) bk.delete();
 }
 private synchronized JSONArray readJournal()throws Exception{if(!journal().exists())return new JSONArray();try(InputStream in=new FileInputStream(journal())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return new JSONArray(out.toString("UTF-8"));}}
 private synchronized JSONArray readJournalPending()throws Exception{if(!journalPending().exists())return new JSONArray();try(InputStream in=new FileInputStream(journalPending())){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return new JSONArray(out.toString("UTF-8"));}}
 private synchronized void writeJournal(JSONArray a)throws Exception{File tmp=new File(getFilesDir(),"move-journal.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(a.toString().getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(journal()))throw new IOException("復原紀錄儲存失敗");}
 private synchronized void writeJournalPending(JSONArray a)throws Exception{File tmp=new File(getFilesDir(),"move-journal-pending.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(a.toString().getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(journalPending()))throw new IOException("暫存復原紀錄儲存失敗");}
 public synchronized void appendJournalPending(MoveRecord r)throws Exception{org.json.JSONArray a=readJournalPending();org.json.JSONObject o=new org.json.JSONObject();o.put("uri",r.uri.toString());o.put("path",r.oldPath);o.put("name",r.oldName);o.put("newPath", r.newPath);o.put("newName", r.newName);a.put(o);writeJournalPending(a);}
 public synchronized void promotePendingJournal()throws Exception{
  org.json.JSONArray pending = readJournalPending();
  if (pending.length() > 0) {
   org.json.JSONArray valid = new org.json.JSONArray();
   for (int i = 0; i < pending.length(); i++) {
       org.json.JSONObject o = pending.getJSONObject(i);
       Uri uri = Uri.parse(o.getString("uri"));
       String expectedPath = o.optString("newPath", null);
       String expectedName = o.optString("newName", null);

       if (expectedPath == null || expectedName == null) continue;

       try (android.database.Cursor c = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
           if (c != null && c.moveToFirst()) {
               String currentPath = c.getString(0);
               String currentName = c.getString(1);
               if (currentPath != null && currentName != null && expectedPath.equals(currentPath) && currentName.equals(expectedName)) {
                   valid.put(o);
               }
           }
       } catch (Exception e) {}
   }
   if (valid.length() > 0) {
       org.json.JSONArray existing = readJournal();
       for(int j=0; j<valid.length(); j++) {
           existing.put(valid.getJSONObject(j));
       }
       writeJournal(existing);
   }
   java.io.File tmp = new java.io.File(getFilesDir(), "move-journal-pending.json");
   if (tmp.exists()) tmp.delete();
  }
 }
 public synchronized void removeJournal(Uri uri)throws Exception{JSONArray a=readJournal(),next=new JSONArray();for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).getString("uri").equals(uri.toString()))next.put(a.getJSONObject(i));writeJournal(next);}
 private void undo(){if(op().fileBusy()||busy)return;try{JSONArray a=readJournal();if(a.length()==0){status.setText("沒有可復原的移動紀錄。");return;}new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("復原上次移動").setMessage("將 "+a.length()+" 個紀錄移回原資料夾。同名檔會加識別碼；已不存在的檔案會列為失敗。").setNegativeButton("取消",null).setPositiveButton("復原",(d,w)->{try{op().setPending(new ArrayList<>());for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);MoveRecord r=new MoveRecord();r.uri=Uri.parse(o.getString("uri"));r.newPath=o.getString("path");r.newName=o.getString("name");op().getPending().add(r);}op().setUndoing(true);op().setOperationHasSuccess(false);op().setOperationMoved(0);op().setOperationFailed(0);op().setFileBusy(true);updateSelection();requestWrite();}catch(Exception e){status.setText("復原紀錄無法讀取。");}}).show();}catch(Exception e){status.setText("復原紀錄無法讀取。");}}
 private void help(){new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("使用說明與隱私").setMessage("1. 授權所有照片與影片後開始掃描。可篩選來源資料夾，或對全相簿掃描。\n2. 重複採 SHA-256；相似照片使用感知雜湊、色彩與比例，影片抽取 7 個畫面。\n3. 人物在手機內用 MobileFaceNet 比對抽樣人臉；分數不是準確率。正面清楚人臉效果較好。\n4. 掃描過程中即可即時勾選檔案進行『移到此資料夾』或『刪除』。\n5. 不自動刪除、不上傳、不在背景持續監控。\n6. 復原只保留上次移動批次。清快取只移除本程式縮圖，不動原照片／影片。").setPositiveButton("關閉",null).show();}
 private void clearThumbs(){resultGeneration = ScanService.clearCacheAsync(this);}
 private File cacheFile(){return new File(getFilesDir(),"scan-cache.json");}
 public void saveScanCache(Scanner.Result res){
  if(res==null)return;
  ScanService.saveScanCache(this, res, resultGeneration);
 }
 private Scanner.Result loadScanCache(){
  if(!cacheFile().exists())return null;
  try(java.io.InputStream in=new java.io.FileInputStream(cacheFile())){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);
   org.json.JSONObject root=new org.json.JSONObject(out.toString("UTF-8"));
   Scanner.Result res=new Scanner.Result();org.json.JSONArray all=root.getJSONArray("all");
   for(int i=0;i<all.length();i++){
    org.json.JSONObject o=all.getJSONObject(i);Scanner.Item item=new Scanner.Item();item.uri=Uri.parse(o.getString("uri"));item.name=o.optString("name","");item.mime=o.optString("mime",null);item.path=o.optString("path","");item.size=o.optLong("size",0);item.duration=o.optLong("duration",0);item.video=o.optBoolean("video",false);item.removed=o.optBoolean("removed",false);
    if(o.has("thumb"))item.thumb=new java.io.File(o.getString("thumb"));res.all.add(item);
   }
   res.exact.addAll(jsonToGroups(root.getJSONArray("exact"),res.all));res.similar.addAll(jsonToGroups(root.getJSONArray("similar"),res.all));res.people.addAll(jsonToGroups(root.getJSONArray("people"),res.all));
   res.unreadable=root.optInt("unreadable",0);res.faceFailures=root.optInt("faceFailures",0);res.faceError=root.optString("faceError","");

   if(root.has("generation")) {
       long cacheGen = root.getLong("generation");
       if(ScanService.getGeneration() == 0) {
           ScanService.setGeneration(cacheGen);
        resultGeneration = cacheGen;
       } else if (cacheGen != ScanService.getGeneration()) {
           return null;
       }
   }
   return res;
  }catch(Exception e){return null;}
 }
 private List<Scanner.Group> jsonToGroups(JSONArray arr,List<Scanner.Item> allItems)throws Exception{
  List<Scanner.Group> list=new ArrayList<>();
  for(int i=0;i<arr.length();i++){
   JSONObject o=arr.getJSONObject(i);Scanner.Group g=new Scanner.Group(o.optString("title",""));if(o.has("face"))g.face=new File(o.getString("face"));
   JSONArray items=o.getJSONArray("items");for(int j=0;j<items.length();j++){int idx=items.getInt(j);if(idx>=0&&idx<allItems.size())g.items.add(allItems.get(idx));}
   list.add(g);
  }
  return list;
 }

 @Override
 public void onBackPressed() {
     if (settingsScreen != null && settingsScreen.getVisibility() == View.VISIBLE) {
         settingsScreen.setVisibility(View.GONE);
         mainContent.setVisibility(View.VISIBLE);
         return;
     }
     super.onBackPressed();
 }

 protected void onDestroy(){
  dead=true;
  ScanService.removeListener(this);
    super.onDestroy();
 }
}

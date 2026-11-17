package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.webkit.*;
import android.webkit.CookieManager;
import android.widget.*;
import android.view.Gravity;
import android.view.View;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** WebView host with document-picker imports and user-approved file exports. */
public final class ManagementActivity extends Activity {
    private WebView web;
    private Ui ui;
    private String origin, key, currentView;
    private boolean remoteMode;
    private WebMessagePort exportPort;
    private TextView title,parentLabel;
    private LinearLayout navigation;
    private final java.util.Map<String,String> pageTitles=new java.util.HashMap<>();
    private ProgressBar progress;
    private LinearLayout error;
    private ScrollView errorScroll;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] exportBytes;
    private String exportMime, exportName;
    private boolean pageLoaded;
    private int editorEpoch;
    private boolean rendererGone;
    private Runnable leaveAction;
    private AlertDialog nativeLeaveDialog;
    private long leaveEpoch;
    private String leaveRequest="";
    private final android.os.Handler main = new android.os.Handler(Looper.getMainLooper());
    private static final int PICK = 30, SAVE = 31;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); ui = new Ui(this);
        android.content.SharedPreferences p = getSharedPreferences("host", MODE_PRIVATE);
        remoteMode = getIntent().getBooleanExtra("remote", false);
        origin = remoteMode ? p.getString("remoteAddress", "") : GatewayService.managementOrigin;
        key = remoteMode ? p.getString("remoteKey", "") : GatewayService.key(this);
        currentView = getIntent().getStringExtra("view"); if (currentView == null) currentView = "providers";
        LinearLayout root = ui.column(); root.setBackgroundColor(ui.bg);
        LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(8),ui.dp(5),ui.dp(8),ui.dp(5));toolbar.setBackgroundColor(ui.card);
        toolbar.addView(ui.iconButton("back","返回"+parentName(),this::onBackPressed));LinearLayout heading=ui.column();title=ui.text("Magpie",17,ui.ink,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(title);parentLabel=ui.text("来自"+parentName(),12,ui.muted,false);heading.addView(parentLabel);ui.grow(toolbar,heading);
        toolbar.addView(ui.iconButton("refresh","刷新页面",this::confirmReload));root.addView(toolbar);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress,new LinearLayout.LayoutParams(-1,ui.dp(2)));
        error=ui.column(); error.setPadding(ui.dp(24),ui.dp(28),ui.dp(24),ui.dp(20)); FrameLayout surface=new FrameLayout(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));errorScroll=new ScrollView(this);errorScroll.addView(error);errorScroll.setVisibility(View.GONE);surface.addView(errorScroll,new FrameLayout.LayoutParams(-1,-1));
        web = new WebView(this); web.setBackgroundColor(ui.bg); surface.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString()+" MagpieAndroid/1.11"); s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                // Public bundled fonts only; no file access or credential bridge.
                if(CoreConfig.sameOrigin(r.getUrl().toString(),origin)){
                    String path=r.getUrl().getPath();
                    if("/__magpie_android/fonts/NotoSansSC.ttf".equals(path)||"/__magpie_android/fonts/Inter.ttf".equals(path)){
                        try{return new WebResourceResponse("font/ttf",null,getAssets().open("fonts/"+path.substring(path.lastIndexOf('/')+1)));}
                        catch(IOException ignored){}
                    }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                String url=r.getUrl().toString();
                if (CoreConfig.sameOrigin(url,origin)) return false;
                openExternal(url); return true;
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap icon) {
                pageLoaded=false;editorEpoch++; closeExportPort(); progress.setVisibility(android.view.View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v,String url) {
                progress.setVisibility(android.view.View.GONE);
                if (!CoreConfig.sameOrigin(url,origin)) { v.stopLoading(); return; }
                if(errorScroll.getVisibility()==android.view.View.VISIBLE){pageLoaded=false;closeExportPort();return;}
                pageLoaded=true; CookieManager.getInstance().flush();
                try {
                    String css=readAsset("mobile.css"), js=readAsset("mobile.js");
                    String token = CoreConfig.newKey();
                    String channel = readAsset("channel.js")+"("+JSONObject.quote(token)+");";
                    v.evaluateJavascript("window.magpieAndroidLocal="+(!remoteMode)+";(function(){if(!document.getElementById('android-css')){var s=document.createElement('style');s.id='android-css';s.textContent="+JSONObject.quote(css)+";document.head.appendChild(s);}})();"+js+channel, ignored -> {
                        if(isFinishing()||!CoreConfig.sameOrigin(web.getUrl(),origin))return;
                        closeExportPort(); WebMessagePort[] ports = web.createWebMessageChannel(); exportPort = ports[0];
                        exportPort.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){
                            @Override public void onMessage(WebMessagePort port,WebMessage message){
                                if(port==exportPort&&CoreConfig.sameOrigin(web.getUrl(),origin))handleExportMessage(message.getData());
                            }
                        });
                        web.postWebMessage(new WebMessage(token,new WebMessagePort[]{ports[1]}),Uri.parse(origin));
                        web.evaluateJavascript("window.magpieAndroidNavigation?window.magpieAndroidNavigation():[]",data->{
                            if(isFinishing()||!CoreConfig.sameOrigin(web.getUrl(),origin))return;
                            try{org.json.JSONArray items=new org.json.JSONArray(data);for(int i=0;i<items.length()&&i<256;i++){JSONObject item=items.getJSONObject(i);String id=item.getString("id"),label=item.getString("title");if(id.length()<=160&&label.length()<=180)pageTitles.put(id,label);}}catch(Exception invalidMetadata){}updateTitle();
                        });
                        if(getIntent().hasExtra("groupId")||getIntent().getBooleanExtra("addGroup",false))dispatchGroup(++editorEpoch,0);
                        if(getIntent().hasExtra("providerId")||getIntent().getBooleanExtra("addProvider",false)){
                            String id=getIntent().getStringExtra("providerId");boolean add=getIntent().getBooleanExtra("addProvider",false);
                            web.evaluateJavascript("window.magpieAndroidProvider("+JSONObject.quote(id==null?"":id)+","+add+")",result->{
                                if("true".equals(result)){getIntent().removeExtra("providerId");getIntent().removeExtra("addProvider");}
                            });
                        }
                    });
                } catch (IOException e) { Toast.makeText(ManagementActivity.this,"手机界面适配资源读取失败",Toast.LENGTH_LONG).show(); }
                String path=Uri.parse(url).getQueryParameter("view"); if(path!=null) {currentView=path; updateTitle();}
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame()) showError("连接失败",e.getDescription().toString());
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response) {
                // Keep upstream's useful sign-in/error page for authentication failures.
                if(r.isForMainFrame() && response.getStatusCode()>=500) showError("服务暂时不可用","HTTP "+response.getStatusCode()+"，请检查 Magpie 是否仍在运行。");
            }
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e) {
                h.cancel(); showError("证书验证失败","请为远程 Magpie 配置有效的 HTTPS 证书。连接已停止。");
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {
                // A broken/updated system WebView must not kill the gateway's app.
                pageLoaded=false;rendererGone=true;closeExportPort();
                android.view.ViewParent parent=v.getParent();
                if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(v);
                v.destroy();
                showError("系统网页组件已停止","请更新或重启 Android System WebView / Chrome 后重试。管理页已安全关闭；本机网关继续运行，可返回工作台查看状态。");
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v,int value) {progress.setProgress(value);}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=callback;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                try {startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){callback.onReceiveValue(null);fileCallback=null;}return true;
            }
            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result) {
                new AlertDialog.Builder(ManagementActivity.this,ui.dialogTheme).setTitle("Magpie").setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;
            }
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result) {
                new AlertDialog.Builder(ManagementActivity.this,ui.dialogTheme).setTitle("请确认").setMessage(message).setPositiveButton("确认",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
            }
        });
        web.setDownloadListener((url,agent,disposition,mime,len)->{
            if(url.startsWith("blob:"))web.evaluateJavascript("window.magpieAndroidDownloadBlob&&window.magpieAndroidDownloadBlob("+JSONObject.quote(url)+")",null);
            else if(CoreConfig.sameOrigin(url,origin))download(url,mime,URLUtil.guessFileName(url,disposition,mime));
            else openExternal(url);
        });
        navigation=ui.column();root.addView(navigation);refreshNavigation();
        ui.viewport(root,null,navigation);setContentView(root);load(currentView);
    }
    @Override protected void onResume(){super.onResume();UiMotion.enter(this);}
    @Override protected void onPause(){UiMotion.pause(this);super.onPause();}
    private void dispatchGroup(int epoch,int attempt){
        if(epoch!=editorEpoch||isFinishing()||isDestroyed()||rendererGone||!CoreConfig.sameOrigin(web.getUrl(),origin))return;
        String id=getIntent().getStringExtra("groupId");boolean add=getIntent().getBooleanExtra("addGroup",false);
        web.evaluateJavascript("window.magpieAndroidGroup?window.magpieAndroidGroup("+JSONObject.quote(id==null?"":id)+","+add+"):false",result->{
            if(epoch!=editorEpoch||isFinishing())return;
            if("true".equals(result)){getIntent().removeExtra("groupId");getIntent().removeExtra("addGroup");}
            else if(attempt<39)main.postDelayed(()->dispatchGroup(epoch,attempt+1),250);
            else Toast.makeText(this,"分组页尚未就绪，可在当前页面直接新建或编辑",Toast.LENGTH_LONG).show();
        });
    }
    private String parentName(){if(getIntent().getBooleanExtra("returnToSetup",false))return "添加服务商";String root=AppNavigation.parent(this,currentView);int i=java.util.Arrays.asList(NavigationModel.ROOTS).indexOf(root);return i<0?"工具":NavigationModel.LABELS[i];}
    private void refreshNavigation(){
        if(navigation==null)return;navigation.removeAllViews();navigation.addView(ui.bottomNav(NavigationModel.section(currentView),id->{
            if(id.equals("plugins")&&currentView.equals("plugins")){UiMotion.stay(this);return;}
            requestLeave(()->leavePrimary(id));
        }));
    }
    private String viewName(String view){if(view.equals("usage"))return "请求明细与导出";String known=NavigationModel.title(view);if(!known.equals("扩展页面"))return known;if(pageTitles.containsKey(view))return pageTitles.get(view);return view.equals(getIntent().getStringExtra("view"))&&getIntent().getStringExtra("pageTitle")!=null?getIntent().getStringExtra("pageTitle"):known;}
    private void updateTitle(){title.setText(viewName(currentView));parentLabel.setText("来自"+parentName());refreshNavigation();}
    private void navigate(String view){
        UiMotion.stay(this);leaveEpoch++;leaveAction=null;leaveRequest="";
        if(!pageLoaded){load(view);return;}
        // Select the upstream navigation button so unsaved-editor confirmation stays upstream's.
        String script="(function(){if(typeof show==='function'){Promise.resolve(show("+JSONObject.quote(view)+")).then(ok=>{if(ok&&window.AndroidExport)AndroidExport.view(new URLSearchParams(location.search).get('view')||'agents')});return true;}return false;})()";
        web.evaluateJavascript(script,result->{if(!"true".equals(result))load(view);});
    }
    private void load(String view){currentView=view;updateTitle();errorScroll.setVisibility(android.view.View.GONE);web.setVisibility(android.view.View.VISIBLE);
        int engineMajor=CoreConfig.chromiumMajor(web.getSettings().getUserAgentString());
        if(engineMajor>0&&engineMajor<110){showError("请更新 Android System WebView","当前系统网页组件使用 Chromium "+engineMajor+"。此界面需要 Chromium / WebView 110 或更新版本；请更新系统 WebView（部分手机使用 Chrome），然后重新打开应用。本机网关可继续运行。");return;}
        if(!remoteMode){if(!GatewayService.ready){showError("本机网关未就绪","返回工作台启动网关后再试。");return;}origin=GatewayService.managementOrigin;key=GatewayService.key(this);}
        web.loadUrl(origin+"/?view="+Uri.encode(view)+"&k="+Uri.encode(key));}
    private void showError(String head,String message){
        pageLoaded=false;editorEpoch++;closeExportPort();
        progress.setVisibility(android.view.View.GONE);error.removeAllViews();error.addView(ui.text(head,22,ui.ink,true));ui.space(error,12);error.addView(ui.text(message,14,ui.muted,false));ui.space(error,20);
        error.addView(ui.button("重试连接",true,()->{if(rendererGone){Intent next=new Intent(getIntent()).setClass(this,ManagementActivity.class).putExtra("view",currentView).putExtra("remote",remoteMode).putExtra("parentRoot",AppNavigation.parent(this,currentView));startActivity(next);finish();}else load(currentView);}));
        if(remoteMode||GatewayService.ready){ui.space(error,10);error.addView(ui.button("使用手机浏览器管理",false,this::browserFallback));}
        ui.space(error,10);error.addView(ui.button("返回"+parentName(),false,this::requestLeave));errorScroll.setVisibility(android.view.View.VISIBLE);web.setVisibility(android.view.View.GONE);
    }
    private void browserFallback(){
        String address=remoteMode?origin:GatewayService.managementOrigin;
        if(address==null||address.isEmpty()){Toast.makeText(this,"请先启动本机网关",Toast.LENGTH_SHORT).show();return;}
        String url=address+"/?view="+Uri.encode(currentView);
        // A remote browser signs in itself; never put remote credentials into an Intent.
        if(!remoteMode)url+="&k="+Uri.encode(GatewayService.key(this));
        openExternal(url);
    }
    private void openExternal(String url){
        Uri u=Uri.parse(url);String scheme=u.getScheme();
        if(!"https".equals(scheme)&&!"http".equals(scheme)&&!"mailto".equals(scheme)){Toast.makeText(this,"无法打开此类型的链接",Toast.LENGTH_SHORT).show();return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,u).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到可打开链接的应用",Toast.LENGTH_SHORT).show();}
    }
    private String readAsset(String name)throws IOException{try(InputStream in=getAssets().open(name)){return new String(readBounded(in,1024*1024),StandardCharsets.UTF_8);}}
    static byte[] readBounded(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("文件超过大小限制");out.write(b,0,n);}return out.toByteArray();}

    private void closeExportPort(){if(exportPort!=null){exportPort.close();exportPort=null;}}
    private void handleExportMessage(String message){
        if(message==null||message.length()>45*1024*1024||isFinishing())return;
        try{
            JSONObject m=new JSONObject(message);String action=m.optString("action");
            if("leave".equals(action)){if(leaveAction==null||!leaveRequest.equals(m.optString("request")))return;Runnable actionOnLeave=leaveAction;leaveAction=null;leaveRequest="";actionOnLeave.run();return;}
            if("view".equals(action)){String view=m.optString("view");if(!view.isEmpty()&&view.length()<=160&&view.indexOf('\n')<0){currentView=view;updateTitle();}return;}
            if("backup".equals(action)){exportBackup(m.optString("path"));return;}
            if("save".equals(action))prepareExport(android.util.Base64.decode(m.getString("encoded"),android.util.Base64.DEFAULT),m.optString("mime"),m.optString("filename"));
        }catch(Exception e){Toast.makeText(this,"导出消息无效",Toast.LENGTH_SHORT).show();}
    }
    private void exportBackup(String path){
            main.post(()->{
                if(remoteMode||!CoreConfig.sameOrigin(web.getUrl(),origin)||path==null)return;
                String name=CoreConfig.safeFilename(path);
                if(!name.matches("magpie-[0-9]{4}-[0-9]{2}-[0-9]{2}(-[0-9]+)?\\.magpie-backup"))return;
                new Thread(()->{try{
                    File dir=new File(getFilesDir(),"core/Downloads");
                    File f=new File(dir,name);
                    if(!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("备份路径无效");
                    byte[]bytes;try(InputStream in=new FileInputStream(f)){bytes=readBounded(in,32*1024*1024);}
                    main.post(()->prepareExport(bytes,"application/octet-stream",name));
                }catch(IOException e){main.post(()->Toast.makeText(ManagementActivity.this,"备份已在应用内生成，但导出失败："+e.getMessage(),Toast.LENGTH_LONG).show());}},"magpie-backup").start();
            });
    }
    private void prepareExport(byte[]bytes,String mime,String filename){
        if(exportBytes!=null){Toast.makeText(this,"请先完成或取消上一次保存",Toast.LENGTH_SHORT).show();return;}
        if(bytes.length>32*1024*1024){Toast.makeText(this,"文件超过 32 MB，请在远程浏览器下载",Toast.LENGTH_LONG).show();return;}
        exportBytes=bytes;exportMime=mime==null||mime.isEmpty()?"application/octet-stream":mime;exportName=CoreConfig.safeFilename(filename);
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(exportMime).putExtra(Intent.EXTRA_TITLE,exportName);
        try{startActivityForResult(i,SAVE);}catch(ActivityNotFoundException e){exportBytes=null;Toast.makeText(this,"没有可用的文件保存程序",Toast.LENGTH_LONG).show();}
    }
    private void download(String address,String mime,String name){
        final String cookie=CookieManager.getInstance().getCookie(address);
        new Thread(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
            if(cookie!=null)c.setRequestProperty("Cookie",cookie);
            if(c.getResponseCode()!=200)throw new IOException("下载失败 HTTP "+c.getResponseCode());
            byte[]bytes;try(InputStream in=c.getInputStream()){bytes=readBounded(in,32*1024*1024);}main.post(()->prepareExport(bytes,mime,name));
        }catch(Exception e){main.post(()->Toast.makeText(this,"下载失败："+e.getMessage(),Toast.LENGTH_LONG).show());}finally{if(c!=null)c.disconnect();}},"magpie-export").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK&&fileCallback!=null){Uri[] uris=null;if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){int n=data.getClipData().getItemCount();uris=new Uri[n];for(int i=0;i<n;i++)uris[i]=data.getClipData().getItemAt(i).getUri();}else if(data.getData()!=null)uris=new Uri[]{data.getData()};}fileCallback.onReceiveValue(uris);fileCallback=null;}
        if(request==SAVE){byte[]bytes=exportBytes;exportBytes=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){Uri uri=data.getData();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法打开保存位置");out.write(bytes);main.post(()->Toast.makeText(this,"文件已保存",Toast.LENGTH_SHORT).show());}catch(IOException e){main.post(()->Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show());}},"magpie-save").start();}}
    }
    @Override public void onBackPressed(){
        if(!pageLoaded){requestLeave();return;}
        web.evaluateJavascript("window.magpieAndroidBack?window.magpieAndroidBack():false",handled->{if(!"true".equals(handled))requestLeave();});
    }
    private void requestLeave(){requestLeave(()->{if(getIntent().getBooleanExtra("returnToSetup",false))UiMotion.close(this);else AppNavigation.primary(this,AppNavigation.parent(this,currentView));});}
    private void leavePrimary(String id){
        if(!getIntent().getBooleanExtra("returnToSetup",false)){AppNavigation.primary(this,id);return;}
        if(nativeLeaveDialog!=null&&nativeLeaveDialog.isShowing())return;
        nativeLeaveDialog=new AlertDialog.Builder(this,ui.dialogTheme).setTitle("离开添加服务商？").setMessage("原生添加页还有未保存的地址、密钥和模型选择。").setNegativeButton("留在当前页",null).setPositiveButton("放弃并继续",(d,w)->{setResult(RESULT_FIRST_USER,new Intent().putExtra("primary",id));UiMotion.close(this);}).create();nativeLeaveDialog.show();
    }
    private void requestLeave(Runnable action){UiMotion.stay(this);leaveAction=action;leaveRequest=Long.toString(++leaveEpoch);if(!pageLoaded||exportPort==null){leaveAction=null;leaveRequest="";action.run();return;}web.evaluateJavascript("window.magpieAndroidLeave&&window.magpieAndroidLeave("+JSONObject.quote(leaveRequest)+")",null);}
    private void confirmReload(){new AlertDialog.Builder(this,ui.dialogTheme).setTitle("刷新管理界面？").setMessage("尚未保存的编辑会丢失。").setNegativeButton("取消",null).setPositiveButton("刷新",(d,w)->load(currentView)).show();}
    @Override protected void onDestroy(){if(nativeLeaveDialog!=null)nativeLeaveDialog.dismiss();if(fileCallback!=null)fileCallback.onReceiveValue(null);closeExportPort();if(web!=null)web.destroy();main.removeCallbacksAndMessages(null);super.onDestroy();}
}

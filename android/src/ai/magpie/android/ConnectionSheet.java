package ai.magpie.android;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.URI;
import java.util.function.BooleanSupplier;

/** Local configuration and an explicit protocol-aware test; remote/LAN keys stay upstream. */
final class ConnectionSheet {
    static Dialog show(Activity a,Ui ui,String gateway,String reference,Runnable manage){return show(a,ui,gateway,reference,manage,false,null);}
    static Dialog show(Activity a,Ui ui,String gateway,String reference,Runnable manage,boolean favorite,BooleanSupplier toggle){return show(a,ui,gateway,reference,manage,favorite,toggle,()->true);}
    static Dialog show(Activity a,Ui ui,String gateway,String reference,Runnable manage,boolean favorite,BooleanSupplier toggle,BooleanSupplier valid){
        View focused=a.getCurrentFocus();if(focused!=null){focused.clearFocus();((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);}
        Dialog dialog=new Dialog(a,ui.dialogTheme);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);boolean local=loopback(gateway);
        boolean[] alive={true},busy={false};dialog.setOnDismissListener(d->alive[0]=false);
        LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);
        LinearLayout title=ui.row();title.setPadding(ui.dp(24),ui.dp(8),ui.dp(12),ui.dp(8));ui.grow(title,ui.text("接入模型",22,ui.ink,true));
        title.addView(ui.iconButton("down","关闭接入面板",dialog::dismiss));root.addView(title);ui.rule(root,0);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);LinearLayout body=ui.column();body.setPadding(ui.dp(24),ui.dp(18),ui.dp(24),ui.dp(12));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(toggle!=null){TextView pin=ui.text(favorite?"取消收藏":"收藏模型",13,ui.ink,true);pin.setGravity(Gravity.CENTER);pin.setMinHeight(ui.dp(48));pin.setPadding(ui.dp(10),0,ui.dp(10),0);pin.setOnClickListener(v->{boolean on=toggle.getAsBoolean();pin.setText(on?"取消收藏":"收藏模型");pin.announceForAccessibility(on?"已收藏模型":"已取消收藏");});body.addView(pin);ui.space(body,10);}
        field(ui,body,"模型标识",reference,"复制接入模型标识",()->copy(a,reference));ui.space(body,18);
        body.addView(ui.text("接口协议",12,ui.muted,false));ui.space(body,6);String[] flavor={"OpenAI"};TextView[] endpoint={null};
        body.addView(new PeriodTabs(ui,new String[]{"OpenAI","Anthropic","Gemini"},0,index->{flavor[0]=new String[]{"OpenAI","Anthropic","Gemini"}[index];if(endpoint[0]!=null)endpoint[0].setText(base(gateway,flavor[0]));}),new LinearLayout.LayoutParams(-1,-2));ui.space(body,20);
        endpoint[0]=field(ui,body,"Base URL",base(gateway,flavor[0]),"复制 Base URL",()->copy(a,base(gateway,flavor[0])));ui.space(body,18);
        field(ui,body,"API Key",local?"magpie":"在网关中选择访问密钥",local?"复制 API Key":"",local?()->copy(a,"magpie"):null);ui.space(body,10);
        body.addView(ui.text(local?"本机使用 magpie 占位；局域网和命名密钥在网关中设置。":"请在网关中确认实际访问地址和密钥。",12,ui.muted,false));ui.space(body,14);ui.rule(body,0);
        if(local){TextView json=action(ui,"复制 OpenAI JSON 配置",()->{copy(a,ClientConfig.openAI(gateway,reference));Toast.makeText(a,"JSON 已复制",Toast.LENGTH_SHORT).show();});json.setContentDescription("复制 OpenAI JSON 配置");body.addView(json);}
        body.addView(action(ui,"网关与访问密钥 →",()->{dialog.dismiss();manage.run();}));
        ui.rule(root,0);LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(10),ui.dp(24),ui.dp(16));
        TextView result=ui.text(local?"测试会发送一条短消息，计入实际用量。":"远程接入请在网关中确认。",12,ui.muted,false);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);footer.addView(result);ui.space(footer,10);
        ActionButtons buttons=new ActionButtons(ui);
        if(local){Button test=ui.button("测试调用",false,()->{});test.setTextSize(14);test.setContentDescription("测试当前接口");test.setOnClickListener(v->{
            if(busy[0])return;if(!valid.getAsBoolean()){result.setText("模型或网关状态已变化，请重新打开接入面板。");return;}
            busy[0]=true;test.setEnabled(false);test.setText("测试中…");String selected=flavor[0];result.setText("正在测试 "+selected+"，请稍候…");
            new Thread(()->{GatewayProbe.Result outcome=GatewayProbe.call(gateway,reference,selected);new Handler(Looper.getMainLooper()).post(()->{if(!alive[0]||a.isFinishing()||a.isDestroyed())return;busy[0]=false;test.setEnabled(true);test.setText("再次测试");result.setText(valid.getAsBoolean()?outcome.message:"模型或网关状态已变化，请重新打开接入面板。");});},"magpie-gateway-test").start();
        });buttons.addAction(test);}
        Button use=ui.button(local?"复制接入信息":"打开接入设置",true,()->{});use.setTextSize(14);int[] epoch={0};use.setOnClickListener(v->{if(!local){dialog.dismiss();manage.run();return;}copy(a,"协议: "+flavor[0]+"\nBase URL: "+base(gateway,flavor[0])+"\nAPI Key: magpie\n模型: "+reference);use.setText("已复制");use.announceForAccessibility("已复制接入信息");int n=++epoch[0];use.postDelayed(()->{if(alive[0]&&epoch[0]==n)use.setText("复制接入信息");},1300);});buttons.addAction(use);footer.addView(buttons);root.addView(footer);dialog.setContentView(viewport(ui,root,title,scroll,footer));
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);w.setDimAmount(.3f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setWindowAnimations(UiMotion.enabled()?R.style.MagpieSheetAnimation:0);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}dialog.show();if(w!=null)w.setLayout(-1,Math.min(ui.dp(720),(int)(a.getResources().getDisplayMetrics().heightPixels*.9f)));return dialog;
    }
    /** Keep actions fixed when possible; let the entire sheet scroll if they would consume its body. */
    private static View viewport(Ui ui,LinearLayout root,View title,ScrollView fields,View footer){
        final int[] boundedHeight={0};
        ScrollView page=new ScrollView(ui.a){
            @Override protected void measureChildWithMargins(View child,int widthSpec,int widthUsed,int heightSpec,int heightUsed){
                if(boundedHeight[0]==0){super.measureChildWithMargins(child,widthSpec,widthUsed,heightSpec,heightUsed);return;}
                // An outer ScrollView normally offers unlimited height; bound the
                // normal layout so its weighted fields cannot push actions below it.
                child.measure(getChildMeasureSpec(widthSpec,getPaddingLeft()+getPaddingRight()+widthUsed,LayoutParams.MATCH_PARENT),MeasureSpec.makeMeasureSpec(boundedHeight[0],MeasureSpec.EXACTLY));
            }
        };page.setFillViewport(true);page.addView(root,new ScrollView.LayoutParams(-1,-2));
        FrameLayout surface=new FrameLayout(ui.a){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int exact=MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec),MeasureSpec.EXACTLY),free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
                title.measure(exact,free);footer.measure(exact,free);
                boolean unified=MeasureSpec.getSize(heightSpec)-title.getMeasuredHeight()-footer.getMeasuredHeight()<ui.dp(120);
                boundedHeight[0]=unified?0:MeasureSpec.getSize(heightSpec);
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)fields.getLayoutParams();p.height=unified?LayoutParams.WRAP_CONTENT:0;p.weight=unified?0:1;
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        surface.addView(page,new FrameLayout.LayoutParams(-1,-1));return surface;
    }
    private static TextView field(Ui ui,LinearLayout body,String label,String value,String copyLabel,Runnable copy){body.addView(ui.text(label,12,ui.muted,false));ui.space(body,5);LinearLayout row=ui.row();TextView text=ui.text(value,16,ui.ink,true);text.setTextIsSelectable(true);ui.grow(row,text);if(copy!=null)row.addView(ui.copyButton(copyLabel,copy));body.addView(row);return text;}
    private static TextView action(Ui ui,String label,Runnable action){TextView v=ui.text(label,13,ui.ink,true);v.setMinHeight(ui.dp(48));v.setGravity(Gravity.CENTER_VERTICAL);v.setOnClickListener(w->action.run());return v;}
    static String base(String gateway,String protocol){String value=gateway.replaceAll("/+$","");return protocol.equals("OpenAI")?value+"/v1":value;}
    static boolean loopback(String address){try{String host=new URI(address).getHost();return "127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host)||"[::1]".equals(host)||"::1".equals(host);}catch(Exception e){return false;}}
    private static void copy(Activity a,String text){((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Magpie 接入",text));}
}

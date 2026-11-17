package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Host controls stay on the calling screen; no detour through the model catalog. */
final class HostActions {
    static final String APP_VERSION=BuildInfo.APP_VERSION,CORE_VERSION=BuildInfo.CORE_VERSION;
    private final Activity a;private final Ui ui;private final Runnable changed;
    private final android.content.SharedPreferences prefs;
    HostActions(Activity a,Ui ui,Runnable changed){this.a=a;this.ui=ui;this.changed=changed;prefs=a.getSharedPreferences("host",0);}
    void start(){try{GatewayService.state="正在启动";a.startForegroundService(new Intent(a,GatewayService.class));changed.run();}catch(Exception e){Toast.makeText(a,"启动失败："+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void workspace(){
        new AlertDialog.Builder(a,ui.dialogTheme).setTitle("工作空间").setItems(new String[]{"使用本机网关","连接远程 Magpie","停止本机网关"},(d,index)->{
            if(index==0){prefs.edit().putBoolean("remote",false).apply();changed.run();if(!GatewayService.ready)start();}
            else if(index==1)remote();else{a.stopService(new Intent(a,GatewayService.class));changed.run();}
        }).show();
    }
    void remote(){
        LinearLayout form=ui.column();form.setPadding(ui.dp(24),ui.dp(10),ui.dp(24),ui.dp(10));
        form.addView(ui.text("填写电脑或服务器的 Magpie Web 地址与访问密钥。",14,ui.muted,false));ui.space(form,16);
        EditText address=input(form,"管理地址",false,prefs.getString("remoteAddress",""));address.setHint("https://magpie.example.com");
        ui.space(form,14);EditText key=input(form,"Web 访问密钥",true,prefs.getString("remoteKey",""));
        ui.space(form,12);form.addView(ui.text("电脑端：magpie web --lan --no-open\n使用 Web 访问密钥，模型 API Key 填在服务商中。",12,ui.muted,false));
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(a,ui.dialogTheme).setTitle("连接远程工作空间").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存并连接",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String origin=CoreConfig.remote(address.getText().toString());String secret=key.getText().toString().trim();if(secret.length()<16){key.setError("请输入至少16位的Web访问密钥");return;}
                prefs.edit().putString("remoteAddress",origin).putString("remoteKey",secret).putBoolean("remote",true).apply();dialog.dismiss();changed.run();
            }catch(IllegalArgumentException e){address.setError(e.getMessage());}
        }));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.show();
    }
    private EditText input(LinearLayout box,String label,boolean secret,String value){
        box.addView(ui.text(label,12,ui.muted,false));EditText v=new EditText(a);v.setSingleLine();v.setMinHeight(ui.dp(48));v.setTextSize(16);v.setTextColor(ui.ink);v.setHintTextColor(ui.muted);v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));v.setTypeface(ui.font(false,400));v.setText(value);v.setSaveEnabled(false);v.setContentDescription(label);box.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    void diagnostics(){
        TextView text=ui.text(GatewayService.state+"\n\n"+GatewayService.logs(),12,ui.ink,false);text.setTextIsSelectable(true);text.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));ScrollView scroll=new ScrollView(a);scroll.addView(text);
        new AlertDialog.Builder(a,ui.dialogTheme).setTitle("本机运行诊断").setView(scroll).setPositiveButton("关闭",null).setNeutralButton("停止网关",(d,w)->{a.stopService(new Intent(a,GatewayService.class));changed.run();}).show();
    }
    void about(){
        new AlertDialog.Builder(a,ui.dialogTheme).setTitle("Magpie Android "+APP_VERSION).setMessage("Magpie 核心 v"+CORE_VERSION+"\n独立 Android 移植版 · 非上游官方 APK\n\n模型、分组、用量、插件与备份使用真实核心接口。模型调用需要自己的账号或密钥。\n\n电脑 Agent 配置、外部 CLI/MCP 程序需要对应环境或远程工作空间。应用配置保存在私有目录，卸载前请备份。\n\nMIT License · Copyright © yetone").setPositiveButton("开源许可",(d,w)->licenses()).setNegativeButton("关闭",null).show();
    }
    private void licenses(){
        try{StringBuilder text=new StringBuilder();for(String name:new String[]{"LICENSE","THIRD_PARTY_NOTICES","BUN-LICENSE.md","fonts/NotoSansSC-OFL.txt","fonts/Inter-OFL.txt","design/PHOSPHOR_LICENSE.txt","design/RADIX_LICENSE.txt","LUCIDE_LICENSE.txt"})try(InputStream in=a.getAssets().open(name)){text.append(name).append("\n").append(new String(ManagementActivity.readBounded(in,2*1024*1024),StandardCharsets.UTF_8)).append("\n\n");}
            TextView body=ui.text(text.toString(),12,ui.ink,false);body.setTextIsSelectable(true);body.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));ScrollView scroll=new ScrollView(a);scroll.addView(body);new AlertDialog.Builder(a,ui.dialogTheme).setTitle("开源许可").setView(scroll).setPositiveButton("关闭",null).show();
        }catch(IOException e){Toast.makeText(a,"许可文件读取失败",Toast.LENGTH_LONG).show();}
    }
}

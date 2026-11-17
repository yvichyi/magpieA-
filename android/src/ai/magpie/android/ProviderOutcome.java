package ai.magpie.android;

import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Explicit result of one request. An empty error string never establishes success. */
final class ProviderOutcome {
    final boolean success,uncertain;
    final LinkedHashMap<String,String> rows;
    final String error;
    private ProviderOutcome(boolean success,boolean uncertain,LinkedHashMap<String,String> rows,String error){this.success=success;this.uncertain=uncertain;this.rows=rows;this.error=error;}
    static ProviderOutcome call(CoreApi api,ProviderDraft draft,boolean saving,String body){
        try{
            String reply=api.post(saving?"/api/provider/save":"/api/provider/list",body);
            if(saving){draft.confirmSaved(reply);return new ProviderOutcome(true,false,null,"");}
            return new ProviderOutcome(true,false,ProviderDraft.models(reply),"");
        }catch(Exception e){
            String message=e.getMessage();if(message==null||message.trim().isEmpty())message="请求未完成，请检查地址和网络";
            if(e instanceof CoreApi.HttpFailure){try{String server=new JSONObject(((CoreApi.HttpFailure)e).body).optString("error","").trim();if(!server.isEmpty())message=server;}catch(Exception ignored){}
                if(message.contains("already added with that key"))message="这个地址和 Key 已添加，请返回模型列表使用已有服务商。";
            }
            String transmitted=draft.key.trim();if(!transmitted.isEmpty())message=message.replace(transmitted,"[密钥已隐藏]");
            if(!draft.key.isEmpty()&&!draft.key.equals(transmitted))message=message.replace(draft.key,"[密钥已隐藏]");
            boolean uncertain=saving&&(!(e instanceof CoreApi.HttpFailure)||((CoreApi.HttpFailure)e).status>=500);
            return new ProviderOutcome(false,uncertain,null,message);
        }
    }
}

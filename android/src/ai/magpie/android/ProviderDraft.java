package ai.magpie.android;

import java.net.URI;
import java.util.*;
import org.json.*;

/** The small native creation flow; advanced provider editing remains upstream. */
final class ProviderDraft {
    final String id;
    ProviderDraft(){this(null);}
    ProviderDraft(String restored){id=restored!=null&&restored.matches("api-[a-f0-9-]{12}")?restored:"api-"+UUID.randomUUID().toString().substring(0,12);}
    String url="",key="",name="",manual="";
    int protocol;
    final LinkedHashMap<String,String> listed=new LinkedHashMap<>();
    final LinkedHashSet<String> selected=new LinkedHashSet<>();
    String displayName(){if(!name.trim().isEmpty())return name.trim();try{return new URI(url.trim()).getHost();}catch(Exception e){return "我的 API";}}
    JSONObject preview(){
        try{
            String base=url.trim().replaceAll("/+$","");URI u=new URI(base);
            if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new IllegalArgumentException("请输入 http 或 https 的 Base URL，密钥请填写在下方");
            if(base.matches(".*(/chat/completions|/responses|/messages|:generateContent)$"))throw new IllegalArgumentException("请填写 Base URL，移除末尾的具体请求路径");
            if(protocol<0||protocol>3)throw new IllegalArgumentException("请选择接口协议");
            return new JSONObject().put("typed",true).put("name",displayName()).put("key",key.trim()).put(new String[]{"chat","responses","anthropic","gemini"}[protocol],base);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("请输入有效的 Base URL");}
    }
    LinkedHashSet<String> modelIds(){
        LinkedHashSet<String> ids=new LinkedHashSet<>(selected);
        for(String id:manual.split("[,，\\r\\n]+")){id=id.trim();if(!id.isEmpty()){if(id.length()>512)throw new IllegalArgumentException("模型标识过长");ids.add(id);}}
        if(ids.size()>5000)throw new IllegalArgumentException("一次最多选择5000个模型");return ids;
    }
    JSONObject save(){try{
        LinkedHashSet<String> ids=modelIds();if(ids.isEmpty())throw new IllegalArgumentException("请先读取并选择模型，或填写模型 ID");
        return preview().put("id",id).put("new",true).put("models",new JSONArray(ids)).put("baseAPI",new String[]{"chat","responses","anthropic","gemini"}[protocol]);
    }catch(JSONException e){throw new IllegalArgumentException("配置内容无效");}}
    static LinkedHashMap<String,String> models(String body){
        try{JSONArray rows=new JSONObject(body).getJSONArray("models");if(rows.length()>5000)throw new IllegalArgumentException("模型超过5000项，请在高级添加中选择");
            LinkedHashMap<String,String> out=new LinkedHashMap<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.getString("id");if(id.isEmpty()||id.length()>512)throw new IllegalArgumentException("模型列表缺少有效标识");out.put(id,row.optString("name",id));}return out;
        }catch(JSONException e){throw new IllegalArgumentException("模型列表响应不完整，请重试读取");}
    }
    void confirmSaved(String body){
        try{JSONArray rows=new JSONObject(body).getJSONArray("providers");for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("id")))return;
        }catch(JSONException ignored){}
        throw new IllegalArgumentException("保存结果待确认，请返回模型列表查看");
    }
}

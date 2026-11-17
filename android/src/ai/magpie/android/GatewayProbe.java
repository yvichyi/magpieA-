package ai.magpie.android;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** One explicit short request through the local gateway, never a vendor credential or an automatic replay. */
final class GatewayProbe {
    static final class Result {final boolean ok;final String message;Result(boolean ok,String message){this.ok=ok;this.message=message;}}
    static Result call(String gateway,String reference,String protocol){
        HttpURLConnection c=null;long start=System.nanoTime();
        try{
            URI u=new URI(gateway);String host=u.getHost();
            if(!("127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host)||"[::1]".equals(host)||"::1".equals(host))||!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException();
            if(reference==null||reference.isEmpty()||reference.length()>1024||reference.indexOf('\n')>=0)throw new IllegalArgumentException();
            String base=gateway.replaceAll("/+$","");JSONObject body=new JSONObject();String path;
            if(protocol.equals("Gemini")){
                String refPath=new URI(null,null,"/"+reference,null).toASCIIString();
                path="/v1beta/models"+refPath+":generateContent";
                body.put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",new JSONArray().put(new JSONObject().put("text","Reply OK."))))).put("generationConfig",new JSONObject().put("maxOutputTokens",32));
            }else if(protocol.equals("OpenAI")||protocol.equals("Anthropic")){
                path=protocol.equals("OpenAI")?"/v1/chat/completions":"/v1/messages";
                body.put("model",reference).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","Reply OK."))).put("max_tokens",32);if(protocol.equals("OpenAI"))body.put("stream",false);
            }else throw new IllegalArgumentException();
            c=(HttpURLConnection)new URL(base+path).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(protocol.equals("Anthropic")){c.setRequestProperty("x-api-key","magpie");c.setRequestProperty("anthropic-version","2023-06-01");}
            else if(protocol.equals("Gemini"))c.setRequestProperty("x-goog-api-key","magpie");else c.setRequestProperty("Authorization","Bearer magpie");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();if(status!=200)return parse(protocol,status,"",elapsed(start));
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException();out.write(buffer,0,n);}
                if(c.getContentLengthLong()>=0&&c.getContentLengthLong()!=out.size())throw new IOException();
                return parse(protocol,status,new String(out.toByteArray(),StandardCharsets.UTF_8),elapsed(start));
            }
        }catch(SocketTimeoutException e){return new Result(false,"本次测试超时，请在请求明细中确认；不会自动重试。");}
        catch(IllegalArgumentException|URISyntaxException e){return new Result(false,"测试仅支持有效的本机网关地址和模型标识。");}
        catch(Exception e){return new Result(false,"本次请求未完成，请检查网关或查看请求明细；不会自动重试。");}
        finally{if(c!=null)c.disconnect();}
    }
    private static long elapsed(long start){return Math.max(0,(System.nanoTime()-start)/1000000);}
    static Result parse(String protocol,int status,String body,long ms){
        if(status!=200){String why=status==401||status==403?"密钥或访问权限被拒绝":status==429?"额度或请求频率受限":status==404?"模型或接口地址未找到":status>=300&&status<400?"网关返回跳转，未继续发送":"服务商未完成本次请求";return new Result(false,why+"（HTTP "+status+"）");}
        try{
            JSONObject root=new JSONObject(body);if(root.has("error"))throw new JSONException("error");boolean answered=false;
            if(protocol.equals("OpenAI")){JSONObject message=root.getJSONArray("choices").getJSONObject(0).getJSONObject("message");answered=content(message.opt("content"),"OpenAI");}
            else if(protocol.equals("Anthropic"))answered=content(root.getJSONArray("content"),"Anthropic");
            else if(protocol.equals("Gemini"))answered=content(root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts"),"Gemini");
            if(!answered)throw new JSONException("no answer");return new Result(true,protocol+" 已收到响应 · "+ms+" ms");
        }catch(Exception e){return new Result(false,"未收到完整的 "+protocol+" 响应，请查看请求明细。");}
    }
    private static boolean content(Object content,String protocol){
        if(content instanceof String)return !((String)content).trim().isEmpty();
        if(content instanceof JSONArray){JSONArray parts=(JSONArray)content;for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;Object text=p.opt("text");boolean type=protocol.equals("Gemini")||"text".equals(p.opt("type"));if(type&&text instanceof String&&!((String)text).trim().isEmpty())return true;}}
        return false;
    }
}

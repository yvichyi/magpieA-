package ai.magpie.android;
import java.net.URI;
import org.json.JSONObject;
final class ClientConfig {
    static String openAI(String gateway,String reference){
        try{URI u=new URI(gateway);String h=u.getHost();if(!("127.0.0.1".equals(h)||"localhost".equalsIgnoreCase(h)||"[::1]".equals(h)||"::1".equals(h)))throw new IllegalArgumentException("远程接入请在网关选择实际访问密钥");
            return new JSONObject().put("base_url",gateway.replaceAll("/+$","")+"/v1").put("api_key","magpie").put("model",reference).toString(2);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("无法生成接入配置");}
    }
}

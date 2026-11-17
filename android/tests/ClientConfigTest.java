package ai.magpie.android;
import org.json.*;
public final class ClientConfigTest{
 public static void main(String[]args)throws Exception{
  JSONObject j=new JSONObject(ClientConfig.openAI("http://127.0.0.1:3425/","供应商/模型\"测试"));
  if(!j.getString("base_url").equals("http://127.0.0.1:3425/v1")||!j.getString("api_key").equals("magpie")||!j.getString("model").equals("供应商/模型\"测试"))throw new AssertionError("Client config fields/escaping changed");
  try{ClientConfig.openAI("https://remote.example","m");throw new AssertionError("Remote placeholder key exported as real credential");}catch(IllegalArgumentException expected){}
  System.out.println("ClientConfig: valid escaped compatible JSON and loopback-only placeholder semantics passed");
 }
}

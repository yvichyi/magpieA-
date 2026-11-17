package ai.magpie.android;
import org.json.*;
import java.util.*;
public final class ProviderDraftTest {
 public static void main(String[]args)throws Exception{
  ProviderDraft d=new ProviderDraft();d.url="https://relay.example/v1";d.key="fixture-secret";
  String id=d.id;
  for(int i=0;i<4;i++){d.protocol=i;JSONObject q=d.preview();String field=new String[]{"chat","responses","anthropic","gemini"}[i];if(!q.getString(field).equals(d.url)||!q.getBoolean("typed")||q.has("new"))throw new AssertionError("Preview contract changes or persists data");}
  d.protocol=0;d.manual="a, b\n模型-c，a";JSONObject save=d.save();
  if(!save.getBoolean("new")||!save.getString("id").equals(id)||save.getJSONArray("models").length()!=3||!save.getString("name").equals("relay.example"))throw new AssertionError("New-provider identity/model order/default name lost");
  if(!d.save().getString("id").equals(id))throw new AssertionError("Retry changed identity");
  if(ProviderDraft.models("{\"models\":[{\"id\":\"x\",\"name\":\"X\"},{\"id\":\"x\"},{\"id\":\"y\"}]}").size()!=2)throw new AssertionError("Duplicate model IDs");
  for(String bad:new String[]{"{}","{\"models\":null}","{\"models\":[{}]}","{\"models\":[{\"id\":\"\"}]}"}){try{ProviderDraft.models(bad);throw new AssertionError("Malformed list treated as empty");}catch(IllegalArgumentException expected){}}
  d.url="https://relay.example/v1/chat/completions";try{d.preview();throw new AssertionError("Full endpoint silently used as base");}catch(IllegalArgumentException expected){}
  d.url="https://user:password@relay.example/v1";try{d.preview();throw new AssertionError("Credentials in URL accepted");}catch(IllegalArgumentException expected){}
  d.url="ftp://relay.example";try{d.preview();throw new AssertionError("Unsupported URL accepted");}catch(IllegalArgumentException expected){}
  System.out.println("ProviderDraft: four protocols, non-persisting preview, stable new identity, explicit models, strict list parsing and URL validation passed");
 }
}

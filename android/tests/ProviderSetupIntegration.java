package ai.magpie.android;
import org.json.*;
public final class ProviderSetupIntegration {
 public static void main(String[] args)throws Exception{
  CoreApi api=new CoreApi(args[0],"android-test-key-1234567890123456");
  ProviderDraft d=new ProviderDraft();d.url=args[1];d.name="手机原生流程 "+d.id;d.key="local-fixture-key";
  int before=new JSONObject(api.get("/api/providers")).getJSONArray("providers").length();
  java.util.LinkedHashMap<String,String> listed=ProviderDraft.models(api.post("/api/provider/list",d.preview().toString()));
  if(!listed.containsKey("echo"))throw new AssertionError("Real vendor listing missing echo");
  if(new JSONObject(api.get("/api/providers")).getJSONArray("providers").length()!=before)throw new AssertionError("Preview saved a provider");
  d.selected.add("echo");try{api.post("/api/provider/save",d.save().toString());throw new AssertionError("Duplicate key/address was added twice");}catch(CoreApi.HttpFailure expected){if(expected.status!=400)throw expected;}
  d.key="native-fixture-"+d.id;String response=api.post("/api/provider/save",d.save().toString());d.confirmSaved(response);
  JSONArray rows=new JSONObject(response).getJSONArray("providers");if(rows.length()!=before+1)throw new AssertionError("Save overwrote an existing provider");
  JSONObject saved=null;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(d.id))saved=rows.getJSONObject(i);
  if(saved==null||!saved.getString("name").equals(d.name))throw new AssertionError("Confirmed identity/name missing");
  System.out.println("PASS Real native client preview returns vendor models without saving; explicit save creates one provider, preserves others and confirms handoff identity: "+d.id);
 }
}

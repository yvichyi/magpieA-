package ai.magpie.android;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
public final class ProviderOutcomeTest {
 public static void main(String[]args)throws Exception{
  System.setProperty("sun.net.http.allowRestrictedHeaders","true");int[] code={400};String[] answer={"{\"error\":\"\"}"};
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",r->{int status=code[0];String body=answer[0];if(r.getRequestURI().getPath().equals("/")){r.getResponseHeaders().add("Set-Cookie","magpie_web=fixture");status=303;}else r.getRequestBody().readAllBytes();byte[] b=body.getBytes(StandardCharsets.UTF_8);r.sendResponseHeaders(status,b.length);r.getResponseBody().write(b);r.close();});server.start();
  try{CoreApi api=new CoreApi("http://127.0.0.1:"+server.getAddress().getPort(),"fixture");ProviderDraft d=new ProviderDraft();d.url="https://example.com/v1";d.key=" secret ";d.manual="echo";
   for(boolean save:new boolean[]{false,true}){ProviderOutcome x=ProviderOutcome.call(api,d,save,(save?d.save():d.preview()).toString());if(x.success||x.error.isEmpty())throw new AssertionError("Blank HTTP error became success");}
   code[0]=500;ProviderOutcome x=ProviderOutcome.call(api,d,true,d.save().toString());if(x.success||!x.uncertain)throw new AssertionError("Server error write uncertainty lost");
   code[0]=400;answer[0]="{\"error\":\"upstream echoed secret\"}";x=ProviderOutcome.call(api,d,false,d.preview().toString());if(x.error.contains("secret"))throw new AssertionError("Trimmed transmitted key remains in UI error");
   code[0]=200;answer[0]="{}";x=ProviderOutcome.call(api,d,true,d.save().toString());if(x.success||!x.uncertain)throw new AssertionError("Malformed save response accepted");
   answer[0]="{\"models\":[{\"id\":\"echo\"}]}";x=ProviderOutcome.call(api,d,false,d.preview().toString());if(!x.success||!x.rows.containsKey("echo"))throw new AssertionError("Valid preview rejected");
   answer[0]="{\"providers\":[{\"id\":\""+d.id+"\",\"name\":\"Example\",\"models\":[]}]}";x=ProviderOutcome.call(api,d,true,d.save().toString());if(!x.success||x.uncertain)throw new AssertionError("Confirmed save rejected");
   System.out.println("ProviderOutcome: blank HTTP errors never succeed, malformed writes remain uncertain, trimmed keys are redacted and valid results are explicit");
  }finally{server.stop(0);}
 }
}

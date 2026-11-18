package ai.magpie.android;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
public final class CoreApiPostTest {
 public static void main(String[]args)throws Exception{
  System.setProperty("sun.net.http.allowRestrictedHeaders","true"); // Android permits Origin; desktop JVM restricts it by default.
  HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger writes=new AtomicInteger();
  s.createContext("/",r->{int code=200;String body="{}",p=r.getRequestURI().getPath();
   if(p.equals("/")){r.getResponseHeaders().add("Set-Cookie","magpie_web=fixture; Path=/; HttpOnly");code=303;}
   else if(!"magpie_web=fixture".equals(r.getRequestHeaders().getFirst("Cookie")))code=401;
   else if(!"POST".equals(r.getRequestMethod())||!r.getRequestHeaders().getFirst("Origin").equals("http://127.0.0.1:"+s.getAddress().getPort()))code=403;
   else {writes.incrementAndGet();body=new String(r.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(body.equals("drop")){r.close();return;}if(body.equals("redirect")){r.getResponseHeaders().add("Location","http://127.0.0.1:1/stolen");code=307;}}
   byte[]b=body.getBytes(StandardCharsets.UTF_8);r.sendResponseHeaders(code,b.length);r.getResponseBody().write(b);r.close();});
  s.start();try{CoreApi api=new CoreApi("http://127.0.0.1:"+s.getAddress().getPort(),"fixture");
   String body="{\"name\":\"中文模型\",\"key\":\"fixture\"}";if(!api.post("/api/provider/list",body).equals(body))throw new AssertionError("Exact UTF8 body lost");
   try{api.post("/api/provider/save","redirect");throw new AssertionError("Authenticated mutation followed redirect");}catch(java.io.IOException expected){}
   try{api.post("/api/provider/save","drop");throw new AssertionError("Connection loss after request body became success");}catch(java.io.IOException expected){}
   for(String p:new String[]{"/api/provider/remove","//other","/api/provider/save?next=evil","/api/provider/list/../save"}){try{api.post(p,body);throw new AssertionError("Mutation allowlist bypass");}catch(IllegalArgumentException expected){}}
   for(String path:new String[]{"/api/plugins/add","/api/plugins/upgrade","/api/plugins/off","/api/plugins/builtin","/api/plugins/check"})if(!api.post(path,body).equals(body))throw new AssertionError("Plugin write payload lost");
   if(writes.get()!=8)throw new AssertionError("Mutation replayed or arbitrary endpoint called");
   System.out.println("CoreApiPost: exact authenticated UTF8, same-origin header, strict mutation allowlist, refused redirect and no replay passed");
  }finally{s.stop(0);}
 }
}

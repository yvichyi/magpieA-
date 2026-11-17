package ai.magpie.android;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
public final class CoreApiTest {
    public static void main(String[] args)throws Exception{
        HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        s.createContext("/",r->{String path=r.getRequestURI().getPath();int code=200;String body="{\"providers\":[]}";
            if(path.equals("/")&&"k=fixture-web-key".equals(r.getRequestURI().getQuery())){r.getResponseHeaders().add("Set-Cookie","magpie_web=fixture-web-key; HttpOnly; Path=/");r.getResponseHeaders().add("Location","/");code=303;}
            else if(!"magpie_web=fixture-web-key".equals(r.getRequestHeaders().getFirst("Cookie")))code=401;
            else if(path.equals("/api/fail"))code=503;
            byte[]b=body.getBytes(StandardCharsets.UTF_8);r.sendResponseHeaders(code,b.length);r.getResponseBody().write(b);r.close();});
        s.start();try{
            String origin="http://127.0.0.1:"+s.getAddress().getPort();CoreApi api=new CoreApi(origin,"fixture-web-key");
            if(!api.get("/api/providers").equals("{\"providers\":[]}"))throw new AssertionError("response changed");
            try{api.get("/api/fail");throw new AssertionError("error presented as data");}catch(IOException expected){}
            try{new CoreApi(origin,"wrong-key").get("/api/providers");throw new AssertionError("invalid key accepted");}catch(IOException expected){}
            try{api.get("//other-host");throw new AssertionError("arbitrary path accepted");}catch(IllegalArgumentException expected){}
            System.out.println("CoreApi: authenticated reads, refused key, failed-read handling and path isolation passed");
        }finally{s.stop(0);}
    }
}

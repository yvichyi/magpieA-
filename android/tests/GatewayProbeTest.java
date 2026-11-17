package ai.magpie.android;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public final class GatewayProbeTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        if(args.length==2){for(String protocol:new String[]{"OpenAI","Anthropic","Gemini"}){GatewayProbe.Result r=GatewayProbe.call(args[0],args[1],protocol);check(r.ok,protocol+": "+r.message);System.out.println("PASS real Go "+protocol+" "+r.message);}return;}
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
        server.createContext("/",x->{try{calls.incrementAndGet();JSONObject body=new JSONObject(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String response;
            String path=x.getRequestURI().getPath();if("a/disconnect".equals(body.optString("model"))){x.close();return;}
            if(path.endsWith("/chat/completions")){check(body.getString("model").equals("a/model/β"),"OpenAI reference changed");check("Bearer magpie".equals(x.getRequestHeaders().getFirst("Authorization")),"OpenAI key missing");check(body.getInt("max_tokens")==32,"OpenAI test is not bounded");response="{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";}
            else if(path.endsWith("/messages")){check("magpie".equals(x.getRequestHeaders().getFirst("x-api-key")),"Anthropic key missing");check(body.getInt("max_tokens")==32,"Anthropic test is not bounded");response="{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}";}
            else{check(path.endsWith("/models/a/model/β:generateContent"),"Gemini reference path changed: "+path);check("magpie".equals(x.getRequestHeaders().getFirst("x-goog-api-key")),"Gemini key missing");check(body.getJSONObject("generationConfig").getInt("maxOutputTokens")==32,"Gemini test is not bounded");response="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";}
            byte[] b=response.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);
        }catch(Throwable e){e.printStackTrace();x.sendResponseHeaders(500,-1);}finally{x.close();}});server.start();
        try{String base="http://127.0.0.1:"+server.getAddress().getPort();for(String p:new String[]{"OpenAI","Anthropic","Gemini"})check(GatewayProbe.call(base,"a/model/β",p).ok,p+" probe failed");check(calls.get()==3,"A single probe was replayed");check(!GatewayProbe.call(base,"a/disconnect","OpenAI").ok,"Disconnect accepted");check(calls.get()==4,"Disconnected POST was replayed");}
        finally{server.stop(0);}
        check(!GatewayProbe.parse("OpenAI",200,"{\"choices\":[{\"message\":{\"tool_calls\":[null]}}]}",0).ok,"Malformed tool-only reply accepted");
        check(!GatewayProbe.parse("Anthropic",200,"{\"content\":[{\"type\":\"text\",\"text\":123}]}",0).ok,"Nonstring Anthropic text accepted");
        check(!GatewayProbe.parse("Gemini",200,"{\"candidates\":[{\"content\":{\"parts\":[{\"type\":\"tool_use\"}]}}]}",0).ok,"Foreign tool schema accepted as Gemini text");
        for(String p:new String[]{"OpenAI","Anthropic","Gemini"})check(!GatewayProbe.parse(p,200,"{\"error\":\"bad\"}",0).ok,"200 error accepted");
        check(!GatewayProbe.parse("OpenAI",200,"{\"choices\":[{\"message\":{}}]}",0).ok,"Empty message accepted");
        check(!GatewayProbe.parse("OpenAI",200,"<html>bad</html>",0).ok,"HTML accepted");
        check(GatewayProbe.parse("OpenAI",429,"private-vendor-key",5).message.contains("429"),"Quota status lost");
        check(!GatewayProbe.parse("OpenAI",401,"private-vendor-key",5).message.contains("private-vendor-key"),"Raw error leaked");
        for(String base:new String[]{"http://10.0.0.1:3425","https://example.com","http://user@localhost:3425","http://localhost:3425/path"}){check(!GatewayProbe.call(base,"a/b","OpenAI").ok,"Unsafe endpoint accepted");}
        System.out.println("PASS three real HTTP protocol payloads, bounded messages, strict reply shapes, errors, origin and no replay");
    }
}

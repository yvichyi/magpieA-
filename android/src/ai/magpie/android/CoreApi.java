package ai.magpie.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Origin-bound dashboard client; native setup has two explicit POST operations. */
final class CoreApi {
    private final String origin,key;
    private String cookie;
    CoreApi(String origin,String key){this.origin=CoreConfig.remote(origin);this.key=key;}
    private HttpURLConnection connection(String path)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(origin+path).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);return c;
    }
    private void authenticate()throws IOException{
        if(cookie!=null)return;
        HttpURLConnection auth=connection("/?k="+URLEncoder.encode(key,StandardCharsets.UTF_8.name()));try{
            int status=auth.getResponseCode();String header=auth.getHeaderField("Set-Cookie");
            if((status!=302&&status!=303)||header==null)throw new IOException("管理接口认证失败（HTTP "+status+"）");
            cookie=header.split(";",2)[0];
        }finally{auth.disconnect();}
    }
    static final class HttpFailure extends IOException{
        final int status;final String body;
        HttpFailure(int status,String body){super("请求失败（HTTP "+status+"）");this.status=status;this.body=body;}
    }
    String post(String path,String body)throws IOException{
        if(!java.util.Arrays.asList("/api/provider/list","/api/provider/save","/api/plugins/add","/api/plugins/upgrade","/api/plugins/off","/api/plugins/builtin","/api/plugins/check").contains(path))throw new IllegalArgumentException("Invalid write API path");
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IllegalArgumentException("配置内容过大");
        authenticate();HttpURLConnection c=connection(path);
        if(path.equals("/api/plugins/add")||path.equals("/api/plugins/upgrade")) c.setReadTimeout(320000);
        else c.setReadTimeout(path.equals("/api/plugins/check") ? 65000 : 30000);
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Cookie",cookie);c.setRequestProperty("Origin",origin);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);
        try{try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status!=200){String error="";InputStream stream=c.getErrorStream();if(stream!=null)try(InputStream in=stream){byte[] limit=new byte[8192];int size=in.read(limit);if(size>0)error=new String(limit,0,size,StandardCharsets.UTF_8);}throw new HttpFailure(status,error);}
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("返回数据过大");out.write(buffer,0,n);}
                long size=c.getContentLengthLong();if(size>=0&&size!=out.size())throw new IOException("返回数据不完整，请确认操作结果");
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
    }
    String get(String path)throws IOException{
        if(!path.startsWith("/api/")||path.contains("..")||path.indexOf('#')>=0)throw new IllegalArgumentException("Invalid API path");
        authenticate();
        HttpURLConnection c=connection(path);c.setRequestProperty("Cookie",cookie);
        try{int status=c.getResponseCode();if(status!=200)throw new IOException("无法读取概览（HTTP "+status+"）");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[]buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("概览数据过大");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
    }
}

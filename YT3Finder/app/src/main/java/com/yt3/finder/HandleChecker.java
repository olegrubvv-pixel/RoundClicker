package com.yt3.finder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class HandleChecker {
    public enum Verdict { OCCUPIED, FREE, UNKNOWN }
    public static final class StrictResult {
        public final String status;
        public StrictResult(String status){ this.status=status; }
    }

    private static String enc(String s){
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
        catch(Exception e){ return s; }
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n, total=0;
        while((n=br.read(buf))>0 && total < 450000){ sb.append(buf,0,n); total+=n; }
        return sb.toString();
    }

    public static Verdict checkOnce(String base, String handle){
        HttpURLConnection c=null;
        try{
            URL u=new URL(base+"/@"+enc(handle));
            c=(HttpURLConnection)u.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(9000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36");
            c.setRequestProperty("Accept-Language","en-US,en;q=0.9");
            c.setRequestProperty("Cache-Control","no-cache");
            int code=c.getResponseCode();
            if(code==404) return Verdict.FREE;
            if(code==403 || code==429 || code>=500) return Verdict.UNKNOWN;
            InputStream in=(code>=400?c.getErrorStream():c.getInputStream());
            String body=in==null?"":readAll(in);
            String low=body.toLowerCase();
            String finalUrl=c.getURL().toString().toLowerCase();

            if(finalUrl.contains("/channel/uc")) return Verdict.OCCUPIED;
            if(low.contains("\"channelid\":\"uc") || low.contains("\"browseid\":\"uc") ||
               low.contains("itemprop=\"channelid\"") || low.contains("itemprop='channelid'") ||
               low.contains("subscriber") || low.contains("subscribers")) return Verdict.OCCUPIED;

            if(low.contains("this page isn't available") || low.contains("this page is not available") ||
               low.contains("page unavailable") || low.contains("\"errorcode\":404") ||
               low.contains("channel does not exist")) return Verdict.FREE;

            if(code==200 && body.length()>3000) {
                String marker="/@"+handle.toLowerCase();
                if(finalUrl.contains(marker) && (low.contains("canonical") || low.contains("ytinitialdata")))
                    return Verdict.OCCUPIED;
            }
            return Verdict.UNKNOWN;
        }catch(Exception e){ return Verdict.UNKNOWN; }
        finally{ if(c!=null)c.disconnect(); }
    }

    public static StrictResult strictCheck(String handle, boolean repeat){
        Verdict a=checkOnce("https://www.youtube.com",handle);
        if(a==Verdict.OCCUPIED) return new StrictResult("occupied");
        Verdict b=checkOnce("https://m.youtube.com",handle);
        if(b==Verdict.OCCUPIED) return new StrictResult("occupied");
        Verdict c=checkOnce("https://www.youtube.com",handle);
        if(c==Verdict.OCCUPIED) return new StrictResult("occupied");
        int free=(a==Verdict.FREE?1:0)+(b==Verdict.FREE?1:0)+(c==Verdict.FREE?1:0);
        int unknown=(a==Verdict.UNKNOWN?1:0)+(b==Verdict.UNKNOWN?1:0)+(c==Verdict.UNKNOWN?1:0);
        if(free==3){
            if(repeat){
                try{Thread.sleep(250);}catch(Exception ignored){}
                Verdict d=checkOnce("https://www.youtube.com",handle);
                if(d!=Verdict.FREE) return new StrictResult("yellow");
            }
            return new StrictResult("green");
        }
        if(free>=1 && unknown>=1) return new StrictResult("yellow");
        return new StrictResult("unknown");
    }

    public static boolean selfTest(){
        String[] known={"MrBeast","YouTube","Google"};
        for(String h:known){
            Verdict a=checkOnce("https://www.youtube.com",h);
            Verdict b=checkOnce("https://m.youtube.com",h);
            if(a!=Verdict.OCCUPIED && b!=Verdict.OCCUPIED) return false;
        }
        return true;
    }
}

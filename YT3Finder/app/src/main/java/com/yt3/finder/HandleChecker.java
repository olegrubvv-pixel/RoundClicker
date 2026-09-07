package com.yt3.finder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class HandleChecker {
    public enum Verdict { OCCUPIED, NOT_FOUND, UNKNOWN }
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
        while((n=br.read(buf))>0 && total < 700000){ sb.append(buf,0,n); total+=n; }
        return sb.toString();
    }

    private static boolean occupiedBody(String low, String handle){
        String h=handle.toLowerCase();
        return low.contains("\"channelid\":\"uc") ||
               low.contains("\"browseid\":\"uc") ||
               low.contains("itemprop=\"channelid\"") ||
               low.contains("itemprop='channelid'") ||
               low.contains("\"vanitychannelurl\":\"http://www.youtube.com/@"+h) ||
               low.contains("\"canonicalbaseurl\":\"/@"+h) ||
               low.contains("<link rel=\"canonical\" href=\"https://www.youtube.com/@"+h) ||
               low.contains("<meta property=\"og:url\" content=\"https://www.youtube.com/@"+h) ||
               low.contains("ytinitialdata") && (low.contains("subscriber") || low.contains("videos"));
    }

    public static Verdict checkUrl(String url, String handle){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(9000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language","en-US,en;q=0.9");
            c.setRequestProperty("Cache-Control","no-cache");
            int code=c.getResponseCode();
            if(code==403 || code==429 || code>=500) return Verdict.UNKNOWN;
            InputStream in=(code>=400?c.getErrorStream():c.getInputStream());
            String body=in==null?"":readAll(in);
            String low=body.toLowerCase();
            String finalUrl=c.getURL().toString().toLowerCase();
            String marker="/@"+handle.toLowerCase();

            if(finalUrl.contains("/channel/uc")) return Verdict.OCCUPIED;
            if(occupiedBody(low,handle)) return Verdict.OCCUPIED;
            if(code==200 && finalUrl.contains(marker) && body.length()>5000 && low.contains("ytcfg")) return Verdict.OCCUPIED;

            // IMPORTANT: 404 is only absence-of-public-page evidence. It is NEVER proof that a handle is claimable.
            if(code==404 || low.contains("this page isn't available") || low.contains("this page is not available") ||
               low.contains("page unavailable") || low.contains("channel does not exist")) return Verdict.NOT_FOUND;

            return Verdict.UNKNOWN;
        }catch(Exception e){ return Verdict.UNKNOWN; }
        finally{ if(c!=null)c.disconnect(); }
    }

    private static Verdict checkPath(String host, String handle, String suffix){
        return checkUrl(host+"/@"+enc(handle)+suffix,handle);
    }

    public static StrictResult strictCheck(String handle, boolean repeat){
        Verdict[] v = new Verdict[]{
            checkPath("https://www.youtube.com",handle,""),
            checkPath("https://www.youtube.com",handle,"/about"),
            checkPath("https://m.youtube.com",handle,""),
            checkPath("https://m.youtube.com",handle,"/videos")
        };
        int notFound=0, unknown=0;
        for(Verdict x:v){
            if(x==Verdict.OCCUPIED) return new StrictResult("occupied");
            if(x==Verdict.NOT_FOUND) notFound++; else unknown++;
        }

        if(repeat && notFound>=3){
            try{Thread.sleep(350);}catch(Exception ignored){}
            Verdict again=checkPath("https://www.youtube.com",handle,"/about");
            if(again==Verdict.OCCUPIED) return new StrictResult("occupied");
            if(again==Verdict.UNKNOWN) unknown++;
            else notFound++;
        }

        // Public checks cannot prove that YouTube will allow assignment, so automatic GREEN is forbidden.
        // Strong absence evidence is YELLOW; network/ambiguous results are hidden.
        if(notFound>=3 && unknown<=2) return new StrictResult("yellow");
        return new StrictResult("unknown");
    }

    public static boolean selfTest(){
        // These are intentionally short/high-demand occupied handles too, because they exposed the old false-positive bug.
        String[] known={"MrBeast","YouTube","Google","777","aaa","l1l"};
        for(String h:known){
            Verdict a=checkPath("https://www.youtube.com",h,"");
            Verdict b=checkPath("https://www.youtube.com",h,"/about");
            Verdict c=checkPath("https://m.youtube.com",h,"");
            if(a!=Verdict.OCCUPIED && b!=Verdict.OCCUPIED && c!=Verdict.OCCUPIED) return false;
        }
        return true;
    }
}

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
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); char[] buf=new char[8192]; int n,total=0;
        while((n=br.read(buf))>0 && total<900000){sb.append(buf,0,n);total+=n;}
        return sb.toString();
    }

    private static boolean occupiedBody(String low,String handle){
        String h=handle.toLowerCase();
        return low.contains("\"channelid\":\"uc") ||
               low.contains("\"browseid\":\"uc") ||
               low.contains("itemprop=\"channelid\"") ||
               low.contains("itemprop='channelid'") ||
               low.contains("\"vanitychannelurl\":\"http://www.youtube.com/@"+h) ||
               low.contains("\"canonicalbaseurl\":\"/@"+h) ||
               low.contains("<link rel=\"canonical\" href=\"https://www.youtube.com/@"+h) ||
               low.contains("<meta property=\"og:url\" content=\"https://www.youtube.com/@"+h) ||
               (low.contains("ytinitialdata") && low.contains("metadata") && (low.contains("subscriber")||low.contains("videos")));
    }

    public static Verdict checkUrl(String url,String handle){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000); c.setReadTimeout(14000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language","en-US,en;q=0.9");
            c.setRequestProperty("Cache-Control","no-cache, no-store");
            int code=c.getResponseCode();
            if(code==403||code==408||code==429||code>=500)return Verdict.UNKNOWN;
            InputStream in=(code>=400?c.getErrorStream():c.getInputStream());
            String body=in==null?"":readAll(in), low=body.toLowerCase();
            String finalUrl=c.getURL().toString().toLowerCase();
            String marker="/@"+handle.toLowerCase();

            if(finalUrl.contains("/channel/uc"))return Verdict.OCCUPIED;
            if(occupiedBody(low,handle))return Verdict.OCCUPIED;
            if(code==200 && finalUrl.contains(marker) && body.length()>5000 && (low.contains("ytcfg")||low.contains("ytinitialdata")))return Verdict.OCCUPIED;

            // Never treat 404 as proof of claimability. It is only one absence signal.
            if(code==404 || low.contains("this page isn't available") || low.contains("this page is not available") ||
               low.contains("page unavailable") || low.contains("channel does not exist"))return Verdict.NOT_FOUND;
            return Verdict.UNKNOWN;
        }catch(Exception e){return Verdict.UNKNOWN;}
        finally{if(c!=null)c.disconnect();}
    }

    private static Verdict checkPath(String host,String handle,String suffix){return checkUrl(host+"/@"+enc(handle)+suffix,handle);}

    private static Verdict[] round(String handle){
        return new Verdict[]{
            checkPath("https://www.youtube.com",handle,""),
            checkPath("https://www.youtube.com",handle,"/about"),
            checkPath("https://www.youtube.com",handle,"/videos"),
            checkPath("https://www.youtube.com",handle,"/shorts"),
            checkPath("https://m.youtube.com",handle,""),
            checkPath("https://m.youtube.com",handle,"/about")
        };
    }

    private static int[] summarize(Verdict[] vs){
        int occupied=0,notFound=0,unknown=0;
        for(Verdict v:vs){if(v==Verdict.OCCUPIED)occupied++;else if(v==Verdict.NOT_FOUND)notFound++;else unknown++;}
        return new int[]{occupied,notFound,unknown};
    }

    public static StrictResult strictCheck(String handle,boolean repeat){
        // Round 1: six independent public surfaces. ANY occupied signal rejects immediately.
        int[] a=summarize(round(handle));
        if(a[0]>0)return new StrictResult("occupied");
        // For maximum precision, even a single UNKNOWN hides the candidate.
        if(a[2]>0 || a[1]<6)return new StrictResult("unknown");

        if(repeat){
            try{Thread.sleep(700);}catch(Exception ignored){}
            // Round 2 repeats all six after a delay to catch transient YouTube responses/caches.
            int[] b=summarize(round(handle));
            if(b[0]>0)return new StrictResult("occupied");
            if(b[2]>0 || b[1]<6)return new StrictResult("unknown");
        }

        // Still NOT green: public page absence cannot prove YouTube will allow assignment.
        return new StrictResult("yellow");
    }

    public static boolean selfTest(){
        String[] known={"MrBeast","YouTube","Google","777","aaa","l1l"};
        for(String h:known){
            int[] s=summarize(round(h));
            if(s[0]<1)return false;
        }
        return true;
    }
}

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
        try{return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");}catch(Exception e){return s;}
    }

    private static String readAll(InputStream in)throws IOException{
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();char[] buf=new char[8192];int n,total=0;
        while((n=br.read(buf))>0&&total<1000000){sb.append(buf,0,n);total+=n;}
        return sb.toString();
    }

    private static boolean occupiedBody(String low,String handle){
        String h=handle.toLowerCase();
        return low.contains("\"channelid\":\"uc")||low.contains("\"browseid\":\"uc")||
               low.contains("itemprop=\"channelid\"")||low.contains("itemprop='channelid'")||
               low.contains("\"vanitychannelurl\":\"http://www.youtube.com/@"+h)||
               low.contains("\"canonicalbaseurl\":\"/@"+h)||
               low.contains("<link rel=\"canonical\" href=\"https://www.youtube.com/@"+h)||
               low.contains("<meta property=\"og:url\" content=\"https://www.youtube.com/@"+h)||
               low.contains("\"webcommandmetadata\":")&&low.contains("/@"+h)||
               low.contains("ytinitialdata")&&low.contains("metadata")&&(low.contains("subscriber")||low.contains("videos"));
    }

    public static Verdict checkUrl(String url,String handle){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(11000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language","en-US,en;q=0.9");
            c.setRequestProperty("Cache-Control","no-cache, no-store, max-age=0");
            c.setRequestProperty("Pragma","no-cache");
            int code=c.getResponseCode();
            if(code==403||code==408||code==409||code==425||code==429||code>=500)return Verdict.UNKNOWN;
            InputStream in=(code>=400?c.getErrorStream():c.getInputStream());
            String body=in==null?"":readAll(in),low=body.toLowerCase();
            String finalUrl=c.getURL().toString().toLowerCase(),marker="/@"+handle.toLowerCase();
            if(finalUrl.contains("/channel/uc"))return Verdict.OCCUPIED;
            if(occupiedBody(low,handle))return Verdict.OCCUPIED;
            if(code==200&&finalUrl.contains(marker)&&body.length()>5000&&(low.contains("ytcfg")||low.contains("ytinitialdata")))return Verdict.OCCUPIED;
            if(code==404||low.contains("this page isn't available")||low.contains("this page is not available")||low.contains("page unavailable")||low.contains("channel does not exist"))return Verdict.NOT_FOUND;
            return Verdict.UNKNOWN;
        }catch(Exception e){return Verdict.UNKNOWN;}finally{if(c!=null)c.disconnect();}
    }

    private static Verdict checkPath(String host,String handle,String suffix,long nonce){
        String join=suffix.contains("?")?"&":"?";
        return checkUrl(host+"/@"+enc(handle)+suffix+join+"_yt3="+nonce,handle);
    }

    private static Verdict[] round(String handle,long nonce){
        return new Verdict[]{
            checkPath("https://www.youtube.com",handle,"",nonce),
            checkPath("https://www.youtube.com",handle,"/about",nonce+1),
            checkPath("https://www.youtube.com",handle,"/videos",nonce+2),
            checkPath("https://www.youtube.com",handle,"/shorts",nonce+3),
            checkPath("https://m.youtube.com",handle,"",nonce+4),
            checkPath("https://m.youtube.com",handle,"/about",nonce+5),
            checkPath("https://m.youtube.com",handle,"/videos",nonce+6),
            checkPath("https://m.youtube.com",handle,"/shorts",nonce+7)
        };
    }

    private static int[] summarize(Verdict[] vs){
        int o=0,n=0,u=0;for(Verdict v:vs){if(v==Verdict.OCCUPIED)o++;else if(v==Verdict.NOT_FOUND)n++;else u++;}return new int[]{o,n,u};
    }

    public static StrictResult strictCheck(String handle,boolean repeat){
        // 3 complete rounds x 8 surfaces = 24 consistent absence signals required.
        // Any occupied or unknown result rejects the candidate.
        int rounds=repeat?3:1;
        for(int r=0;r<rounds;r++){
            int[] s=summarize(round(handle,System.nanoTime()+r*100));
            if(s[0]>0)return new StrictResult("occupied");
            if(s[2]>0||s[1]<8)return new StrictResult("unknown");
            if(r<rounds-1){try{Thread.sleep(900L+r*350L);}catch(Exception ignored){}}
        }
        // Public absence is still only a candidate, never guaranteed claimability.
        return new StrictResult("yellow");
    }

    public static boolean selfTest(){
        String[] known={"MrBeast","YouTube","Google","777","aaa","l1l"};
        for(String h:known){
            int[] s=summarize(round(h,System.nanoTime()));
            if(s[0]<1)return false;
        }
        return true;
    }
}

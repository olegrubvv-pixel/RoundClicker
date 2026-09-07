package com.yt3.finder;

import java.util.*;

public final class CandidateGenerator {
    private static final String LAT="abcdefghijklmnopqrstuvwxyz";
    private static final String DIG="0123456789";

    public static List<String> basic(){
        ArrayList<String> out=new ArrayList<>(51840);
        String edge=LAT+DIG;
        String middle=LAT+DIG+"_-.·";
        for(int i=0;i<edge.length();i++) for(int j=0;j<middle.length();j++) for(int k=0;k<edge.length();k++)
            out.add(""+edge.charAt(i)+middle.charAt(j)+edge.charAt(k));
        out.sort((a,b)->Integer.compare(score(b),score(a)));
        return out;
    }

    private static int score(String s){
        int n=0;
        if(s.charAt(0)==s.charAt(1)&&s.charAt(1)==s.charAt(2))n+=1000;
        if(s.charAt(0)==s.charAt(2))n+=700;
        if(s.charAt(0)==s.charAt(1)||s.charAt(1)==s.charAt(2))n+=450;
        if(Character.isDigit(s.charAt(0))||Character.isDigit(s.charAt(1))||Character.isDigit(s.charAt(2)))n+=40;
        if("_-.·".indexOf(s.charAt(1))>=0)n+=100;
        return n;
    }

    private static final int[][] RANGES={
        {0x0061,0x024F},{0x0370,0x03FF},{0x0400,0x052F},{0x0530,0x058F},{0x0590,0x05FF},{0x0600,0x06FF},
        {0x0900,0x097F},{0x0980,0x09FF},{0x0A00,0x0A7F},{0x0A80,0x0AFF},{0x0B80,0x0BFF},{0x0C00,0x0C7F},
        {0x0C80,0x0CFF},{0x0D00,0x0D7F},{0x0D80,0x0DFF},{0x0E00,0x0E7F},{0x0E80,0x0EFF},{0x1000,0x109F},
        {0x10A0,0x10FF},{0x1200,0x137F},{0x1780,0x17FF},{0x3040,0x309F},{0x30A0,0x30FF},{0x4E00,0x9FFF},{0xAC00,0xD7A3}
    };

    public static List<String> internationalPretty(){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        ArrayList<int[]> pools=new ArrayList<>();
        for(int[] range:RANGES)pools.add(letters(range[0],range[1],120));
        for(int[] cps:pools){
            for(int a:cps){
                String A=cp(a);
                set.add(A+A+A);set.add(A+"1"+A);set.add(A+"2"+A);set.add(A+"_"+A);set.add(A+"-"+A);set.add(A+"."+A);set.add(A+"·"+A);
                set.add(A+A+"1");set.add(A+A+"2");set.add("1"+A+A);set.add("2"+A+A);
            }
            int lim=Math.min(cps.length,36);
            for(int i=0;i<lim;i++)for(int j=0;j<lim;j++){
                String A=cp(cps[i]),B=cp(cps[j]);set.add(A+B+A);set.add(A+A+B);set.add(A+B+B);
            }
        }
        for(char d='0';d<='9';d++)for(char e='0';e<='9';e++){
            String D=String.valueOf(d),E=String.valueOf(e);set.add(D+D+D);set.add(D+"_"+E);set.add(D+E+D);set.add(D+D+E);
        }
        ArrayList<String> out=new ArrayList<>(set);
        Collections.shuffle(out,new Random(20260907L));
        out.sort((a,b)->Integer.compare(beauty(b),beauty(a)));
        return out;
    }

    private static int[] letters(int from,int to,int cap){
        int[] tmp=new int[cap];int n=0;int span=Math.max(1,to-from+1),step=Math.max(1,span/(cap*3));
        for(int cp=from;cp<=to&&n<cap;cp+=step)if(Character.isLetter(cp))tmp[n++]=cp;
        if(n<cap)for(int cp=from;cp<=to&&n<cap;cp++)if(Character.isLetter(cp)&&!contains(tmp,n,cp))tmp[n++]=cp;
        return Arrays.copyOf(tmp,n);
    }
    private static boolean contains(int[] a,int n,int x){for(int i=0;i<n;i++)if(a[i]==x)return true;return false;}
    private static String cp(int cp){return new String(Character.toChars(cp));}
    private static int beauty(String s){
        int[] cp=s.codePoints().toArray();if(cp.length!=3)return 0;int n=0;
        if(cp[0]==cp[1]&&cp[1]==cp[2])n+=1000;if(cp[0]==cp[2])n+=700;if(cp[0]==cp[1]||cp[1]==cp[2])n+=450;
        if(cp[1]=='_'||cp[1]=='-'||cp[1]=='.'||cp[1]=='·')n+=120;if(Character.isDigit(cp[0])||Character.isDigit(cp[1])||Character.isDigit(cp[2]))n+=60;
        return n;
    }
}

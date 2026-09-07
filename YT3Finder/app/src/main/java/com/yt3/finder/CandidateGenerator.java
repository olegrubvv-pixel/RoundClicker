package com.yt3.finder;

import java.util.*;

public final class CandidateGenerator {
    private static final String LAT="abcdefghijklmnopqrstuvwxyz";
    private static final String DIG="0123456789";
    private static final String SEP="_-.·";

    public static List<String> basic(){
        ArrayList<String> out=new ArrayList<>(51840);
        String edge=LAT+DIG;
        String middle=LAT+DIG+SEP;
        for(int i=0;i<edge.length();i++) for(int j=0;j<middle.length();j++) for(int k=0;k<edge.length();k++)
            out.add(""+edge.charAt(i)+middle.charAt(j)+edge.charAt(k));
        out.sort((a,b)->Integer.compare(score(b),score(a)));
        return out;
    }

    // Kept under the old method name so the rest of the app does not break.
    // It is now LATIN ONLY: letters a-z, digits 0-9 and valid middle separators.
    public static List<String> internationalPretty(){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        String edge=LAT+DIG;

        // aaa, 333, a2a, 1a1, aba, aa1, 1aa, abb
        for(int i=0;i<edge.length();i++){
            char a=edge.charAt(i);
            set.add(""+a+a+a);
            for(int j=0;j<edge.length();j++){
                char b=edge.charAt(j);
                set.add(""+a+b+a);
                set.add(""+a+a+b);
                set.add(""+b+a+a);
                set.add(""+a+b+b);
            }
            // a_a, a-a, a.a, a·a
            for(int j=0;j<SEP.length();j++) set.add(""+a+SEP.charAt(j)+a);
        }

        // 1_2, a_1, 1_a and equivalent separator patterns.
        for(int i=0;i<edge.length();i++) for(int j=0;j<edge.length();j++){
            char a=edge.charAt(i), b=edge.charAt(j);
            for(int k=0;k<SEP.length();k++) set.add(""+a+SEP.charAt(k)+b);
        }

        ArrayList<String> out=new ArrayList<>(set);
        out.sort((a,b)->Integer.compare(score(b),score(a)));
        return out;
    }

    private static int score(String s){
        int n=0;
        if(s.charAt(0)==s.charAt(1)&&s.charAt(1)==s.charAt(2))n+=2000;
        if(s.charAt(0)==s.charAt(2))n+=1400;
        if(s.charAt(0)==s.charAt(1)||s.charAt(1)==s.charAt(2))n+=900;
        if(Character.isDigit(s.charAt(0))||Character.isDigit(s.charAt(1))||Character.isDigit(s.charAt(2)))n+=80;
        if(SEP.indexOf(s.charAt(1))>=0)n+=180;
        return n;
    }
}

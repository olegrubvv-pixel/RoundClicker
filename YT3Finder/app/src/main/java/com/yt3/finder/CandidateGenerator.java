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
        // Stable random order: avoids wasting the first hours on obvious premium handles,
        // while keeping exactly the same order after pause/restart.
        Collections.shuffle(out,new Random(0x59335431L));
        return out;
    }

    // Legacy compatibility. There is no second/pretty/international mode anymore.
    public static List<String> internationalPretty(){ return basic(); }
}

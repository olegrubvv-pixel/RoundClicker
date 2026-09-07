package com.yt3.finder;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanService extends Service {
    public static final String ACTION_START="com.yt3.finder.START";
    public static final String ACTION_PAUSE="com.yt3.finder.PAUSE";
    public static final String ACTION_RESET="com.yt3.finder.RESET";
    private static final int VERIFIER_VERSION=8;
    private static final String CHANNEL="yt3_scan";

    private final AtomicBoolean stop=new AtomicBoolean(false);
    private ExecutorService runner;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;

    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences("yt3",MODE_PRIVATE);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"YT3 поиск",NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String text){
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setContentTitle("YT3 Finder").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).setContentIntent(pi).build();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_PAUSE.equals(action)){pauseNow();return START_NOT_STICKY;}
        if(ACTION_RESET.equals(action)){pauseNow();resetData();return START_NOT_STICKY;}
        if(ACTION_START.equals(action))startScan();
        return START_STICKY;
    }

    private synchronized void startScan(){
        if(runner!=null&&!runner.isShutdown())return;
        stop.set(false);startForeground(7,notification("Этап 1/2: быстрый отсев YouTube…"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YT3Finder:scan");wakeLock.acquire(6*60*60*1000L);
        runner=Executors.newSingleThreadExecutor();runner.submit(this::runScan);
    }

    private boolean isBlacklisted(String h){return prefs.getStringSet("blacklist",Collections.emptySet()).contains(h.toLowerCase(Locale.ROOT));}
    private void addBlacklist(String h){Set<String>b=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));b.add(h.toLowerCase(Locale.ROOT));prefs.edit().putStringSet("blacklist",b).apply();}

    private void runScan(){
        try{
            if(prefs.getInt("verifier_version",0)!=VERIFIER_VERSION){
                Set<String> oldBlacklist=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));
                deleteFile("candidates.tsv");deleteFile("survivors.tsv");
                prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).putStringSet("blacklist",oldBlacklist).putInt("phase",1).apply();
            }
            prefs.edit().putBoolean("running",true).putString("error","").apply();
            int phase=prefs.getInt("phase",1);
            if(phase<=1&&!stop.get())runPhase1();
            if(prefs.getInt("phase",1)==2&&!stop.get())runPhase2();
        }finally{
            prefs.edit().putBoolean("running",false).apply();
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            synchronized(this){if(runner!=null){runner.shutdownNow();runner=null;}}
        }
    }

    private void runPhase1(){
        List<String> all=CandidateGenerator.basic();
        int index=Math.min(prefs.getInt("phase1_index",0),all.size());
        int removed=prefs.getInt("prefilter_removed",0);
        int survived=prefs.getInt("survivor_count",readLines("survivors.tsv").size());
        prefs.edit().putInt("phase",1).putInt("total",all.size()).putInt("phase1_total",all.size()).apply();

        ExecutorService pool=Executors.newFixedThreadPool(12);
        while(index<all.size()&&!stop.get()){
            int end=Math.min(index+24,all.size());
            List<Future<FastOne>> fs=new ArrayList<>();
            for(int i=index;i<end;i++){
                final String h=all.get(i);
                if(isBlacklisted(h)){removed++;continue;}
                fs.add(pool.submit(()->new FastOne(h,HandleChecker.fastPrefilter(h))));
            }
            for(Future<FastOne> f:fs){
                if(stop.get())break;
                try{
                    FastOne one=f.get();
                    if(one.verdict==HandleChecker.Verdict.OCCUPIED){addBlacklist(one.handle);removed++;}
                    else{appendUnique("survivors.tsv",one.handle);survived++;}
                }catch(Exception ignored){}
            }
            if(stop.get())break;
            index=end;
            prefs.edit().putInt("phase1_index",index).putInt("prefilter_removed",removed).putInt("survivor_count",survived).putBoolean("running",true).apply();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Этап 1/2: "+index+" / "+all.size()+" · отсеяно "+removed+" · осталось "+survived));
        }
        pool.shutdownNow();
        if(index>=all.size()&&!stop.get()){
            List<String> survivors=readLines("survivors.tsv");
            prefs.edit().putInt("phase",2).putInt("phase2_index",0).putInt("phase2_total",survivors.size()).putInt("survivor_count",survivors.size()).apply();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Этап 1 завершён. Осталось "+survivors.size()+". Начинаю сверхстрогую проверку…"));
        }
    }

    private void runPhase2(){
        List<String> survivors=readLines("survivors.tsv");
        int index=Math.min(prefs.getInt("phase2_index",0),survivors.size());
        int found=readLines("candidates.tsv").size();
        prefs.edit().putInt("phase",2).putInt("phase2_total",survivors.size()).putInt("candidate_count",found).apply();

        ExecutorService pool=Executors.newFixedThreadPool(2);
        while(index<survivors.size()&&!stop.get()){
            int end=Math.min(index+2,survivors.size());
            List<Future<DeepOne>> fs=new ArrayList<>();
            for(int i=index;i<end;i++){
                final String h=survivors.get(i);
                if(isBlacklisted(h))continue;
                fs.add(pool.submit(()->new DeepOne(h,HandleChecker.strictCheck(h,true).status)));
            }
            for(Future<DeepOne> f:fs){
                if(stop.get())break;
                try{
                    DeepOne one=f.get();
                    if("occupied".equals(one.status)){addBlacklist(one.handle);continue;}
                    if(!"yellow".equals(one.status)||isBlacklisted(one.handle))continue;
                    try{Thread.sleep(500);}catch(Exception ignored){}
                    String second=HandleChecker.strictCheck(one.handle,true).status;
                    if("occupied".equals(second)){addBlacklist(one.handle);continue;}
                    if("yellow".equals(second)&&!isBlacklisted(one.handle)){appendUnique("candidates.tsv",one.handle);found=readLines("candidates.tsv").size();}
                }catch(Exception ignored){}
            }
            if(stop.get())break;
            index=end;
            prefs.edit().putInt("phase2_index",index).putInt("candidate_count",found).putBoolean("running",true).apply();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Этап 2/2: "+index+" / "+survivors.size()+" · кандидатов "+found));
        }
        pool.shutdownNow();
        if(index>=survivors.size()&&!stop.get()){
            prefs.edit().putInt("phase",3).putBoolean("running",false).putString("error","Готово. Быстрый отсев завершён, весь остаток прошёл сверхстрогую проверку.").apply();
        }
    }

    private static class FastOne{final String handle;final HandleChecker.Verdict verdict;FastOne(String h,HandleChecker.Verdict v){handle=h;verdict=v;}}
    private static class DeepOne{final String handle,status;DeepOne(String h,String s){handle=h;status=s;}}

    private void appendUnique(String file,String h){
        if(isBlacklisted(h))return;
        List<String> rows=readLines(file);if(rows.contains(h))return;
        try(FileOutputStream fos=openFileOutput(file,MODE_APPEND)){fos.write((h+"\n").getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
    private List<String> readLines(String file){
        ArrayList<String> out=new ArrayList<>();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(openFileInput(file),StandardCharsets.UTF_8))){String s;while((s=br.readLine())!=null){s=s.trim();if(!s.isEmpty()&&!out.contains(s))out.add(s);}}catch(Exception ignored){}
        return out;
    }
    private void resetData(){
        Set<String> blacklist=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));
        deleteFile("candidates.tsv");deleteFile("survivors.tsv");
        prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).putStringSet("blacklist",blacklist).putInt("phase",1).apply();
    }
    private synchronized void pauseNow(){
        stop.set(true);prefs.edit().putBoolean("running",false).apply();if(runner!=null)runner.shutdownNow();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

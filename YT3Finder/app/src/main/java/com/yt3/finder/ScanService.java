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
    private static final int VERIFIER_VERSION=4;
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
        stop.set(false);startForeground(7,notification("Идёт сверхстрогий поиск…"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YT3Finder:scan");wakeLock.acquire(6*60*60*1000L);
        runner=Executors.newSingleThreadExecutor();runner.submit(this::runScan);
    }

    private void runScan(){
        try{
            if(prefs.getInt("verifier_version",0)!=VERIFIER_VERSION){
                deleteFile("candidates.tsv");
                prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).apply();
            }
            List<String> list=CandidateGenerator.basic();
            int index=Math.min(prefs.getInt("index",0),list.size());
            int found=prefs.getInt("candidate_count",0);
            prefs.edit().putInt("total",list.size()).putBoolean("running",true).putString("error","").apply();

            // Fewer workers deliberately reduce YouTube throttling and false ambiguity.
            ExecutorService pool=Executors.newFixedThreadPool(2);
            while(index<list.size()&&!stop.get()){
                int end=Math.min(index+2,list.size());
                List<Future<One>> fs=new ArrayList<>();
                for(int i=index;i<end;i++){
                    final String h=list.get(i);
                    fs.add(pool.submit(()->new One(h,HandleChecker.strictCheck(h,true).status)));
                }
                for(Future<One> f:fs){
                    if(stop.get())break;
                    try{
                        One one=f.get();
                        if("yellow".equals(one.status)){
                            // One more full ultra-strict verification before storing.
                            HandleChecker.StrictResult finalCheck=HandleChecker.strictCheck(one.handle,true);
                            if("yellow".equals(finalCheck.status)){
                                appendCandidate(one.handle);
                                found++;
                                prefs.edit().putString("latest_candidate",one.handle).putInt("candidate_count",found).apply();
                            }
                        }
                    }catch(Exception ignored){}
                }
                if(stop.get())break;
                index=end;
                prefs.edit().putInt("index",index).putInt("candidate_count",found).putBoolean("running",true).apply();
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Проверено "+index+" / "+list.size()+" · кандидатов "+found));
                try{Thread.sleep(180);}catch(Exception ignored){}
            }
            pool.shutdownNow();
            if(index>=list.size()&&!stop.get())prefs.edit().putBoolean("running",false).putString("error","Проверка всех 51 840 вариантов завершена.").apply();
        }finally{
            prefs.edit().putBoolean("running",false).apply();
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            synchronized(this){if(runner!=null){runner.shutdownNow();runner=null;}}
        }
    }

    private void appendCandidate(String h){
        try(FileOutputStream fos=openFileOutput("candidates.tsv",MODE_APPEND)){fos.write((h+"\n").getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
    private static class One{final String handle,status;One(String h,String s){handle=h;status=s;}}
    private void resetData(){deleteFile("candidates.tsv");prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).apply();}
    private synchronized void pauseNow(){
        stop.set(true);prefs.edit().putBoolean("running",false).apply();
        if(runner!=null)runner.shutdownNow();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

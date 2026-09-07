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
    public static final String EXTRA_MODE="mode";
    public static final String MODE_BASIC="basic";
    public static final String MODE_INTL="intl"; // legacy id; now means pretty Latin only
    private static final int VERIFIER_VERSION=2;

    private final AtomicBoolean stop=new AtomicBoolean(false);
    private ExecutorService runner;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;
    private static final String CHANNEL="yt3_scan";

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences("yt3",MODE_PRIVATE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"YT3 поиск",NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String text){
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setContentTitle("YT3 Finder").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).setContentIntent(pi).build();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_PAUSE.equals(action)){pauseNow();return START_NOT_STICKY;}
        if(ACTION_RESET.equals(action)){pauseNow();resetData();return START_NOT_STICKY;}
        if(ACTION_START.equals(action)){
            String mode=intent.getStringExtra(EXTRA_MODE);if(mode==null)mode=MODE_BASIC;startScan(mode);
        }
        return START_STICKY;
    }

    private synchronized void startScan(String mode){
        if(runner!=null&&!runner.isShutdown())return;
        stop.set(false);startForeground(7,notification("Подготовка строгой проверки…"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YT3Finder:scan");wakeLock.acquire(6*60*60*1000L);
        runner=Executors.newSingleThreadExecutor();final String m=mode;runner.submit(()->runScan(m));
    }

    private void runScan(String mode){
        try{
            if(prefs.getInt("verifier_version",0)!=VERIFIER_VERSION){
                clearFiles();
                prefs.edit().putInt("index",0).putInt("green",0).putInt("yellow",0)
                    .putInt("verifier_version",VERIFIER_VERSION).putString("mode",mode).apply();
            }
            if(!HandleChecker.selfTest()){
                prefs.edit().putBoolean("selftest",false).putBoolean("running",false)
                    .putString("error","Самотест не пройден. Поиск остановлен — ложные кандидаты не допускаются.").apply();return;
            }
            prefs.edit().putBoolean("selftest",true).putString("error","").apply();
            String oldMode=prefs.getString("mode","");
            if(!mode.equals(oldMode)){
                prefs.edit().putInt("index",0).putInt("green",0).putInt("yellow",0).putString("mode",mode).apply();clearFiles();
            }
            List<String> list=MODE_INTL.equals(mode)?CandidateGenerator.internationalPretty():CandidateGenerator.basic();
            prefs.edit().putInt("total",list.size()).putBoolean("running",true).putString("mode",mode).apply();
            int index=Math.min(prefs.getInt("index",0),list.size());int green=0,yellow=prefs.getInt("yellow",0);
            ExecutorService pool=Executors.newFixedThreadPool(4);
            while(index<list.size()&&!stop.get()){
                int end=Math.min(index+4,list.size());List<Future<One>> fs=new ArrayList<>();
                for(int i=index;i<end;i++){final String h=list.get(i);fs.add(pool.submit(()->new One(h,HandleChecker.strictCheck(h,true).status)));}
                for(Future<One> f:fs){
                    if(stop.get())break;
                    try{One one=f.get();if("yellow".equals(one.status)){append("yellow.tsv",one.handle+"\n");yellow++;}}catch(Exception ignored){}
                }
                if(stop.get())break;
                index=end;
                prefs.edit().putInt("index",index).putInt("green",0).putInt("yellow",yellow).putBoolean("running",true).apply();
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Проверено "+index+" / "+list.size()+" · строгих кандидатов "+yellow));
            }
            pool.shutdownNow();prefs.edit().putBoolean("running",false).apply();
        }finally{
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            synchronized(this){if(runner!=null){runner.shutdownNow();runner=null;}}
        }
    }

    private static class One{final String handle,status;One(String h,String s){handle=h;status=s;}}
    private void append(String name,String text){try(FileOutputStream fos=openFileOutput(name,MODE_APPEND)){fos.write(text.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private void clearFiles(){deleteFile("green.tsv");deleteFile("yellow.tsv");}
    private void resetData(){clearFiles();prefs.edit().clear().apply();}
    private synchronized void pauseNow(){stop.set(true);prefs.edit().putBoolean("running",false).apply();if(runner!=null)runner.shutdownNow();if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}

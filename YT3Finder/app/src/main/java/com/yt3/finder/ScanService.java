package com.yt3.finder;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanService extends Service {
    public static final String ACTION_START="com.yt3.finder.START";
    public static final String ACTION_PAUSE="com.yt3.finder.PAUSE";
    public static final String ACTION_RESET="com.yt3.finder.RESET";
    public static final String MODE_BASIC="basic";
    private static final int VERIFIER_VERSION=3;
    private static final String CHANNEL="yt3_scan";

    private final AtomicBoolean stop=new AtomicBoolean(false);
    private ExecutorService runner;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;

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
        if(ACTION_START.equals(action)) startScan();
        return START_STICKY;
    }

    private synchronized void startScan(){
        if(runner!=null&&!runner.isShutdown())return;
        stop.set(false);
        startForeground(7,notification("Ищу первый строгий кандидат…"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YT3Finder:scan");
        wakeLock.acquire(6*60*60*1000L);
        runner=Executors.newSingleThreadExecutor();
        runner.submit(this::runScan);
    }

    private void runScan(){
        try{
            if(prefs.getInt("verifier_version",0)!=VERIFIER_VERSION){
                prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).apply();
            }
            // When the user asks for the next candidate, discard the previous candidate and resume.
            prefs.edit().remove("candidate").putString("error","").apply();

            List<String> list=CandidateGenerator.basic();
            int index=Math.min(prefs.getInt("index",0),list.size());
            prefs.edit().putInt("total",list.size()).putBoolean("running",true).apply();

            ExecutorService pool=Executors.newFixedThreadPool(4);
            while(index<list.size()&&!stop.get()){
                int end=Math.min(index+4,list.size());
                List<Future<One>> fs=new ArrayList<>();
                for(int i=index;i<end;i++){
                    final String h=list.get(i);
                    fs.add(pool.submit(()->new One(h,HandleChecker.strictCheck(h,true).status)));
                }
                String found=null;
                for(Future<One> f:fs){
                    if(stop.get())break;
                    try{
                        One one=f.get();
                        if(found==null&&"yellow".equals(one.status)) found=one.handle;
                    }catch(Exception ignored){}
                }
                index=end;
                prefs.edit().putInt("index",index).putBoolean("running",true).apply();

                if(found!=null){
                    prefs.edit().putString("candidate",found).putBoolean("running",false)
                        .putString("error","Нашёл строгий кандидат. Проверь его в самом YouTube.").apply();
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Кандидат найден: @"+found));
                    break;
                }
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Проверено "+index+" / "+list.size()));
            }
            pool.shutdownNow();
            if(index>=list.size()&&!stop.get()&&prefs.getString("candidate","").isEmpty()){
                prefs.edit().putBoolean("running",false).putString("error","Все 51 840 вариантов проверены. Строгий кандидат не найден.").apply();
            }
        }finally{
            prefs.edit().putBoolean("running",false).apply();
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            synchronized(this){if(runner!=null){runner.shutdownNow();runner=null;}}
        }
    }

    private static class One{final String handle,status;One(String h,String s){handle=h;status=s;}}
    private void resetData(){prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).apply();}
    private synchronized void pauseNow(){
        stop.set(true);prefs.edit().putBoolean("running",false).apply();
        if(runner!=null)runner.shutdownNow();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

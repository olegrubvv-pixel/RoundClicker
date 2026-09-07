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
    private static final int VERIFIER_VERSION=6;
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
        stop.set(false);startForeground(7,notification("Идёт усиленный поиск…"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YT3Finder:scan");wakeLock.acquire(6*60*60*1000L);
        runner=Executors.newSingleThreadExecutor();runner.submit(this::runScan);
    }

    private boolean isBlacklisted(String h){
        Set<String> b=prefs.getStringSet("blacklist",Collections.emptySet());
        return b.contains(h.toLowerCase(Locale.ROOT));
    }

    private void addBlacklist(String h){
        Set<String> b=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));
        b.add(h.toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet("blacklist",b).apply();
    }

    private void runScan(){
        try{
            if(prefs.getInt("verifier_version",0)!=VERIFIER_VERSION){
                deleteFile("candidates.tsv");
                Set<String> oldBlacklist=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));
                prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).putStringSet("blacklist",oldBlacklist).apply();
            }
            List<String> list=CandidateGenerator.basic();
            int index=Math.min(prefs.getInt("index",0),list.size());
            int found=readCandidates().size();
            prefs.edit().putInt("candidate_count",found).putInt("total",list.size()).putBoolean("running",true).putString("error","").apply();

            // Before continuing the scan, clean already saved candidates that are now clearly occupied.
            found=autoCleanCandidates(found);

            ExecutorService pool=Executors.newFixedThreadPool(2);
            int sinceCleanup=0;
            while(index<list.size()&&!stop.get()){
                int end=Math.min(index+2,list.size());
                List<Future<One>> fs=new ArrayList<>();
                for(int i=index;i<end;i++){
                    final String h=list.get(i);
                    if(isBlacklisted(h)) continue;
                    fs.add(pool.submit(()->new One(h,HandleChecker.strictCheck(h,false).status)));
                }
                for(Future<One> f:fs){
                    if(stop.get())break;
                    try{
                        One one=f.get();
                        if(!"yellow".equals(one.status)||isBlacklisted(one.handle))continue;

                        // Stage 2: full deep verification.
                        HandleChecker.StrictResult deep1=HandleChecker.strictCheck(one.handle,true);
                        if(!"yellow".equals(deep1.status)||isBlacklisted(one.handle))continue;

                        try{Thread.sleep(1200);}catch(Exception ignored){}
                        // Stage 3: repeat full verification from scratch.
                        HandleChecker.StrictResult deep2=HandleChecker.strictCheck(one.handle,true);
                        if(!"yellow".equals(deep2.status)||isBlacklisted(one.handle))continue;

                        appendCandidate(one.handle);
                        found++;
                        prefs.edit().putString("latest_candidate",one.handle).putInt("candidate_count",found).apply();

                        // Immediately recheck the full saved queue after adding a new candidate.
                        found=autoCleanCandidates(found);
                    }catch(Exception ignored){}
                }
                if(stop.get())break;
                index=end;sinceCleanup+=(end-index)+2;
                prefs.edit().putInt("index",index).putInt("candidate_count",found).putBoolean("running",true).apply();

                // Periodic background cleanup: only clearly OCCUPIED candidates are removed.
                if(index>0 && index%200==0){
                    found=autoCleanCandidates(found);
                    prefs.edit().putInt("candidate_count",found).apply();
                }

                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification("Проверено "+index+" / "+list.size()+" · кандидатов "+found));
                try{Thread.sleep(150);}catch(Exception ignored){}
            }
            pool.shutdownNow();
            // Final cleanup when scan reaches the end.
            if(index>=list.size()&&!stop.get()){
                found=autoCleanCandidates(found);
                prefs.edit().putInt("candidate_count",found).putBoolean("running",false).putString("error","Проверка всех 51 840 вариантов завершена. Занятые кандидаты автоматически удалены.").apply();
            }
        }finally{
            prefs.edit().putBoolean("running",false).apply();
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            synchronized(this){if(runner!=null){runner.shutdownNow();runner=null;}}
        }
    }

    /**
     * Rechecks saved candidates and removes only those for which YouTube gives
     * a positive OCCUPIED signal. UNKNOWN/limits/network errors never delete a candidate.
     */
    private int autoCleanCandidates(int currentCount){
        if(stop.get()) return currentCount;
        List<String> rows=readCandidates();
        if(rows.isEmpty()) return 0;

        ArrayList<String> keep=new ArrayList<>();
        int removed=0;
        for(String h:rows){
            if(stop.get()){ keep.add(h); continue; }
            if(isBlacklisted(h)){ removed++; continue; }

            HandleChecker.StrictResult r=HandleChecker.strictCheck(h,false);
            if("occupied".equals(r.status)){
                addBlacklist(h);
                removed++;
            }else{
                // UNKNOWN or candidate => keep it. Never auto-delete on uncertainty.
                keep.add(h);
            }
            try{Thread.sleep(120);}catch(Exception ignored){}
        }
        if(removed>0) rewriteCandidates(keep);
        prefs.edit().putInt("candidate_count",keep.size()).putInt("auto_removed",prefs.getInt("auto_removed",0)+removed).apply();
        return keep.size();
    }

    private List<String> readCandidates(){
        ArrayList<String> out=new ArrayList<>();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(openFileInput("candidates.tsv"),StandardCharsets.UTF_8))){
            String s;while((s=br.readLine())!=null){s=s.trim();if(!s.isEmpty()&&!out.contains(s))out.add(s);}
        }catch(Exception ignored){}
        return out;
    }

    private void rewriteCandidates(List<String> rows){
        try(FileOutputStream fos=openFileOutput("candidates.tsv",MODE_PRIVATE)){
            for(String h:rows)fos.write((h+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }

    private void appendCandidate(String h){
        if(isBlacklisted(h))return;
        List<String> existing=readCandidates();
        if(existing.contains(h))return;
        try(FileOutputStream fos=openFileOutput("candidates.tsv",MODE_APPEND)){fos.write((h+"\n").getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
    private static class One{final String handle,status;One(String h,String s){handle=h;status=s;}}
    private void resetData(){
        Set<String> blacklist=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));
        deleteFile("candidates.tsv");prefs.edit().clear().putInt("verifier_version",VERIFIER_VERSION).putStringSet("blacklist",blacklist).apply();
    }
    private synchronized void pauseNow(){
        stop.set(true);prefs.edit().putBoolean("running",false).apply();
        if(runner!=null)runner.shutdownNow();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

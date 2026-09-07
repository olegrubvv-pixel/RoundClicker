package com.yt3.finder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView selfStatus,stats,status,resultsTitle;
    private LinearLayout resultList;
    private Button start,pause,basic,intl,selftest;
    private String selectedMode=ScanService.MODE_BASIC;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(6),dp(4),dp(6));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackgroundColor(Color.rgb(18,23,33));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(12),dp(8),dp(12),dp(8));l.setLayoutParams(p);return l;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(8,10,15));getWindow().setNavigationBarColor(Color.rgb(8,10,15));
        prefs=getSharedPreferences("yt3",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,10,15));
        ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);
        TextView title=text("YT3 Strict Finder",26,Color.WHITE);title.setTypeface(null,1);title.setPadding(dp(16),dp(18),dp(16),dp(4));root.addView(title);
        root.addView(text("Нативный поиск трёхзнаков · работает в фоне",13,Color.rgb(145,154,174)));

        LinearLayout testCard=card();testCard.addView(text("Проверка исправности",20,Color.WHITE));
        selfStatus=text("Не проверено",14,Color.rgb(244,200,74));testCard.addView(selfStatus);
        selftest=btn("Проверить @MrBeast · @YouTube · @Google");testCard.addView(selftest);root.addView(testCard);

        LinearLayout modeCard=card();modeCard.addView(text("Режим поиска",20,Color.WHITE));
        basic=btn("Все 3 знака · латиница + цифры + разделители");intl=btn("Красивые · все языки вперемешку");
        modeCard.addView(basic);modeCard.addView(intl);root.addView(modeCard);

        LinearLayout statCard=card();stats=text("0 / 0   🟢 0   🟡 0",22,Color.WHITE);stats.setTypeface(null,1);statCard.addView(stats);
        status=text("Сначала пройди самотест.",13,Color.rgb(145,154,174));statCard.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        start=btn("▶ Начать");pause=btn("Пауза");Button reset=btn("Сброс");
        actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));actions.addView(pause,new LinearLayout.LayoutParams(0,-2,1));actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));statCard.addView(actions);root.addView(statCard);

        LinearLayout resCard=card();LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);resultsTitle=text("Кандидаты",20,Color.WHITE);top.addView(resultsTitle,new LinearLayout.LayoutParams(0,-2,1));Button copyGreen=btn("Копировать 🟢");top.addView(copyGreen);resCard.addView(top);resultList=new LinearLayout(this);resultList.setOrientation(LinearLayout.VERTICAL);resCard.addView(resultList);root.addView(resCard);

        LinearLayout info=card();info.addView(text("🟢 Зелёный — все внешние проверки дали отсутствие канала. 🟡 Жёлтый — есть свободный сигнал, но хотя бы одна проверка неопределённа. Ошибка сети никогда не становится зелёной. Окончательное присвоение всё равно подтверждает только YouTube при сохранении handle.",13,Color.rgb(215,220,230)));root.addView(info);

        selftest.setOnClickListener(v->runSelfTest());basic.setOnClickListener(v->{selectedMode=ScanService.MODE_BASIC;paintModes();});intl.setOnClickListener(v->{selectedMode=ScanService.MODE_INTL;paintModes();});
        start.setOnClickListener(v->startScan());pause.setOnClickListener(v->sendAction(ScanService.ACTION_PAUSE));reset.setOnClickListener(v->{sendAction(ScanService.ACTION_RESET);handler.postDelayed(this::refresh,300);});copyGreen.setOnClickListener(v->copyFile("green.tsv"));
        selectedMode=prefs.getString("mode",ScanService.MODE_BASIC);paintModes();runSelfTest();handler.post(refreshLoop);
    }

    private void paintModes(){basic.setEnabled(!ScanService.MODE_BASIC.equals(selectedMode));intl.setEnabled(!ScanService.MODE_INTL.equals(selectedMode));}
    private void runSelfTest(){
        selftest.setEnabled(false);selfStatus.setText("Проверяю известные занятые хэндлы…");selfStatus.setTextColor(Color.rgb(111,168,255));
        new Thread(()->{boolean ok=HandleChecker.selfTest();prefs.edit().putBoolean("selftest",ok).apply();runOnUiThread(()->{selftest.setEnabled(true);if(ok){selfStatus.setText("✓ Исправно: тестовые хэндлы определены как занятые");selfStatus.setTextColor(Color.rgb(53,217,135));}else{selfStatus.setText("✕ Самотест не пройден. Поиск заблокирован.");selfStatus.setTextColor(Color.rgb(255,59,85));}refresh();});}).start();
    }
    private void startScan(){
        if(!prefs.getBoolean("selftest",false)){Toast.makeText(this,"Сначала должен пройти самотест",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(this,ScanService.class);i.setAction(ScanService.ACTION_START);i.putExtra(ScanService.EXTRA_MODE,selectedMode);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        handler.postDelayed(this::refresh,300);
    }
    private void sendAction(String action){Intent i=new Intent(this,ScanService.class);i.setAction(action);startService(i);}

    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    private void refresh(){
        int index=prefs.getInt("index",0),total=prefs.getInt("total",0),g=prefs.getInt("green",0),y=prefs.getInt("yellow",0);boolean running=prefs.getBoolean("running",false),ok=prefs.getBoolean("selftest",false);
        stats.setText(index+" / "+total+"   🟢 "+g+"   🟡 "+y);start.setEnabled(ok&&!running);pause.setEnabled(running);status.setText(running?"Поиск работает в фоне. Можно свернуть приложение.":prefs.getString("error",index>0?"Пауза/остановлено. Нажми Начать для продолжения.":"Готово к поиску."));
        loadResults();
    }

    private void loadResults(){
        resultList.removeAllViews();
        ArrayList<String[]> rows=new ArrayList<>();
        for(String h:readLines("green.tsv",60))rows.add(new String[]{h,"green"});
        for(String h:readLines("yellow.tsv",60))rows.add(new String[]{h,"yellow"});
        Collections.reverse(rows);
        int shown=0;for(String[] r:rows){if(shown++>=100)break;TextView v=text("@"+r[0]+("green".equals(r[1])?"    🟢 строгий":"    🟡 не факт"),18,Color.WHITE);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackgroundColor("green".equals(r[1])?Color.rgb(18,57,33):Color.rgb(59,49,19));v.setOnClickListener(x->copy(r[0]));v.setOnLongClickListener(x->{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@"+Uri.encode(r[0]))));return true;});resultList.addView(v,new LinearLayout.LayoutParams(-1,-2));}
        if(rows.isEmpty())resultList.addView(text("Пока кандидатов нет.",14,Color.rgb(145,154,174)));
    }
    private List<String> readLines(String file,int max){ArrayList<String> all=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(openFileInput(file),StandardCharsets.UTF_8))){String s;while((s=br.readLine())!=null){if(!s.isBlank())all.add(s);}}catch(Exception ignored){}if(all.size()>max)return all.subList(all.size()-max,all.size());return all;}
    private void copy(String h){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("handle","@"+h));Toast.makeText(this,"Скопировано @"+h,Toast.LENGTH_SHORT).show();}
    private void copyFile(String file){List<String> l=readLines(file,100000);StringBuilder b=new StringBuilder();for(String h:l)b.append('@').append(h).append('\n');((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("YT3 green",b.toString()));Toast.makeText(this,"Скопировано: "+l.size(),Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);}
}

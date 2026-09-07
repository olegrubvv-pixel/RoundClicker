package com.yt3.finder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView stats,status,candidateText;
    private Button start,pause,copy,verify,reset;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(6),dp(4),dp(6));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackgroundColor(Color.rgb(18,23,33));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(12),dp(8),dp(12),dp(8));l.setLayoutParams(p);return l;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(8,10,15));getWindow().setNavigationBarColor(Color.rgb(8,10,15));
        prefs=getSharedPreferences("yt3",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,10,15));
        ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);

        TextView title=text("YT3 Finder",30,Color.WHITE);title.setTypeface(null,1);title.setPadding(dp(16),dp(20),dp(16),dp(4));root.addView(title);
        root.addView(text("Цель: найти хоть один доступный латинский трёхзнак",14,Color.rgb(145,154,174)));

        LinearLayout stat=card();
        stat.addView(text("Поиск по всем 51 840 вариантам",20,Color.WHITE));
        stats=text("0 / 51 840",28,Color.WHITE);stats.setTypeface(null,1);stat.addView(stats);
        status=text("Готово к поиску.",14,Color.rgb(145,154,174));stat.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        start=btn("▶ Начать");pause=btn("Пауза");reset=btn("Сброс");
        actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));actions.addView(pause,new LinearLayout.LayoutParams(0,-2,1));actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));
        stat.addView(actions);root.addView(stat);

        LinearLayout found=card();
        found.addView(text("Найденный кандидат",20,Color.WHITE));
        candidateText=text("—",36,Color.rgb(244,200,74));candidateText.setTypeface(null,1);candidateText.setGravity(17);candidateText.setPadding(dp(8),dp(18),dp(8),dp(18));found.addView(candidateText);
        copy=btn("Копировать");verify=btn("Проверить / поставить в YouTube");
        found.addView(copy);found.addView(verify);root.addView(found);

        LinearLayout info=card();
        info.addView(text("Приложение больше не ищет красивые варианты и не тратит время на другие языки. Оно перебирает весь латинский набор в стабильном случайном порядке, чтобы не начинать с очевидно занятых комбинаций. Публичные проверки используются только как жёсткий предварительный отсев. Финальную доступность может подтвердить только сам YouTube при попытке сохранить handle. Если YouTube не принимает кандидат — нажми «Искать следующий», и поиск продолжится с места остановки.",13,Color.rgb(215,220,230)));
        root.addView(info);

        start.setOnClickListener(v->startScan());
        pause.setOnClickListener(v->sendAction(ScanService.ACTION_PAUSE));
        reset.setOnClickListener(v->{sendAction(ScanService.ACTION_RESET);handler.postDelayed(this::refresh,350);});
        copy.setOnClickListener(v->copyCandidate());
        verify.setOnClickListener(v->openYouTubeHandle());

        handler.post(refreshLoop);
    }

    private void startScan(){
        Intent i=new Intent(this,ScanService.class);i.setAction(ScanService.ACTION_START);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        handler.postDelayed(this::refresh,300);
    }
    private void sendAction(String action){Intent i=new Intent(this,ScanService.class);i.setAction(action);startService(i);}
    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,800);}};

    private void refresh(){
        int index=prefs.getInt("index",0),total=prefs.getInt("total",51840);
        boolean running=prefs.getBoolean("running",false);
        String c=prefs.getString("candidate","");
        stats.setText(index+" / "+total);
        candidateText.setText(c.isEmpty()?"—":"@"+c);
        copy.setEnabled(!c.isEmpty());verify.setEnabled(!c.isEmpty());pause.setEnabled(running);
        start.setEnabled(!running);
        start.setText(c.isEmpty()?"▶ Начать / продолжить":"Искать следующий");
        if(running)status.setText("Ищу в фоне… можно свернуть приложение.");
        else if(!c.isEmpty())status.setText("Кандидат найден. Проверь его непосредственно в YouTube.");
        else status.setText(prefs.getString("error",index>0?"Пауза. Можно продолжить.":"Готово к поиску."));
    }

    private void copyCandidate(){
        String c=prefs.getString("candidate","");if(c.isEmpty())return;
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("handle","@"+c));
        Toast.makeText(this,"Скопировано @"+c,Toast.LENGTH_SHORT).show();
    }
    private void openYouTubeHandle(){
        String c=prefs.getString("candidate","");if(c.isEmpty())return;
        copyCandidate();
        try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/handle")));}
        catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@"+Uri.encode(c))));}
    }
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);}
}

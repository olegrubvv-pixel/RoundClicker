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
    private TextView stats,status,countText;
    private LinearLayout candidateList;
    private Button start,pause,reset,copyAll;
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
        root.addView(text("Ищет любой латинский трёхзнак и не останавливается на кандидатах",14,Color.rgb(145,154,174)));

        LinearLayout stat=card();
        stat.addView(text("Сверхстрогая проверка",20,Color.WHITE));
        stats=text("0 / 51 840",28,Color.WHITE);stats.setTypeface(null,1);stat.addView(stats);
        status=text("Готово к поиску.",14,Color.rgb(145,154,174));stat.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        start=btn("▶ Начать / продолжить");pause=btn("Пауза");reset=btn("Сброс");
        actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));actions.addView(pause,new LinearLayout.LayoutParams(0,-2,1));actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));
        stat.addView(actions);root.addView(stat);

        LinearLayout found=card();
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
        countText=text("Кандидаты: 0",20,Color.WHITE);top.addView(countText,new LinearLayout.LayoutParams(0,-2,1));copyAll=btn("Копировать все");top.addView(copyAll);found.addView(top);
        candidateList=new LinearLayout(this);candidateList.setOrientation(LinearLayout.VERTICAL);found.addView(candidateList);root.addView(found);

        LinearLayout info=card();
        info.addView(text("Один хэндл должен пройти 3 полных раунда по 8 страниц YouTube, затем перед сохранением кандидата весь сверхстрогий тест запускается ещё раз. Любой признак занятого канала, любая ошибка сети, лимит, 429 или неоднозначный ответ сразу исключают его. Даже после этого кандидат не считается гарантированно свободным: окончательно это подтверждает только YouTube при попытке назначить handle. Поиск не останавливается при нахождении кандидата и продолжает проверять остальные варианты в фоне.",13,Color.rgb(215,220,230)));
        root.addView(info);

        start.setOnClickListener(v->startScan());pause.setOnClickListener(v->sendAction(ScanService.ACTION_PAUSE));
        reset.setOnClickListener(v->{sendAction(ScanService.ACTION_RESET);handler.postDelayed(this::refresh,400);});
        copyAll.setOnClickListener(v->copyAll());
        handler.post(refreshLoop);
    }

    private void startScan(){Intent i=new Intent(this,ScanService.class);i.setAction(ScanService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);handler.postDelayed(this::refresh,300);}
    private void sendAction(String action){Intent i=new Intent(this,ScanService.class);i.setAction(action);startService(i);}
    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,800);}};

    private void refresh(){
        int index=prefs.getInt("index",0),total=prefs.getInt("total",51840),count=prefs.getInt("candidate_count",0);
        boolean running=prefs.getBoolean("running",false);
        stats.setText(index+" / "+total);countText.setText("Кандидаты: "+count);
        start.setEnabled(!running);pause.setEnabled(running);copyAll.setEnabled(count>0);
        status.setText(running?"Поиск продолжает работать в фоне…":prefs.getString("error",index>0?"Пауза. Можно продолжить.":"Готово к поиску."));
        loadCandidates();
    }

    private void loadCandidates(){
        candidateList.removeAllViews();List<String> rows=readLines("candidates.tsv",80);Collections.reverse(rows);
        if(rows.isEmpty()){candidateList.addView(text("Пока кандидатов нет.",14,Color.rgb(145,154,174)));return;}
        for(String h:rows){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(6),dp(5),dp(6),dp(5));
            TextView v=text("@"+h+"   🟡 сверхстрогий кандидат",17,Color.WHITE);v.setBackgroundColor(Color.rgb(59,49,19));
            Button cp=btn("Копировать");Button yt=btn("YouTube");
            cp.setOnClickListener(x->copy(h));yt.setOnClickListener(x->{copy(h);openYouTube(h);});
            row.addView(v,new LinearLayout.LayoutParams(0,-2,1));row.addView(cp);row.addView(yt);candidateList.addView(row);
        }
    }

    private List<String> readLines(String file,int max){ArrayList<String> all=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(openFileInput(file),StandardCharsets.UTF_8))){String s;while((s=br.readLine())!=null)if(!s.isBlank())all.add(s);}catch(Exception ignored){}if(all.size()>max)return new ArrayList<>(all.subList(all.size()-max,all.size()));return all;}
    private void copy(String h){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("handle","@"+h));Toast.makeText(this,"Скопировано @"+h,Toast.LENGTH_SHORT).show();}
    private void copyAll(){List<String> l=readLines("candidates.tsv",100000);StringBuilder b=new StringBuilder();for(String h:l)b.append('@').append(h).append('\n');((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("YT3 candidates",b.toString()));Toast.makeText(this,"Скопировано: "+l.size(),Toast.LENGTH_SHORT).show();}
    private void openYouTube(String h){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/handle")));}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@"+Uri.encode(h))));}}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);}
}

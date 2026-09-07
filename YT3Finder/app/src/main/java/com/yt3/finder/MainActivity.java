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
    private TextView stats,status,countText,manualResult,filteredText,phaseText;
    private LinearLayout candidateList;
    private Button start,pause,reset,copyAll,manualCheck;
    private EditText manualInput;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(6),dp(4),dp(6));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}
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
        root.addView(text("1) Быстро убрать занятые из 51 840  →  2) сверхстрого проверить остаток",14,Color.rgb(145,154,174)));

        LinearLayout manual=card();
        manual.addView(text("Проверить конкретный хэндл",20,Color.WHITE));
        manualInput=new EditText(this);manualInput.setHint("Например: abc или @abc");manualInput.setTextColor(Color.WHITE);manualInput.setHintTextColor(Color.rgb(120,130,150));manualInput.setSingleLine(true);manual.addView(manualInput);
        manualCheck=btn("Проверить сверхстрого");manual.addView(manualCheck);
        manualResult=text("Введите трёхзнак и нажмите Проверить.",14,Color.rgb(145,154,174));manual.addView(manualResult);root.addView(manual);

        LinearLayout stat=card();
        stat.addView(text("Фоновый поиск",20,Color.WHITE));
        phaseText=text("Этап 1 из 2",15,Color.rgb(111,168,255));phaseText.setTypeface(null,1);stat.addView(phaseText);
        stats=text("0 / 51 840",28,Color.WHITE);stats.setTypeface(null,1);stat.addView(stats);
        filteredText=text("Отсеяно занятых: 0",15,Color.rgb(111,168,255));stat.addView(filteredText);
        status=text("Готово к поиску.",14,Color.rgb(145,154,174));stat.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        start=btn("▶ Начать / продолжить");pause=btn("Пауза");reset=btn("Сброс");
        actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));actions.addView(pause,new LinearLayout.LayoutParams(0,-2,1));actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));stat.addView(actions);root.addView(stat);

        LinearLayout found=card();LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
        countText=text("Кандидаты: 0",20,Color.WHITE);top.addView(countText,new LinearLayout.LayoutParams(0,-2,1));copyAll=btn("Копировать все");top.addView(copyAll);found.addView(top);
        candidateList=new LinearLayout(this);candidateList.setOrientation(LinearLayout.VERTICAL);found.addView(candidateList);root.addView(found);

        LinearLayout info=card();
        info.addView(text("Теперь тяжёлая проверка вообще не запускается во время первого прохода. Этап 1 делает один быстрый запрос на каждый из 51 840 вариантов и сразу выкидывает те, где YouTube явно находит существующий канал. Всё, что не удалось уверенно назвать занятым, сохраняется в отдельный остаток. Только когда первый проход полностью закончен, начинается Этап 2: весь остаток проходит две независимые сверхстрогие многостраничные проверки. Ошибка или лимит не считаются свободой.",13,Color.rgb(215,220,230)));root.addView(info);

        manualCheck.setOnClickListener(v->runManualCheck());start.setOnClickListener(v->startScan());pause.setOnClickListener(v->sendAction(ScanService.ACTION_PAUSE));
        reset.setOnClickListener(v->{sendAction(ScanService.ACTION_RESET);handler.postDelayed(this::refresh,400);});copyAll.setOnClickListener(v->copyAll());handler.post(refreshLoop);
    }

    private String normalize(String s){s=s==null?"":s.trim().toLowerCase(Locale.ROOT);if(s.startsWith("@"))s=s.substring(1);return s;}
    private boolean validThree(String h){String edge="abcdefghijklmnopqrstuvwxyz0123456789",mid=edge+"_-.·";return h.length()==3&&edge.indexOf(h.charAt(0))>=0&&mid.indexOf(h.charAt(1))>=0&&edge.indexOf(h.charAt(2))>=0;}

    private void runManualCheck(){
        String h=normalize(manualInput.getText().toString());
        if(!validThree(h)){manualResult.setText("Неверный формат. Нужно ровно 3 символа.");manualResult.setTextColor(Color.rgb(255,100,110));return;}
        manualCheck.setEnabled(false);manualResult.setText("Проверяю @"+h+"…");manualResult.setTextColor(Color.rgb(111,168,255));
        new Thread(()->{
            String result;
            if(HandleChecker.definitelyOccupiedFast(h))result="occupied";
            else{result=HandleChecker.strictCheck(h,true).status;if("yellow".equals(result)){try{Thread.sleep(500);}catch(Exception ignored){}result=HandleChecker.strictCheck(h,true).status;}}
            final String r=result;
            runOnUiThread(()->{manualCheck.setEnabled(true);
                if("occupied".equals(r)){manualResult.setText("🔴 @"+h+" — ЗАНЯТ");manualResult.setTextColor(Color.rgb(255,80,95));}
                else if("yellow".equals(r)){manualResult.setText("🟡 @"+h+" — сверхстрогий кандидат. Финально проверяет YouTube при назначении.");manualResult.setTextColor(Color.rgb(244,200,74));}
                else{manualResult.setText("⚪ @"+h+" — не удалось подтвердить: ошибка/лимит/неоднозначность.");manualResult.setTextColor(Color.rgb(180,190,205));}
            });
        }).start();
    }

    private void startScan(){Intent i=new Intent(this,ScanService.class);i.setAction(ScanService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);handler.postDelayed(this::refresh,300);}
    private void sendAction(String action){Intent i=new Intent(this,ScanService.class);i.setAction(action);startService(i);}
    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,700);}};

    private void refresh(){
        int phase=prefs.getInt("phase",1),count=prefs.getInt("candidate_count",0),removed=prefs.getInt("prefilter_removed",0),survivors=prefs.getInt("survivor_count",0);
        boolean running=prefs.getBoolean("running",false);
        if(phase<=1){int i=prefs.getInt("phase1_index",0),t=prefs.getInt("phase1_total",51840);phaseText.setText("Этап 1 из 2 · быстрый отсев");stats.setText(i+" / "+t);filteredText.setText("Отсеяно занятых: "+removed+"   ·   осталось: "+survivors);}
        else if(phase==2){int i=prefs.getInt("phase2_index",0),t=prefs.getInt("phase2_total",survivors);phaseText.setText("Этап 2 из 2 · сверхстрогая проверка остатка");stats.setText(i+" / "+t);filteredText.setText("После первого этапа осталось: "+t+"   ·   кандидатов: "+count);}
        else{phaseText.setText("✓ Оба этапа завершены");stats.setText("Готово");filteredText.setText("Отсеяно занятых на этапе 1: "+removed);}
        countText.setText("Кандидаты: "+count);start.setEnabled(!running&&phase<3);pause.setEnabled(running);copyAll.setEnabled(count>0);
        if(running)status.setText(phase<=1?"Быстро просматриваю все 51 840. Глубокая проверка пока НЕ идёт.":"Проверяю только оставшиеся хэндлы сверхстрого…");
        else status.setText(prefs.getString("error",phase<3?"Пауза/готово к продолжению.":"Готово."));
        loadCandidates();
    }

    private void loadCandidates(){
        candidateList.removeAllViews();List<String> rows=readLines("candidates.tsv",80);Collections.reverse(rows);
        if(rows.isEmpty()){candidateList.addView(text("Кандидаты появятся только на втором этапе.",14,Color.rgb(145,154,174)));return;}
        for(String h:rows){LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(6),dp(6),dp(6),dp(6));
            TextView v=text("@"+h+"   🟡 сверхстрогий кандидат",17,Color.WHITE);v.setBackgroundColor(Color.rgb(59,49,19));outer.addView(v,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button cp=btn("Копировать"),yt=btn("YouTube"),del=btn("Занят ✕");
            cp.setOnClickListener(x->copy(h));yt.setOnClickListener(x->{copy(h);openYouTube(h);});del.setOnClickListener(x->deleteCandidate(h));
            row.addView(cp,new LinearLayout.LayoutParams(0,-2,1));row.addView(yt,new LinearLayout.LayoutParams(0,-2,1));row.addView(del,new LinearLayout.LayoutParams(0,-2,1));outer.addView(row);candidateList.addView(outer);}
    }

    private void deleteCandidate(String h){
        Set<String> blacklist=new HashSet<>(prefs.getStringSet("blacklist",Collections.emptySet()));blacklist.add(h.toLowerCase(Locale.ROOT));
        List<String> all=readLines("candidates.tsv",100000);ArrayList<String> keep=new ArrayList<>();for(String x:all)if(!x.equalsIgnoreCase(h))keep.add(x);
        try(FileOutputStream fos=openFileOutput("candidates.tsv",MODE_PRIVATE)){for(String x:keep)fos.write((x+"\n").getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        prefs.edit().putStringSet("blacklist",blacklist).putInt("candidate_count",keep.size()).apply();Toast.makeText(this,"@"+h+" удалён",Toast.LENGTH_SHORT).show();refresh();
    }

    private List<String> readLines(String file,int max){ArrayList<String> all=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(openFileInput(file),StandardCharsets.UTF_8))){String s;while((s=br.readLine())!=null)if(!s.isBlank())all.add(s);}catch(Exception ignored){}if(all.size()>max)return new ArrayList<>(all.subList(all.size()-max,all.size()));return all;}
    private void copy(String h){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("handle","@"+h));Toast.makeText(this,"Скопировано @"+h,Toast.LENGTH_SHORT).show();}
    private void copyAll(){List<String> l=readLines("candidates.tsv",100000);StringBuilder b=new StringBuilder();for(String h:l)b.append('@').append(h).append('\n');((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("YT3 candidates",b.toString()));Toast.makeText(this,"Скопировано: "+l.size(),Toast.LENGTH_SHORT).show();}
    private void openYouTube(String h){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/handle")));}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/@"+Uri.encode(h))));}}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);}
}

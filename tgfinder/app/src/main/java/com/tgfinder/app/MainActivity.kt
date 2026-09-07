package com.tgfinder.app

import android.Manifest
import android.content.*
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity:AppCompatActivity(){
    private lateinit var root:LinearLayout;private lateinit var status:TextView;private lateinit var foundBox:LinearLayout
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onCreate(b:Bundle?){super.onCreate(b);if(Build.VERSION.SDK_INT>=33)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),1);buildUi();TelegramCore.listen{runOnUiThread{refreshState()}}; val id=Prefs.apiId(this); if(id>0)TelegramCore.start(this,id,Prefs.apiHash(this));refreshFound()}
    private fun buildUi(){
        val scroll=ScrollView(this);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(18),dp(14),dp(40));setBackgroundColor(0xffeef7fc.toInt())};scroll.addView(root);setContentView(scroll)
        title("Telegram Username Finder",24); text("Зелёный результат = Telegram подтвердил доступность через TDLib.")
        val api=edit("API ID",InputType.TYPE_CLASS_NUMBER);api.setText(Prefs.apiId(this).takeIf{it>0}?.toString()?:"")
        val hash=edit("API HASH");hash.setText(Prefs.apiHash(this));val phone=edit("Телефон +...",InputType.TYPE_CLASS_PHONE);phone.setText(Prefs.phone(this))
        button("Подключить Telegram"){val id=api.text.toString().toIntOrNull()?:0;val h=hash.text.toString().trim();val p=phone.text.toString().trim();Prefs.saveCredentials(this,id,h,p);TelegramCore.start(this,id,h);Toast.makeText(this,"Инициализация…",Toast.LENGTH_SHORT).show()}
        status=TextView(this).apply{setPadding(0,dp(8),0,dp(8));textSize=15f};root.addView(status)
        button("Отправить номер"){TelegramCore.submitPhone(phone.text.toString().trim()){e->runOnUiThread{toast(e?:"Код отправлен")}}}
        val code=edit("Код из Telegram",InputType.TYPE_CLASS_NUMBER);button("Подтвердить код"){TelegramCore.submitCode(code.text.toString().trim()){e->runOnUiThread{toast(e?:"Код принят")}}}
        val pass=edit("Пароль 2FA",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);button("Подтвердить 2FA"){TelegramCore.submitPassword(pass.text.toString()){e->runOnUiThread{toast(e?:"Готово")}}}
        title("Ручная точная проверка",19);val manual=edit("apple / aaaaaa");button("Проверить"){thread{val u=manual.text.toString().trim().removePrefix("@").lowercase();val r=TelegramCore.checkUsername(u);runOnUiThread{toast("@$u — ${r.label}");if(r.status=="free"){val l=Prefs.loadFound(this);if(l.none{it.username==u}){l.add(0,Prefs.Found(u,System.currentTimeMillis()));Prefs.saveFound(this,l);refreshFound()}}}}}
        title("Автопоиск",19)
        val modes=arrayOf("Английские слова","Красивые ID","Всё вместе");val spin=Spinner(this);spin.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,modes);spin.setSelection(when(Prefs.mode(this)){"pretty"->1;"mixed"->2;else->0});root.addView(spin)
        val delays=arrayOf("Быстро · 0.6с","Нормально · 0.9с","Осторожно · 1.3с");val ds=Spinner(this);ds.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,delays);root.addView(ds)
        button("▶ Начать фоновый поиск"){Prefs.setMode(this,when(spin.selectedItemPosition){1->"pretty";2->"mixed";else->"words"});Prefs.setDelay(this,longArrayOf(600,900,1300)[ds.selectedItemPosition]);val i=Intent(this,ScanService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);toast("Поиск работает в фоне")}
        button("■ Остановить"){startService(Intent(this,ScanService::class.java).setAction("STOP"))}
        title("Подтверждённо свободные",19);foundBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(foundBox)
    }
    private fun refreshState(){status.text=TelegramCore.statusText}
    private fun refreshFound(){if(!::foundBox.isInitialized)return;foundBox.removeAllViews();val list=Prefs.loadFound(this);if(list.isEmpty()){textInto(foundBox,"Пока ничего не найдено.");return};val fmt=SimpleDateFormat("dd.MM HH:mm",Locale.getDefault());list.take(200).forEach{f->
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(9),dp(6),dp(9));setBackgroundColor(0xffe3f8ef.toInt())}
        val tv=TextView(this).apply{text="@${f.username}\n${fmt.format(Date(f.checkedAt))}";textSize=16f;setTextColor(0xff08764b.toInt())};row.addView(tv,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val copy=Button(this).apply{text="Копировать";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("username","@${f.username}"));toast("Скопировано")}};row.addView(copy);foundBox.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)})
    }}
    private fun title(s:String,size:Int){root.addView(TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(0xff10233f.toInt());setPadding(0,dp(12),0,dp(7));setTypeface(typeface,1)})}
    private fun text(s:String){textInto(root,s)}
    private fun textInto(v:LinearLayout,s:String){v.addView(TextView(this).apply{text=s;textSize=14f;setTextColor(0xff70819b.toInt());setPadding(0,0,0,dp(8))})}
    private fun edit(h:String,type:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{hint=h;inputType=type;setPadding(dp(12),dp(10),dp(12),dp(10));root.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})}
    private fun button(t:String,on:()->Unit){root.addView(Button(this).apply{text=t;setOnClickListener{on()}},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)})}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onResume(){super.onResume();refreshFound();refreshState()}
}

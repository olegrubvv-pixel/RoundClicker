package com.tgfinder.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScanService: Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var running=false
    override fun onCreate(){super.onCreate();createChannel()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action=="STOP"){running=false;stopSelf();return START_NOT_STICKY}
        if(running)return START_STICKY
        running=true;startForeground(7,notification("Поиск запущен"));scope.launch{loop()};return START_STICKY
    }
    private suspend fun loop(){
        val seen=HashSet<String>();var checked=0;var free=0
        while(running){
            if(TelegramCore.state!=TelegramCore.State.READY){ delay(1200);continue }
            val u=UsernameGenerator.next(Prefs.mode(this)); if(!seen.add(u))continue
            val r=TelegramCore.checkUsername(u);checked++
            if(r.status=="free"){
                val list=Prefs.loadFound(this); if(list.none{it.username==u}){list.add(0,Prefs.Found(u,System.currentTimeMillis()));Prefs.saveFound(this,list);free++}
            }
            if(r.status=="error" && r.label.contains("FLOOD",true)){
                val sec=Regex("(\\d+)").find(r.label)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 60L
                update("Лимит Telegram · пауза ${sec}с");delay(sec*1000)
            } else {
                update("Проверено $checked · найдено $free · @$u");delay(Prefs.delay(this))
            }
        }
    }
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val c=NotificationChannel("scan","Фоновый поиск",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(c)}}
    private fun notification(text:String):Notification{
        val stop=PendingIntent.getService(this,1,Intent(this,ScanService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"scan").setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("TG Username Finder").setContentText(text).setOngoing(true).addAction(0,"Остановить",stop).build()
    }
    private fun update(t:String){getSystemService(NotificationManager::class.java).notify(7,notification(t))}
    override fun onDestroy(){running=false;scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}

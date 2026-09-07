package com.tgfinder.app

import android.content.Context
import android.os.Build
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object TelegramCore {
    enum class State { OFF, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, READY, ERROR }
    @Volatile var state=State.OFF
    @Volatile var statusText="Не подключено"
    private var client: Client?=null
    private var selfChatId:Long=0
    private var apiId=0; private var apiHash=""; private lateinit var ctx:Context
    private val listeners=mutableListOf<()->Unit>()
    fun listen(l:()->Unit){ synchronized(listeners){listeners+=l} }
    private fun emit(){ synchronized(listeners){listeners.toList()}.forEach{runCatching{it()}} }

    fun start(c:Context,id:Int,hash:String){
        if(client!=null && apiId==id && apiHash==hash) return
        ctx=c.applicationContext; apiId=id; apiHash=hash
        try{ System.loadLibrary("tdjni") }catch(_:Throwable){}
        client=Client.create({ update ->
            if(update is TdApi.UpdateAuthorizationState) handleAuth(update.authorizationState)
        }, null, null)
        statusText="Инициализация Telegram…"; emit()
    }
    private fun handleAuth(s:TdApi.AuthorizationState){
        when(s){
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val base=File(ctx.filesDir,"tdlib").apply{mkdirs()}
                val p=TdApi.SetTdlibParameters(false, File(base,"db").absolutePath, File(base,"files").absolutePath, byteArrayOf(), true, true, false, false, apiId, apiHash, "ru", Build.MODEL ?: "Android", Build.VERSION.RELEASE ?: "Android", "1.0")
                sendObject(p)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber->{state=State.WAIT_PHONE;statusText="Введите номер телефона";emit()}
            is TdApi.AuthorizationStateWaitCode->{state=State.WAIT_CODE;statusText="Введите код из Telegram";emit()}
            is TdApi.AuthorizationStateWaitPassword->{state=State.WAIT_PASSWORD;statusText="Введите пароль 2FA";emit()}
            is TdApi.AuthorizationStateReady->{state=State.READY;statusText="Telegram подключён ✓";resolveSelfChat();emit()}
            is TdApi.AuthorizationStateClosed->{state=State.OFF;statusText="Сессия закрыта";emit()}
            else -> {}
        }
    }
    fun submitPhone(phone:String, cb:(String?)->Unit)=sendWithError(TdApi.SetAuthenticationPhoneNumber(phone,null),cb)
    fun submitCode(code:String, cb:(String?)->Unit)=sendWithError(TdApi.CheckAuthenticationCode(code),cb)
    fun submitPassword(pass:String, cb:(String?)->Unit)=sendWithError(TdApi.CheckAuthenticationPassword(pass),cb)
    private fun resolveSelfChat(){
        sendObject(TdApi.GetMe()){obj->
            if(obj is TdApi.User){ sendObject(TdApi.CreatePrivateChat(obj.id,false)){chat-> if(chat is TdApi.Chat) selfChatId=chat.id } }
        }
    }
    data class Check(val status:String,val label:String)
    fun checkUsername(username:String, timeoutMs:Long=15000):Check{
        if(state!=State.READY) return Check("error","Telegram не подключён")
        if(!UsernameGenerator.valid(username)) return Check("invalid","Невалидный")
        if(selfChatId==0L) resolveSelfChat()
        val latch=CountDownLatch(1); var out=Check("error","Нет ответа Telegram")
        val start=System.currentTimeMillis(); while(selfChatId==0L && System.currentTimeMillis()-start<3000) Thread.sleep(50)
        client?.send(TdApi.CheckChatUsername(selfChatId,username)){r->
            out=when(r){
                is TdApi.CheckChatUsernameResultOk->Check("free","Свободен")
                is TdApi.CheckChatUsernameResultUsernameOccupied->Check("taken","Занят")
                is TdApi.CheckChatUsernameResultUsernameInvalid->Check("invalid","Невалидный")
                is TdApi.CheckChatUsernameResultUsernamePurchasable->Check("fragment","Только Fragment")
                is TdApi.Error->Check("error",r.message ?: "Ошибка Telegram")
                else->Check("error",r.javaClass.simpleName)
            };latch.countDown()
        }
        latch.await(timeoutMs,TimeUnit.MILLISECONDS);return out
    }
    private fun <R:TdApi.Object> sendObject(f:TdApi.Function<R>, cb:(TdApi.Object)->Unit={}){ client?.send(f){r->cb(r)} }
    private fun <R:TdApi.Object> sendWithError(f:TdApi.Function<R>, cb:(String?)->Unit){ client?.send(f){r->cb(if(r is TdApi.Error) r.message else null)} }
}

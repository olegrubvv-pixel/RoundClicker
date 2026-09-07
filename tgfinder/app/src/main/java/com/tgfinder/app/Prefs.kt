package com.tgfinder.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val P = "tgfinder"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun saveCredentials(c: Context, apiId: Int, apiHash: String, phone: String) {
        p(c).edit().putInt("apiId", apiId).putString("apiHash", apiHash).putString("phone", phone).apply()
    }
    fun apiId(c: Context)=p(c).getInt("apiId",0)
    fun apiHash(c: Context)=p(c).getString("apiHash","") ?: ""
    fun phone(c: Context)=p(c).getString("phone","") ?: ""
    fun setMode(c: Context, mode:String)=p(c).edit().putString("mode",mode).apply()
    fun mode(c: Context)=p(c).getString("mode","words") ?: "words"
    fun setDelay(c: Context, ms:Long)=p(c).edit().putLong("delay",ms).apply()
    fun delay(c: Context)=p(c).getLong("delay",900L)

    data class Found(val username:String,val checkedAt:Long,val favorite:Boolean=false)
    fun loadFound(c: Context): MutableList<Found> {
        val raw=p(c).getString("found","[]") ?: "[]"; val a=JSONArray(raw); val out=mutableListOf<Found>()
        for(i in 0 until a.length()) { val o=a.getJSONObject(i); out += Found(o.getString("u"),o.getLong("t"),o.optBoolean("f",false)) }
        return out
    }
    fun saveFound(c: Context, list:List<Found>){ val a=JSONArray(); list.forEach{ a.put(JSONObject().put("u",it.username).put("t",it.checkedAt).put("f",it.favorite)) }; p(c).edit().putString("found",a.toString()).apply() }
}

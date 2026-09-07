package com.tgfinder.app

import kotlin.random.Random

object UsernameGenerator {
    private val words = """apple story rigid tiger river cloud stone flame dream light brave smart lucky magic royal fresh sweet quiet quick bright black white green blue silver golden happy rapid solid crisp clean sharp grand young prime urban rural vivid ocean beach storm frost snow rain wind earth moon solar space star comet nova orbit angel ghost wolf fox bear lion eagle raven shark whale horse panda peach lemon mango berry grape olive bread candy sugar honey coffee cocoa music audio radio video pixel photo movie drama laugh smile heart soul mind idea truth power glory honor peace world local global alpha delta omega retro modern future cyber turbo hyper super mini micro macro neon velvet crystal shadow secret simple gentle honest loyal funny sunny windy rainy snowy proud agile tiny giant clever fancy basic casual wild calm cool warm cold silent happy lucky noble lucid amber coral ivory pearl mint cedar maple bloom field ridge vale lake shore dawn dusk night north south east west open close begin final early later first last real pure true bold soft hard fast slow high low deep wide long short round square point line wave echo tone beat rhythm lyric melody chord piano guitar drum bass dance party game play quest level score hero rogue mage knight sword shield arrow spear crown king queen prince fairy witch spell potion dragon phoenix koala otter bunny puppy kitty foxie wolfy""".split(" ")
    private val letters="abcdefghijklmnopqrstuvwxyz"
    private val digits="0123456789"
    private val suffixes=listOf("x","hq","io","pro","go","lab","hub","one","now","ly","box","net","zen","fox","app")
    fun next(mode:String):String = when(mode){"pretty"->pretty();"mixed"->if(Random.nextBoolean()) word() else pretty();else->word()}
    private fun word():String{
        repeat(50){
            val w=words.random(); val variants=listOf(w, w+suffixes.random(), w+letters.random(), w+digits.random())
            val v=variants.random().lowercase(); if(valid(v)) return v
        }
        return "word"+Random.nextInt(1000,9999)
    }
    private fun pretty():String{
        repeat(100){
            val a=letters.random(); val b=letters.random(); val d=digits.random()
            val v=listOf("$a$a$a$a$a", "$a$b$b$b$a", "$a$b$a$b$a", "$a$a$b$a$a", "$a$d$a$d$a", "$a$b$d$b$a", "$a$d$d$d$a", "$a$a$a$d$d").random()
            if(valid(v)) return v
        }
        return "a"+Random.nextInt(10000,99999)
    }
    fun valid(u:String)=u.matches(Regex("^[a-z][a-z0-9_]{4,31}$"))
}

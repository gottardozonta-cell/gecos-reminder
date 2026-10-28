package com.example.gecosreminder

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Tipo { INGRESSO, USCITA }

data class Sede(
    val id: String,
    var nome: String,
    var indirizzo: String,
    var lat: Double,
    var lng: Double,
    var raggio: Int = 500
)

data class Regola(
    val id: String,
    var sedeId: String,
    var tipo: Tipo,
    var ora: Int,
    var minuto: Int,
    var giorno: Int,
    var descrizione: String,
    var attiva: Boolean = true
)

data class Impostazioni(
    var master: Boolean = true,
    var suonoUri: String? = null,
    var appPackage: String? = null,
    var ripetiOgniMin: Int = 5,
    var ripetiMaxMin: Int = 45
)

object Store {
    private val gson = Gson()
    private lateinit var p: SharedPreferences

    fun init(c: Context) {
        if (this::p.isInitialized) return
        p = c.getSharedPreferences("gecos_reminder", Context.MODE_PRIVATE)
        if (!p.getBoolean("seeded", false)) {
            p.edit()
                .putString("sedi", gson.toJson(sediDefault()))
                .putString("regole", gson.toJson(regoleDefault()))
                .putBoolean("seeded", true)
                .apply()
        }
    }

    private inline fun <reified T> leggi(key: String, def: T): T =
        p.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: def

    fun sedi(c: Context): MutableList<Sede> { init(c); return leggi("sedi", sediDefault()) }
    fun regole(c: Context): MutableList<Regola> { init(c); return leggi("regole", regoleDefault()) }
    fun impostazioni(c: Context): Impostazioni { init(c); return leggi("imp", Impostazioni()) }

    fun salvaSedi(c: Context, l: List<Sede>) { init(c); p.edit().putString("sedi", gson.toJson(l)).apply() }
    fun salvaRegole(c: Context, l: List<Regola>) { init(c); p.edit().putString("regole", gson.toJson(l)).apply() }
    fun salvaImpostazioni(c: Context, i: Impostazioni) { init(c); p.edit().putString("imp", gson.toJson(i)).apply() }

    fun fatto(c: Context, regolaId: String, dataKey: String): Boolean {
        init(c); return p.getBoolean("$regolaId|$dataKey", false)
    }

    fun setFatto(c: Context, regolaId: String, dataKey: String) {
        init(c); p.edit().putBoolean("$regolaId|$dataKey", true).apply()
    }

    data class Occ(val regolaId: String, val dataKey: String, val start: Long)
    fun setOcc(c: Context, o: Occ?) { init(c); p.edit().putString("occ", o?.let { gson.toJson(it) }).apply() }
    fun occ(c: Context): Occ? { init(c); return p.getString("occ", null)?.let { gson.fromJson(it, Occ::class.java) } }

    private fun sediDefault() = mutableListOf(
        Sede("cap", "CAPACITAZIONE", "Via Borgatti (fondo), Rimini", 0.0, 0.0),
        Sede("sp", "SE POTESSI", "Viale Valturio 36, Rimini", 0.0, 0.0)
    )

    private fun regoleDefault(): MutableList<Regola> {
        val cap = "A.DISABILITA' E DIP.PATOLOGICHE - CAPACITAZIONE - EDUCATORE"
        val sp = "A.C.TA' EDUCATIVE RESIDENZIALI - SE POTESSI - EDUCATORE"
        val r = mutableListOf<Regola>()
        fun add(g: Int, h: Int, m: Int, t: Tipo, s: String, d: String) {
            r.add(Regola(UUID.randomUUID().toString(), s, t, h, m, g, d))
        }
        val L = Calendar.MONDAY; val M = Calendar.TUESDAY; val G = Calendar.WEDNESDAY
        val V = Calendar.THURSDAY; val F = Calendar.FRIDAY
        // Capacitazione: lun-ven 08:45 / 13:00
        for (g in listOf(L, M, G, V, F)) {
            add(g, 8, 45, Tipo.INGRESSO, "cap", cap)
            add(g, 13, 0, Tipo.USCITA, "cap", cap)
        }
        // Capacitazione: lunedì 13:30 / 15:30
        add(L, 13, 30, Tipo.INGRESSO, "cap", cap)
        add(L, 15, 30, Tipo.USCITA, "cap", cap)
        // Se Potessi: lunedì 16:00 / 19:00
        add(L, 16, 0, Tipo.INGRESSO, "sp", sp)
        add(L, 19, 0, Tipo.USCITA, "sp", sp)
        // Se Potessi: martedì 13:30 / 17:30
        add(M, 13, 30, Tipo.INGRESSO, "sp", sp)
        add(M, 17, 30, Tipo.USCITA, "sp", sp)
        // Se Potessi: mer-ven 13:30 / 16:30
        for (g in listOf(G, V, F)) {
            add(g, 13, 30, Tipo.INGRESSO, "sp", sp)
            add(g, 16, 30, Tipo.USCITA, "sp", sp)
        }
        return r
    }
}

fun nomeGiorno(g: Int): String = when (g) {
    Calendar.MONDAY -> "Lunedì"
    Calendar.TUESDAY -> "Martedì"
    Calendar.WEDNESDAY -> "Mercoledì"
    Calendar.THURSDAY -> "Giovedì"
    Calendar.FRIDAY -> "Venerdì"
    Calendar.SATURDAY -> "Sabato"
    else -> "Domenica"
}

fun hhmm(h: Int, m: Int) = String.format(Locale.ITALY, "%02d:%02d", h, m)

fun dataKey(cal: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(cal.time)
fun dataKey(d: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(d)

package com.example.gecosreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    const val K_REGOLA = "regolaId"
    const val K_DATA = "dataKey"
    const val K_START = "start"
    const val K_COUNT = "count"

    fun prossimo(c: Context): Triple<Regola, Calendar, String>? {
        val now = Calendar.getInstance()
        var bestR: Regola? = null
        var bestC: Calendar? = null
        for (r in Store.regole(c)) {
            if (!r.attiva) continue
            val t = now.clone() as Calendar
            t.set(Calendar.HOUR_OF_DAY, r.ora)
            t.set(Calendar.MINUTE, r.minuto)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            t.set(Calendar.DAY_OF_WEEK, r.giorno)
            if (!t.after(now)) t.add(Calendar.WEEK_OF_YEAR, 1)
            if (bestC == null || t.before(bestC)) { bestR = r; bestC = t }
        }
        bestC ?: return null
        return Triple(bestR!!, bestC, dataKey(bestC))
    }

    private fun pi(c: Context, i: Intent): PendingIntent =
        PendingIntent.getBroadcast(c, 2001, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun programma(c: Context, at: Long, regolaId: String, dataKey: String, start: Long, count: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra(K_REGOLA, regolaId)
            .putExtra(K_DATA, dataKey)
            .putExtra(K_START, start)
            .putExtra(K_COUNT, count)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, i))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, i))
        }
    }

    fun programmaProssimo(c: Context) {
        val p = prossimo(c) ?: return
        programma(c, p.second.timeInMillis, p.first.id, p.third, p.second.timeInMillis, 0)
    }

    fun riprova(c: Context, regolaId: String, dataKey: String, start: Long, count: Int) {
        val st = Store.impostazioni(c)
        val elapsedMin = (System.currentTimeMillis() - start) / 60_000
        if (elapsedMin >= st.ripetiMaxMin) {
            programmaProssimo(c)
        } else {
            val at = System.currentTimeMillis() + st.ripetiOgniMin * 60_000L
            programma(c, at, regolaId, dataKey, start, count + 1)
        }
    }
}

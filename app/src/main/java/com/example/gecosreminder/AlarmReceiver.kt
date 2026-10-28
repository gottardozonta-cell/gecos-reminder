package com.example.gecosreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { gestisci(context, intent) } finally { result.finish() }
        }
    }

    private suspend fun gestisci(c: Context, i: Intent) {
        val imp = Store.impostazioni(c)
        val regolaId = i.getStringExtra(Scheduler.K_REGOLA)
            ?: return Scheduler.programmaProssimo(c)
        val dataKey = i.getStringExtra(Scheduler.K_DATA) ?: ""
        val start = i.getLongExtra(Scheduler.K_START, System.currentTimeMillis())
        val count = i.getIntExtra(Scheduler.K_COUNT, 0)

        val r = Store.regole(c).find { it.id == regolaId }
        if (r == null || !imp.master) return Scheduler.programmaProssimo(c)
        if (Store.fatto(c, r.id, dataKey)) return Scheduler.programmaProssimo(c)

        Store.setOcc(c, Store.Occ(r.id, dataKey, start))

        val sede = Store.sedi(c).find { it.id == r.sedeId }
        var suona = false
        var avviso = false
        if (sede == null || sede.lat == 0.0) {
            suona = true; avviso = true
        } else {
            val loc = Geo.posizione(c)
            if (loc != null && Geo.entroRaggio(loc, sede)) suona = true
        }

        if (suona) {
            Notifiche.mostra(c, r, sede, dataKey, avviso)
            Scheduler.riprova(c, r.id, dataKey, start, count)
        } else {
            Scheduler.programmaProssimo(c)
        }
    }
}

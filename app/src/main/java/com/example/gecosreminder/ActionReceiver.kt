package com.example.gecosreminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val A_DONE = "com.example.gecosreminder.DONE"
        const val A_SNOOZE = "com.example.gecosreminder.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            A_DONE -> {
                val rid = intent.getStringExtra(Scheduler.K_REGOLA)
                val dk = intent.getStringExtra(Scheduler.K_DATA)
                if (rid != null && dk != null) Store.setFatto(context, rid, dk)
                nm.cancel(Notifiche.NOTIF_ID)
                Store.setOcc(context, null)
                Scheduler.programmaProssimo(context)
            }
            A_SNOOZE -> {
                val rid = intent.getStringExtra(Scheduler.K_REGOLA)
                val dk = intent.getStringExtra(Scheduler.K_DATA)
                nm.cancel(Notifiche.NOTIF_ID)
                if (rid != null && dk != null) {
                    val start = Store.occ(context)?.start ?: System.currentTimeMillis()
                    Scheduler.riprova(context, rid, dk, start, 0)
                } else Scheduler.programmaProssimo(context)
            }
        }
    }
}

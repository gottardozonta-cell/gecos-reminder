package com.example.gecosreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat

object Notifiche {
    const val NOTIF_ID = 3001
    private const val BASE = "gecos_alerts"

    private fun canale(c: Context, suonoUri: String?): String {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = BASE + "_" + (suonoUri ?: "default").hashCode()
        for (ch in nm.notificationChannels.toList()) {
            if (ch.id.startsWith(BASE) && ch.id != id) nm.deleteNotificationChannel(ch.id)
        }
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "Promemoria timbratura", NotificationManager.IMPORTANCE_HIGH)
            val suono = suonoUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ch.setSound(suono, Notification.AUDIO_ATTRIBUTES_DEFAULT)
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 400, 200, 400)
            nm.createNotificationChannel(ch)
        }
        return id
    }

    private fun apriGecos(c: Context): PendingIntent? {
        val pkg = Store.impostazioni(c).appPackage
        val intent = if (pkg != null) c.packageManager.getLaunchIntentForPackage(pkg)
        else c.packageManager.getLaunchIntentForPackage(c.packageName)
        return intent?.let {
            PendingIntent.getActivity(c, 1001, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    fun mostra(c: Context, r: Regola, sede: Sede?, dataKey: String, avvisoPosizione: Boolean) {
        if (c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val imp = Store.impostazioni(c)
        val tipo = if (r.tipo == Tipo.INGRESSO) "INGRESSO" else "USCITA"
        val testo = buildString {
            append("${hhmm(r.ora, r.minuto)} — $tipo\n")
            append(sede?.nome ?: "")
            if (avvisoPosizione) append("\n⚠ Posizione della sede non impostata: apri l'app")
        }
        val b = NotificationCompat.Builder(c, canale(c, imp.suonoUri))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timbra: $tipo ${sede?.nome ?: ""}")
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo + "\n\n" + r.descrizione))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
        apriGecos(c)?.let { b.setContentIntent(it) }

        val pDone = PendingIntent.getBroadcast(c, 1002,
            Intent(c, ActionReceiver::class.java).setAction(ActionReceiver.A_DONE)
                .putExtra(Scheduler.K_REGOLA, r.id).putExtra(Scheduler.K_DATA, dataKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        b.addAction(0, "Timbro fatto", pDone)

        val pSn = PendingIntent.getBroadcast(c, 1003,
            Intent(c, ActionReceiver::class.java).setAction(ActionReceiver.A_SNOOZE)
                .putExtra(Scheduler.K_REGOLA, r.id).putExtra(Scheduler.K_DATA, dataKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        b.addAction(0, "Rimanda", pSn)

        c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build())
    }
}

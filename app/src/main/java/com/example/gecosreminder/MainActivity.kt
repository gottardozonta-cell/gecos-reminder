package com.example.gecosreminder

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { MaterialTheme { Schermata() } }
    }
}

@Composable
fun Schermata() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current

    var versione by remember { mutableStateOf(0) }
    var notifOk by remember { mutableStateOf(false) }
    var locOk by remember { mutableStateOf(false) }
    var bgOk by remember { mutableStateOf(false) }
    var alarmOk by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    fun aggiorna() {
        notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        locOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        bgOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        alarmOk = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
    }

    fun riapplica() {
        aggiorna()
        if (notifOk && locOk && alarmOk) {
            val occ = Store.occ(ctx)
            if (occ != null && !Store.fatto(ctx, occ.regolaId, occ.dataKey))
                Scheduler.riprova(ctx, occ.regolaId, occ.dataKey, occ.start, 0)
            else Scheduler.programmaProssimo(ctx)
        }
        versione++
    }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) aggiorna() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { aggiorna() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { riapplica() }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { riapplica() }
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val imp = Store.impostazioni(ctx)
            imp.suonoUri = uri?.toString()
            Store.salvaImpostazioni(ctx, imp)
            versione++
        }
    }

    val imp = remember(versione) { Store.impostazioni(ctx) }
    val sedi = remember(versione) { Store.sedi(ctx) }
    val regole = remember(versione) { Store.regole(ctx) }
    val prossimo = remember(versione) { Scheduler.prossimo(ctx) }
    fun nomeSede(id: String) = sedi.find { it.id == id }?.nome ?: id

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Gecos Reminder", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Promemoria attivi", Modifier.weight(1f))
                Switch(checked = imp.master, onCheckedChange = {
                    imp.master = it; Store.salvaImpostazioni(ctx, imp); riapplica()
                })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Prossimo promemoria", style = MaterialTheme.typography.titleMedium)
                val p = prossimo
                if (p == null) Text("Nessun turno configurato")
                else {
                    val (r, cal, _) = p
                    Text("${nomeGiorno(cal.get(Calendar.DAY_OF_WEEK))} ${hhmm(r.ora, r.minuto)} — ${if (r.tipo == Tipo.INGRESSO) "INGRESSO" else "USCITA"}")
                    Text(nomeSede(r.sedeId), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    val r0 = regole.firstOrNull()
                    if (r0 != null) Notifiche.mostra(ctx, r0, sedi.find { it.id == r0.sedeId }, dataKey(Calendar.getInstance()), false)
                }) { Text("Prova notifica e suono") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Permessi", style = MaterialTheme.typography.titleMedium)
                PermRow("Notifiche", notifOk) {
                    permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
                PermRow("Posizione mentre usi l'app", locOk) {
                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                PermRow("Posizione in background", bgOk, enabled = locOk) {
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                PermRow("Sveglie e promemoria esatti", alarmOk) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) {}
                }
                Text("Dopo aver concesso in Impostazioni, torna qui: la spunta si aggiorna da sola.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sedi", style = MaterialTheme.typography.titleMedium)
                sedi.forEach { s ->
                    Text(s.nome, style = MaterialTheme.typography.titleSmall)
                    Text(s.indirizzo, style = MaterialTheme.typography.bodySmall)
                    Text(if (s.lat == 0.0) "⚠ Posizione GPS non impostata" else "📍 Posizione impostata", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = locOk, onClick = {
                            scope.launch {
                                val loc = Geo.posizione(ctx)
                                if (loc != null) {
                                    s.lat = loc.latitude; s.lng = loc.longitude
                                    Store.salvaSedi(ctx, sedi); riapplica()
                                }
                            }
                        }) { Text("Sono qui ora") }
                        Text("Raggio ${s.raggio} m")
                        OutlinedButton(onClick = { if (s.raggio > 100) { s.raggio -= 50; Store.salvaSedi(ctx, sedi); versione++ } }) { Text("−") }
                        OutlinedButton(onClick = { if (s.raggio < 2000) { s.raggio += 50; Store.salvaSedi(ctx, sedi); versione++ } }) { Text("+") }
                    }
                }
                Text("Mettiti fisicamente nella sede e premi \"Sono qui ora\".", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Suono promemoria", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Suono promemoria")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, imp.suonoUri?.let { Uri.parse(it) })
                        soundLauncher.launch(i)
                    }) { Text("Scegli suono") }
                    if (imp.suonoUri != null) {
                        OutlinedButton(onClick = {
                            try { RingtoneManager.getRingtone(ctx, Uri.parse(imp.suonoUri))?.play() } catch (_: Exception) {}
                        }) { Text("Prova") }
                        OutlinedButton(onClick = {
                            val i2 = Store.impostazioni(ctx); i2.suonoUri = null
                            Store.salvaImpostazioni(ctx, i2); versione++
                        }) { Text("Default") }
                    }
                }
                Text(if (imp.suonoUri == null) "Suono di sistema" else "Suono personalizzato", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("App da aprire con la notifica", style = MaterialTheme.typography.titleMedium)
                val lbl: String = imp.appPackage?.let { pkg ->
                    try {
                        @Suppress("DEPRECATION")
                        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                } ?: "Non impostata"
                Text(lbl, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showApps = true }) { Text("Scegli app") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ripetizione se non timbri", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imp.ripetiOgniMin = (imp.ripetiOgniMin - 1).coerceAtLeast(1); Store.salvaImpostazioni(ctx, imp); versione++ }) { Text("−") }
                    Text("Ogni ${imp.ripetiOgniMin} min", Modifier.weight(1f))
                    OutlinedButton(onClick = { imp.ripetiOgniMin = (imp.ripetiOgniMin + 1).coerceAtMost(60); Store.salvaImpostazioni(ctx, imp); versione++ }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imp.ripetiMaxMin = (imp.ripetiMaxMin - 5).coerceAtLeast(5); Store.salvaImpostazioni(ctx, imp); versione++ }) { Text("−") }
                    Text("Per massimo ${imp.ripetiMaxMin} min", Modifier.weight(1f))
                    OutlinedButton(onClick = { imp.ripetiMaxMin = (imp.ripetiMaxMin + 5).coerceAtMost(240); Store.salvaImpostazioni(ctx, imp); versione++ }) { Text("+") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Turni", style = MaterialTheme.typography.titleMedium)
                giorniCiclo.forEach { g ->
                    val gg = regole.filter { it.giorno == g }.sortedWith(compareBy({ it.ora }, { it.minuto }))
                    if (gg.isEmpty()) return@forEach
                    Text(nomeGiorno(g), style = MaterialTheme.typography.titleSmall)
                    gg.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                TimePickerDialog(ctx, { _, h, m ->
                                    r.ora = h; r.minuto = m
                                    Store.salvaRegole(ctx, regole); riapplica()
                                }, r.ora, r.minuto, true).show()
                            }) { Text(hhmm(r.ora, r.minuto)) }
                            Text("${if (r.tipo == Tipo.INGRESSO) "Ingresso" else "Uscita"} · ${nomeSede(r.sedeId)}",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { regole.remove(r); Store.salvaRegole(ctx, regole); riapplica() }) { Text("✕") }
                        }
                    }
                }
                OutlinedButton(onClick = { showAdd = true }) { Text("Aggiungi turno") }
            }
        }

        Text("La timbratura va sempre confermata da te dentro Gecos Plus.", style = MaterialTheme.typography.bodySmall)
    }

    if (showApps) {
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Scegli Gecos Plus") },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("Chiudi") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    elencoApp(ctx).forEach { (pkg, label) ->
                        TextButton(onClick = {
                            val i3 = Store.impostazioni(ctx); i3.appPackage = pkg
                            Store.salvaImpostazioni(ctx, i3)
                            showApps = false; versione++
                        }) { Text(label) }
                    }
                }
            }
        )
    }

    if (showAdd) {
        var gIdx by remember { mutableStateOf(0) }
        var sIdx by remember { mutableStateOf(0) }
        var tIdx by remember { mutableStateOf(0) }
        var hSel by remember { mutableStateOf(8) }
        var mSel by remember { mutableStateOf(45) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nuovo turno") },
            confirmButton = {
                TextButton(onClick = {
                    if (sedi.isNotEmpty()) {
                        val s = sedi[sIdx.coerceAtMost(sedi.size - 1)]
                        regole.add(Regola(UUID.randomUUID().toString(), s.id, tipiCiclo[tIdx], hSel, mSel, giorniCiclo[gIdx], descrizionePerSede(s.id)))
                        Store.salvaRegole(ctx, regole)
                        showAdd = false; riapplica()
                    }
                }) { Text("Aggiungi") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annulla") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Selettore("Giorno", nomeGiorno(giorniCiclo[gIdx]),
                        { gIdx = (gIdx + giorniCiclo.size - 1) % giorniCiclo.size },
                        { gIdx = (gIdx + 1) % giorniCiclo.size })
                    Selettore("Sede", sedi.getOrElse(sIdx) { sedi[0] }.nome,
                        { if (sedi.isNotEmpty()) sIdx = (sIdx + sedi.size - 1) % sedi.size },
                        { if (sedi.isNotEmpty()) sIdx = (sIdx + 1) % sedi.size })
                    Selettore("Tipo", if (tipiCiclo[tIdx] == Tipo.INGRESSO) "Ingresso" else "Uscita",
                        { tIdx = (tIdx + 1) % tipiCiclo.size },
                        { tIdx = (tIdx + 1) % tipiCiclo.size })
                    TextButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m -> hSel = h; mSel = m }, hSel, mSel, true).show()
                    }) { Text("Ora: ${hhmm(hSel, mSel)}") }
                }
            }
        )
    }
}

private val giorniCiclo = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
private val tipiCiclo = listOf(Tipo.INGRESSO, Tipo.USCITA)

fun descrizionePerSede(id: String): String = when (id) {
    "cap" -> "A.DISABILITA' E DIP.PATOLOGICHE - CAPACITAZIONE - EDUCATORE"
    "sp" -> "A.C.TA' EDUCATIVE RESIDENZIALI - SE POTESSI - EDUCATORE"
    else -> ""
}

fun elencoApp(c: Context): List<Pair<String, String>> {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return c.packageManager.queryIntentActivities(i, 0)
        .mapNotNull {
            val pkg = it.activityInfo?.packageName ?: return@mapNotNull null
            val label = try { it.loadLabel(c.packageManager).toString() } catch (_: Exception) { pkg }
            pkg to label
        }
        .distinctBy { it.first }
        .filter { it.first != c.packageName }
        .sortedBy { it.second.lowercase() }
}

@Composable
fun Selettore(etichetta: String, valore: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onPrev) { Text("◀") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(etichetta, style = MaterialTheme.typography.labelSmall)
            Text(valore, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onNext) { Text("▶") }
    }
}

@Composable
fun PermRow(nome: String, ok: Boolean, enabled: Boolean = true, onAsk: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (ok) "✅" else "⚠️")
        Text(nome, Modifier.weight(1f).padding(start = 6.dp))
        if (!ok) TextButton(enabled = enabled, onClick = onAsk) { Text("Concedi") }
    }
}

@Composable
fun PermRow(nome: String, ok: Boolean, enabled: Boolean = true, onAsk: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            if (ok) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = if (ok) "OK" else "Da concedere",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(nome, Modifier.weight(1f).padding(start = 8.dp))
        if (!ok) TextButton(enabled = enabled, onClick = onAsk) { Text("Concedi") }
    }
}

fun elencoApp(c: Context): List<Pair<String, String>> {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return c.packageManager.queryIntentActivities(i, 0)
        .mapNotNull {
            val pkg = it.activityInfo?.packageName ?: return@mapNotNull null
            val label = try { it.loadLabel(c.packageManager).toString() } catch (_: Exception) { pkg }
            pkg to label
        }
        .distinctBy { it.first }
        .filter { it.first != c.packageName }
        .sortedBy { it.second.lowercase() }
}

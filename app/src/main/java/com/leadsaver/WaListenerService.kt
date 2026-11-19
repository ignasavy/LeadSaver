package com.leadsaver

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Escucha las notificaciones de WhatsApp (normal y Business).
 * Si el que escribe no está agendado, lo guarda como "LEAD N".
 */
class WaListenerService : NotificationListenerService() {

    companion object {
        private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val BIDI = Regex("[‎‏‪-‮⁦-⁩ ]")
        private val JID = Regex("(\\d{8,15})@(s\\.whatsapp\\.net|c\\.us)")
        private val TEL = Regex("tel:\\+?(\\d{8,15})")
        // Un número en texto libre: +54 9 11 2345-6789, 5491123456789, etc.
        private val PHONE_IN_TEXT = Regex("\\+?\\d[\\d\\s().\\-]{7,}\\d")
        // Claves con el texto de los mensajes: no las miramos ni las guardamos (privacidad)
        private val TEXT_KEYS = setOf(
            "android.text", "android.bigText", "android.subText", "android.summaryText",
            "android.infoText", "android.textLines", "text"
        )
    }

    override fun onListenerConnected() {
        LeadSaver.debug(applicationContext, "Servicio conectado ✔")
    }

    override fun onListenerDisconnected() {
        LeadSaver.debug(applicationContext, "Servicio desconectado, reconectando…")
        requestRebind(ComponentName(this, WaListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WA_PACKAGES) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val ctx = applicationContext

        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        val info = StringBuilder()
        info.append("title=").append(clean(title)).append(" | shortcut=").append(n.shortcutId)
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            LeadSaver.debug(ctx, "$info → resumen, ignorado"); return
        }
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            n.shortcutId?.endsWith("@g.us") == true
        ) {
            LeadSaver.debug(ctx, "$info → grupo, ignorado"); return
        }

        // Juntamos todos los textos "de identidad" (no los mensajes) de la notificación
        val found = mutableListOf<String>()
        n.shortcutId?.let { found += it }
        sbn.tag?.let { found += it }
        scan(extras, 0, found)

        val digits = pick(found, title)
        if (digits == null) {
            LeadSaver.debug(ctx, "$info → NO encontré número. Datos: " +
                found.distinct().joinToString(" ; ").take(700))
            return
        }
        val result = try {
            LeadSaver.saveIfUnknown(ctx, digits)
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName} ${e.message}"
        }
        LeadSaver.debug(ctx, "$info → $result")
    }

    private fun clean(s: CharSequence?) = s?.let { BIDI.replace(it.toString(), " ").trim() }

    /** Elige el número del remitente entre todo lo encontrado. */
    private fun pick(found: List<String>, title: CharSequence?): String? {
        for (s in found) JID.find(s)?.let { return it.groupValues[1] }
        for (s in found) TEL.find(s)?.let { return it.groupValues[1] }
        // El título / nombre del remitente es un número cuando no está agendado
        val texts = listOfNotNull(clean(title)) +
            found.filter { it.startsWith("name:") }.map { clean(it.removePrefix("name:"))!! }
        for (t in texts) phoneOnly(t)?.let { return it }
        return null
    }

    private fun phoneOnly(t: String): String? {
        val m = PHONE_IN_TEXT.find(t) ?: return null
        val d = m.value.filter { it.isDigit() }
        // Que el número sea "casi todo" el título (evita confundir con texto que trae números)
        val rest = t.replace(m.value, "").replace(Regex("\\(\\d+\\s*\\p{L}+\\)|[~\\s:]"), "")
        return if (d.length in 8..15 && rest.length <= 2) d else null
    }

    private fun scan(obj: Any?, depth: Int, out: MutableList<String>) {
        if (obj == null || depth > 4) return
        when {
            obj is Bundle -> for (k in obj.keySet()) {
                if (k in TEXT_KEYS) continue
                @Suppress("DEPRECATION")
                scan(obj.get(k), depth + 1, out)
            }
            Build.VERSION.SDK_INT >= 28 && obj is Person -> {
                obj.key?.let { out += it }
                obj.uri?.let { out += it }
                obj.name?.let { out += "name:" + it }
            }
            obj is CharSequence -> {
                val s = obj.toString()
                if (s.length < 120) out += s
            }
            obj is Array<*> -> obj.forEach { scan(it, depth + 1, out) }
            obj is List<*> -> obj.forEach { scan(it, depth + 1, out) }
            obj is Parcelable -> {}
        }
    }
}

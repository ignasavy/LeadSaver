package com.leadsaver

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Escucha las notificaciones de WhatsApp (normal y Business).
 * Si el que escribe no está agendado, lo guarda como "LEAD N".
 */
class WaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LeadSaver"
        private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        // Caracteres invisibles de dirección de texto que WhatsApp mete alrededor de los números
        private val BIDI = Regex("[‎‏‪-‮⁦-⁩]")
        private val PHONE_ONLY = Regex("^\\+?[\\d\\s().\\-]{8,}$")
        // Sufijos tipo " (3 mensajes)" / " (2 messages)"
        private val COUNT_SUFFIX = Regex("\\s*\\(\\d+\\s+\\p{L}+\\)\\s*$")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WA_PACKAGES) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return

        val digits = extractNumber(n, extras) ?: return
        try {
            LeadSaver.saveIfUnknown(applicationContext, digits)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando lead", e)
        }
    }

    override fun onListenerDisconnected() {
        // Si el sistema nos desconecta, pedimos volver a conectarnos
        requestRebind(ComponentName(this, WaListenerService::class.java))
    }

    /** Devuelve solo los dígitos del número (ej. 5491123456789) o null si no es un chat individual. */
    private fun extractNumber(n: Notification, extras: Bundle): String? {
        // 1) El ID del chat (JID), lo más confiable: "5491123456789@s.whatsapp.net"
        val sid = n.shortcutId
        if (sid != null) {
            if (sid.endsWith("@g.us") || sid.contains("broadcast") || sid.contains("newsletter")) return null
            if (sid.endsWith("@s.whatsapp.net")) {
                val d = sid.substringBefore("@").filter { it.isDigit() }
                if (d.length >= 8) return d
            }
        }

        // 2) El remitente del último mensaje (MessagingStyle)
        if (Build.VERSION.SDK_INT >= 28) {
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            val last = msgs?.lastOrNull() as? Bundle
            @Suppress("DEPRECATION")
            val person = last?.get("sender_person") as? Person
            person?.uri?.let { uri ->
                if (uri.startsWith("tel:")) {
                    val d = uri.removePrefix("tel:").filter { it.isDigit() }
                    if (d.length >= 8) return d
                }
            }
            person?.key?.let { key ->
                if (key.endsWith("@s.whatsapp.net")) {
                    val d = key.substringBefore("@").filter { it.isDigit() }
                    if (d.length >= 8) return d
                }
            }
        }

        // 3) Título de la notificación: si no está agendado, WhatsApp muestra el número
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: return null
        val clean = BIDI.replace(title.toString(), "").replace(COUNT_SUFFIX, "").trim()
        if (!PHONE_ONLY.matches(clean)) return null
        val d = clean.filter { it.isDigit() }
        return if (d.length >= 8) d else null
    }
}

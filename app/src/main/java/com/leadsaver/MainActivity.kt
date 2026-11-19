package com.leadsaver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Pantalla mínima: dar permisos y ver el estado. El trabajo real lo hace WaListenerService. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Lead Saver"
            textSize = 26f
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad)
        }
        root.addView(status)

        root.addView(button("1. Permitir contactos") {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), 1)
        })
        root.addView(button("2. Activar acceso a notificaciones") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("3. Quitar límite de batería") { askBattery() })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun refresh() {
        val prefs = getSharedPreferences("leads", Context.MODE_PRIVATE)
        val ok = { b: Boolean -> if (b) "✅" else "❌" }
        val contacts = LeadSaver.hasPerms(this)
        val listener = listenerEnabled()
        status.text = buildString {
            appendLine("${ok(contacts)} Permiso de contactos")
            appendLine("${ok(listener)} Acceso a notificaciones")
            appendLine("${ok(batteryIgnored())} Sin límite de batería")
            appendLine()
            appendLine(if (contacts && listener) "Funcionando en 2do plano 👍" else "Completá los pasos de abajo")
            appendLine()
            appendLine("Leads agendados: ${prefs.getInt("counter", 0)}")
            prefs.getString("last", null)?.let { appendLine("Último: $it") }
        }
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun batteryIgnored(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife")
    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
    }
}

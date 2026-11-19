package com.leadsaver

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.util.Log

object LeadSaver {

    private const val PREFS = "leads"
    private const val NAME_PREFIX = "LEAD "   // Cambiá acá el prefijo si querés otro nombre

    /** Agenda el número como "LEAD N" si todavía no existe en contactos. */
    @Synchronized
    fun saveIfUnknown(ctx: Context, digits: String): String {
        if (!hasPerms(ctx)) return "SIN PERMISO de contactos"
        val number = "+$digits"
        if (existsInContacts(ctx, number)) return "ya estaba agendado ($number)"

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = prefs.getInt("counter", 0) + 1
        val name = "$NAME_PREFIX$n"
        insertContact(ctx, name, number)
        prefs.edit()
            .putInt("counter", n)
            .putString("last", "$name  ·  $number")
            .apply()
        Log.i("LeadSaver", "Agendado $name $number")
        return "AGENDADO $name $number"
    }

    /** Guarda las últimas entradas de diagnóstico para verlas en la app. */
    @Synchronized
    fun debug(ctx: Context, entry: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString("debug", "") ?: ""
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val all = ("[$time] $entry\n\n$old").take(6000)
        prefs.edit().putString("debug", all).apply()
        Log.i("LeadSaver", entry)
    }

    fun hasPerms(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun existsInContacts(ctx: Context, number: String): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { return it.count > 0 }
        return false
    }

    private fun insertContact(ctx: Context, name: String, number: String) {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        )
        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }
}

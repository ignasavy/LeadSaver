# Lead Saver

App Android que corre en 2do plano: cuando te escribe por WhatsApp (o WhatsApp Business) un número que no tenés agendado, lo guarda automáticamente en tus contactos como **LEAD 1**, **LEAD 2**, **LEAD 3**…

## Cómo conseguir el APK

**Opción A – Android Studio**
1. Descomprimí el zip y abrí la carpeta `LeadSaver` con Android Studio.
2. Esperá que sincronice Gradle.
3. Conectá el celu con "Depuración USB" activada y tocá ▶ Run.
   (O: Build → Build APK(s) → el APK queda en `app/build/outputs/apk/debug/`).

**Opción B – Sin instalar nada (GitHub)**
1. Subí la carpeta a un repo de GitHub.
2. En la pestaña *Actions* se compila solo; descargá el artefacto `LeadSaver-apk`.

## Configuración en el celular (una sola vez)
1. Abrí Lead Saver → **Permitir contactos**.
2. **Activar acceso a notificaciones** → activá "Lead Saver".
   - En Android 13+, si aparece "Configuración restringida": Ajustes → Apps → Lead Saver → menú ⋮ → **Permitir configuración restringida**, y volvé a intentar.
3. **Quitar límite de batería** (importante en Xiaomi, Samsung, Motorola; en Xiaomi también activá "Inicio automático").

Listo. No hace falta abrir la app de nuevo.

## Notas
- Las notificaciones de WhatsApp tienen que estar activadas (la app lee la notificación para saber quién escribe).
- Ignora grupos, difusiones y canales.
- Si un chat está silenciado, WhatsApp no muestra notificación y no se agenda.
- Para cambiar "LEAD " por otro texto: `LeadSaver.kt`, constante `NAME_PREFIX`.
- Los contactos se guardan en el teléfono (no en la cuenta de Google).

package com.novalock.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Locale

private const val PREFS = "novalock_secure"
private const val DAY_MS = 86_400_000L
private const val REPO = "raphaelsancho21-byte/NovaLock"
private val BG = Color.rgb(9, 13, 27)
private val PANEL = Color.rgb(21, 27, 47)
private val MUTED = Color.rgb(148, 163, 184)
private val ACCENT = Color.rgb(114, 99, 255)

internal fun Context.store() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
internal fun Context.isProtected(pkg: String) = store().getBoolean("app_" + pkg, false)
internal fun Context.deadline(pkg: String) = store().getLong("until_" + pkg, 0L)
internal fun Context.granted(pkg: String) = store().getBoolean("grant_" + pkg, false)
internal fun Context.setGranted(pkg: String, value: Boolean) { store().edit().putBoolean("grant_" + pkg, value).apply() }
internal fun Context.setDeadline(pkg: String, time: Long) { store().edit().putLong("until_" + pkg, time).apply() }
internal fun Context.hasPin() = store().contains("pin_hash")

internal fun Context.hashPin(pin: String, salt: ByteArray): String {
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
    return try {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        bytes.joinToString("") { "%02x".format(it) }
    } finally { spec.clearPassword() }
}
internal fun Context.savePin(pin: String) {
    val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
    store().edit().putString("pin_salt", salt.joinToString("") { "%02x".format(it) })
        .putString("pin_hash", hashPin(pin, salt)).apply()
}
internal fun Context.checkPin(pin: String): Boolean {
    val hex = store().getString("pin_salt", null) ?: return false
    val salt = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val actual = hashPin(pin, salt).toByteArray()
    val expected = (store().getString("pin_hash", "") ?: "").toByteArray()
    return java.security.MessageDigest.isEqual(actual, expected)
}
internal fun block(color: Int, radius: Float = 22f): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = radius }
internal fun Context.dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()
internal fun Context.title(t: String, size: Float = 17f, color: Int = Color.WHITE): TextView =
    TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
internal fun Context.body(t: String, size: Float = 13f, color: Int = MUTED): TextView =
    TextView(this).apply { text = t; textSize = size; setTextColor(color) }

internal fun Context.vertical(): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.button(t: String, action: () -> Unit): TextView =
    title(t, 15f).apply {
        gravity = Gravity.CENTER; background = block(ACCENT, dp(16).toFloat())
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setOnClickListener { action() }
    }
internal fun Context.card(): LinearLayout =
    vertical().apply {
        background = block(PANEL, dp(22).toFloat())
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }
internal fun LinearLayout.addSpace(context: Context, height: Int) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(height)))
}
internal fun Context.pinField(hintText: String): EditText =
    EditText(this).apply {
        hint = hintText
        setHintTextColor(MUTED); setTextColor(Color.WHITE); textSize = 20f
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        background = block(PANEL, dp(14).toFloat())
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG; window.navigationBarColor = BG
        showHome()
    }
    private fun showHome() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); clipToPadding = false }
        root = vertical().apply { setPadding(dp(20), dp(28), dp(20), dp(34)) }
        scroll.addView(root); setContentView(scroll)

        val top = title("◈  NOVALOCK", 25f)
        root.addView(top)
        root.addView(body("Seu espaço digital, sob controle.", 14f))
        root.addSpace(this, 26)

        val hero = card()
        hero.addView(title("PROTEÇÃO ATIVA", 12f, Color.rgb(148, 136, 255)))
        hero.addSpace(this, 10)
        hero.addView(title("Seus apps. Suas regras.", 23f))
        hero.addSpace(this, 8)
        hero.addView(body("Selecione os aplicativos e use o bloqueio de 24 horas.", 14f))
        root.addView(hero)
        root.addSpace(this, 16)

        if (!hasPin()) {
            val setup = card()
            setup.addView(title("01   Crie seu PIN", 18f))
            setup.addSpace(this, 10)
            setup.addView(body("Defina um código de pelo menos 4 dígitos para desbloquear seus apps."))
            setup.addSpace(this, 12)
            val p1 = pinField("Novo PIN")
            val p2 = pinField("Confirmar PIN")
            setup.addView(p1); setup.addSpace(this, 8); setup.addView(p2); setup.addSpace(this, 12)
            setup.addView(button("Criar PIN de proteção") {
                val pin = p1.text.toString()
                if (pin.length < 4 || pin != p2.text.toString()) {
                    Toast.makeText(this, "Use 4+ dígitos iguais nos dois campos.", Toast.LENGTH_LONG).show()
                } else { savePin(pin); showHome() }
            })
            root.addView(setup)
            return
        }

        val access = card()
        access.addView(title("02   Acessibilidade", 18f))
        access.addSpace(this, 8)
        access.addView(body("Ative NovaLock nos ajustes de Acessibilidade. Essa permissão é necessária para identificar quando um app protegido é aberto."))
        access.addSpace(this, 12)
        access.addView(button("Abrir configurações de acesso") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(access)
        root.addSpace(this, 20)

        root.addView(title("Aplicativos instalados", 20f))
        root.addSpace(this, 5)
        root.addView(body("Marque os apps que deseja proteger."))
        root.addSpace(this, 12)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        if (apps.isEmpty()) root.addView(body("Nenhum aplicativo encontrado."))
        for (app in apps) {
            val pkg = app.activityInfo.packageName
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = block(PANEL, dp(16).toFloat())
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val icon = ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)) }
            row.addView(icon, LinearLayout.LayoutParams(dp(39), dp(39)))
            val labels = vertical().apply { setPadding(dp(12), 0, dp(5), 0) }
            labels.addView(title(app.loadLabel(packageManager).toString(), 14f))
            labels.addView(body(if (isProtected(pkg)) "Proteção selecionada" else "Sem proteção", 11f))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val check = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(ACCENT)
                isChecked = isProtected(pkg)
                setOnCheckedChangeListener { _, checked ->
                    store().edit().putBoolean("app_" + pkg, checked).apply()
                    if (!checked) { setGranted(pkg, false); setDeadline(pkg, 0) }
                    (labels.getChildAt(1) as TextView).text = if (checked) "Proteção selecionada" else "Sem proteção"
                }
            }
            row.addView(check)
            row.setOnClickListener { check.isChecked = !check.isChecked }
            root.addView(row)
            root.addSpace(this, 8)
        }
        root.addSpace(this, 14)
        val update = card()
        update.addView(title("Atualizações automáticas", 18f))
        update.addSpace(this, 6)
        val state = body("NovaLock verifica as versões publicadas no GitHub.")
        update.addView(state)
        update.addSpace(this, 12)
        update.addView(button("Verificar atualização") { checkUpdates(state) })
        root.addView(update)
        checkUpdates(state, silent = true)
    }
    private fun checkUpdates(label: TextView, silent: Boolean = false) {
        Thread {
            try {
                val connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                connection.connectTimeout = 7000; connection.readTimeout = 7000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                val status = connection.responseCode
                if (status != 200) {
                    runOnUiThread { if (!silent) label.text = "Ainda não há versão publicada em Releases (HTTP $status)." }
                    connection.disconnect(); return@Thread
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val tag = json.optString("tag_name").removePrefix("v")
                val url = json.optString("html_url")
                val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
                val newer = compareVersions(tag, current) > 0
                runOnUiThread {
                    label.text = if (newer) "Nova versão disponível: $tag (atual: $current)" else "Você está na versão mais recente ($current)."
                    if (newer && url.startsWith("https://github.com/")) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Atualização disponível")
                            .setMessage("NovaLock $tag já está disponível. Deseja abrir a versão oficial no GitHub?")
                            .setPositiveButton("Abrir atualização") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }.setNegativeButton("Depois", null).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (!silent) label.text = "Não foi possível verificar: ${e.localizedMessage}" }
            }
        }.start()
    }
    private fun compareVersions(a: String, b: String): Int {
        val x = a.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}

class GuardService : AccessibilityService() {
    private var previousPackage = ""
    private var lastLockLaunch = 0L
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg != previousPackage) {
            if (previousPackage.isNotBlank() && isProtected(previousPackage) && granted(previousPackage)) {
                setGranted(previousPackage, false)
                setDeadline(previousPackage, System.currentTimeMillis() + DAY_MS)
            }
            previousPackage = pkg
        }
        if (!isProtected(pkg) || granted(pkg)) return
        if (SystemClock.elapsedRealtime() - lastLockLaunch < 1200L) return
        lastLockLaunch = SystemClock.elapsedRealtime()
        val intent = Intent(this, LockActivity::class.java)
            .putExtra("target", pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try { startActivity(intent) } catch (_: Exception) { }
    }
    override fun onInterrupt() {}
}

class LockActivity : Activity() {
    private lateinit var target: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra("target") ?: run { finish(); return }
        window.statusBarColor = BG; window.navigationBarColor = BG
        window.setFlags(8192, 8192) // FLAG_SECURE: evita capturas de tela
        showLock()
    }
    private fun showLock() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val layout = vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(100), dp(26), dp(30))
        }
        scroll.addView(layout); setContentView(scroll)
        layout.addView(title("◈  NOVALOCK", 22f, Color.rgb(165, 155, 255)))
        layout.addSpace(this, 45)
        layout.addView(title("🔒", 55f))
        layout.addSpace(this, 20)
        layout.addView(title("Aplicativo protegido", 23f))
        layout.addSpace(this, 10)
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }
                   catch (_: PackageManager.NameNotFoundException) { target }
        layout.addView(body(name, 16f))
        layout.addSpace(this, 28)
        val wait = (deadline(target) - System.currentTimeMillis()).coerceAtLeast(0L)
        if (wait > 0) {
            val hours = wait / 3_600_000
            val mins = (wait % 3_600_000) / 60_000
            layout.addView(title("Acesso indisponível", 20f))
            layout.addSpace(this, 8)
            layout.addView(body("Tempo restante: ${hours}h ${mins}min", 17f))
            layout.addSpace(this, 16)
            layout.addView(body("O período de 24 horas começa depois que você sai do aplicativo desbloqueado."))
        } else {
            layout.addView(body("Digite o PIN para liberar uma sessão."))
            layout.addSpace(this, 15)
            val pin = pinField("Digite seu PIN")
            layout.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            layout.addSpace(this, 12)
            layout.addView(button("Desbloquear aplicativo") {
                if (checkPin(pin.text.toString())) {
                    setGranted(target, true); setDeadline(target, 0L)
                    val launch = packageManager.getLaunchIntentForPackage(target)
                    if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch) }
                    finish()
                } else { Toast.makeText(this, "PIN incorreto", Toast.LENGTH_SHORT).show(); pin.text.clear() }
            })
        }
        layout.addSpace(this, 14)
        layout.addView(button("Voltar à tela inicial") {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        })
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

package ru.gs8triple

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private data class AppEntry(val label: String, val pkg: String, val activity: String) {
        override fun toString() = label
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var status: TextView
    private lateinit var countPicker: Spinner
    private lateinit var appPickers: List<Spinner>
    private lateinit var leftSeek: SeekBar
    private lateinit var centerSeek: SeekBar
    private var apps: List<AppEntry> = emptyList()
    private val defaults = listOf("ru.yandex.music", "ru.yandex.yandexnavi", "ru.rutube.app")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("gs8triple_v2", MODE_PRIVATE)
        apps = discoverApps()
        buildUi()
        refreshPickers()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
        }
        root.addView(TextView(this).apply { text = "GS8 TRIPLE 2.0"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "GAC GS8 Gen2 • 2/3 окна • Freeform"
            textSize = 13f
        })
        root.addView(label("КОЛИЧЕСТВО ОКОН"))
        countPicker = Spinner(this)
        countPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("2 окна — 50/50", "3 окна"))
        countPicker.setSelection(if (prefs.getInt("count", 3) == 2) 0 else 1)
        root.addView(countPicker)

        root.addView(label("ПРИЛОЖЕНИЯ"))
        appPickers = (0..2).map { i ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(TextView(this).apply { text = listOf("ЛЕВОЕ ОКНО", "ЦЕНТРАЛЬНОЕ ОКНО", "ПРАВОЕ ОКНО")[i] })
            val spinner = Spinner(this)
            row.addView(spinner)
            root.addView(row)
            spinner
        }

        root.addView(label("РАЗМЕРЫ ОКОН"))
        root.addView(TextView(this).apply { text = "Ширина левого окна" })
        leftSeek = SeekBar(this).apply { max = 1400; progress = (prefs.getInt("left", 400) - 200).coerceIn(0, 1400) }
        root.addView(leftSeek)
        root.addView(TextView(this).apply { text = "Ширина центрального окна (для 3 окон)" })
        centerSeek = SeekBar(this).apply { max = 1400; progress = (prefs.getInt("center", 760) - 200).coerceIn(0, 1400) }
        root.addView(centerSeek)

        root.addView(Button(this).apply {
            text = "СОХРАНИТЬ НАСТРОЙКИ"
            setOnClickListener { saveConfig(); status.text = "Настройки сохранены." }
        })
        root.addView(Button(this).apply {
            text = "РАЗЛОЖИТЬ ОКНА"
            setOnClickListener { saveConfig(); launchLayout() }
        })
        root.addView(Button(this).apply {
            text = "ПОКАЗАТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    status.text = "Разреши отображение поверх других окон, затем снова нажми кнопку."
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                    status.text = "Панель запущена. Она меняет и сохраняет желаемые размеры."
                }
            }
        })
        root.addView(Button(this).apply {
            text = "ОСТАНОВИТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "ОБНОВИТЬ СПИСОК ПРИЛОЖЕНИЙ"
            setOnClickListener { apps = discoverApps(); refreshPickers(); status.text = "Найдено приложений: ${apps.size}" }
        })
        status = TextView(this).apply { text = "Выбери два или три приложения."; textSize = 14f }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(s: String) = TextView(this).apply {
        text = "\n$s"; textSize = 17f; setPadding(0, 10, 0, 4)
    }

    @Suppress("DEPRECATION")
    private fun discoverApps(): List<AppEntry> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .map { AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    private fun refreshPickers() {
        appPickers.forEachIndexed { i, spinner ->
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps)
            val wanted = prefs.getString("app$i", defaults[i])
            val idx = apps.indexOfFirst { it.pkg == wanted }
            if (idx >= 0) spinner.setSelection(idx)
        }
    }

    private fun saveConfig() {
        val edit = prefs.edit()
            .putInt("count", if (countPicker.selectedItemPosition == 0) 2 else 3)
            .putInt("left", leftSeek.progress + 200)
            .putInt("center", centerSeek.progress + 200)
        appPickers.forEachIndexed { i, spinner ->
            apps.getOrNull(spinner.selectedItemPosition)?.let { edit.putString("app$i", it.pkg) }
        }
        edit.apply()
    }

    private fun launchLayout() {
        val count = prefs.getInt("count", 3)
        val selected = appPickers.take(count).mapNotNull { s -> apps.getOrNull(s.selectedItemPosition) }
        if (selected.size != count || selected.map { it.pkg }.distinct().size != count) {
            status.text = "Выбери $count разных приложений."
            return
        }
        val width = 1920
        val height = 968
        val config = LayoutConfig(count, prefs.getInt("left", if (count == 2) 960 else 400),
            prefs.getInt("center", 760), width, height)
        val rects = config.bounds()
        val report = StringBuilder()
        selected.forEachIndexed { i, app ->
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(app.pkg, app.activity))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                val options = ActivityOptions.makeBasic()
                val method = ActivityOptions::class.java.getDeclaredMethod(
                    "setLaunchWindowingMode", Int::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(options, 5)
                options.launchBounds = rects[i]
                startActivity(intent, options.toBundle())
                report.append("Запрос отправлен: ${app.label} → ${rects[i]}\n")
            } catch (e: Exception) {
                val cause = e.cause ?: e
                report.append("Ошибка ${app.label}: ${cause.javaClass.simpleName}: ${cause.message}\n")
            }
        }
        status.text = report.toString()
    }
}

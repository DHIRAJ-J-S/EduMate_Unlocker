package com.the404guy.em_unlocker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EduMateUnlocker"
        private const val PREFS_NAME = "EduMateUnlockerPrefs"
        private const val KEY_SPOOF_ENABLED = "spoof_enabled"
        private const val KEY_SELECTED_COLLEGE = "selected_college"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_USE_LEGACY_SITE = "use_legacy_site"
        private const val KEY_TIP_SHOWN = "tip_shown"
        private const val KEY_CHECK_UPDATES = "check_updates_on_startup"
        
        const val COLLEGE_SIT = "SIT"
        const val COLLEGE_SEC = "SEC"
        
        private const val CURRENT_VERSION = "1.3"
        
        // ============================================
        // GITHUB REPOSITORY URL
        // ============================================
        private const val GITHUB_REPO_URL = "https://github.com/DHIRAJ-J-S/EduMate_Unlocker"
        private const val GITHUB_API_RELEASES = "https://api.github.com/repos/DHIRAJ-J-S/EduMate_Unlocker/releases/latest"
        private const val GITHUB_RELEASES_PAGE = "https://github.com/DHIRAJ-J-S/EduMate_Unlocker/releases/latest"
        // ============================================
        
        val NEW_SITE_URLS = mapOf(
            COLLEGE_SIT to "https://student.sairamit.edu.in/",
            COLLEGE_SEC to "https://student.sairam.edu.in/"
        )
        
        val LEGACY_SITE_URLS = mapOf(
            COLLEGE_SIT to "https://edumate.sairamit.edu.in/",
            COLLEGE_SEC to "https://edumate.sairam.edu.in/"
        )
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusText: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var prefs: SharedPreferences
    
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    
    private var easterEggClicks = 0
    private var settingsDialog: AlertDialog? = null
    private var updateBottomSheet: BottomSheetDialog? = null
    
    private var spoofEnabled = true
    private var selectedCollege = COLLEGE_SIT
    private var useLegacySite = false
    private var updateIgnoredThisSession = false
    private var currentDownloadId: Long = -1

    private val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private var defaultUserAgent = ""

    private val desktopSpoofScript = """
        (function() {
            try {
                if (window.__desktopSpoofed) return;
                window.__desktopSpoofed = true;
                Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; } });
                Object.defineProperty(screen, 'width', { get: () => 1920 });
                Object.defineProperty(screen, 'height', { get: () => 1080 });
                Object.defineProperty(screen, 'availWidth', { get: () => 1920 });
                Object.defineProperty(screen, 'availHeight', { get: () => 1040 });
                Object.defineProperty(window, 'innerWidth', { get: () => 1920 });
                Object.defineProperty(window, 'innerHeight', { get: () => 1080 });
                Object.defineProperty(window, 'orientation', { get: () => undefined });
                Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });
                const origMatchMedia = window.matchMedia;
                window.matchMedia = function(q) {
                    if (q.includes('max-width') && (q.includes('600') || q.includes('768') || q.includes('480'))) {
                        return { matches: false, media: q, addEventListener: ()=>{}, removeEventListener: ()=>{} };
                    }
                    if (q.includes('pointer: coarse') || q.includes('hover: none')) {
                        return { matches: false, media: q, addEventListener: ()=>{}, removeEventListener: ()=>{} };
                    }
                    return origMatchMedia.call(window, q);
                };
                return 'OK';
            } catch(e) {
                return 'ERROR: ' + e.message;
            }
        })();
    """.trimIndent()

    private val legacyLoginFixScript = """
        (function() {
            try {
                if (window.__loginFixed) return;
                window.__loginFixed = true;
                
                var staffRadio = document.querySelector('input.loginradiobutton[value="Staff"]');
                if (staffRadio) {
                    staffRadio.value = 'Student';
                    staffRadio.checked = true;
                }
                
                var labels = document.querySelectorAll('label');
                labels.forEach(function(label) {
                    var radio = label.querySelector('input.loginradiobutton');
                    var span = label.querySelector('span');
                    if (radio && span) {
                        if (radio.value === 'Student' && radio.checked) {
                            span.textContent = 'Student';
                        } else if (radio.value === 'Student' && !radio.checked) {
                            label.style.display = 'none';
                        }
                    }
                });
                
                var extensionInput = document.getElementById('Extension');
                if (extensionInput) extensionInput.value = '@sairamtap.edu.in';
                var extInputByName = document.querySelector('input[name="ExtensionName"]');
                if (extInputByName) extInputByName.value = '@sairamtap.edu.in';
                var extInputByClass = document.querySelector('input.clsExtn');
                if (extInputByClass) extInputByClass.value = '@sairamtap.edu.in';
                
                return 'OK';
            } catch(e) {
                return 'ERROR: ' + e.message;
            }
        })();
    """.trimIndent()
    
    private val legacyNavbarFixScript = """
        (function() {
            try {
                if (window.__navbarFixed) return;
                window.__navbarFixed = true;
                var style = document.createElement('style');
                style.textContent = '.menu-toggler { top: 55px !important; z-index: 1000 !important; } .main-container-inner { padding-top: 10px !important; } .sidebar { top: 50px !important; } .navbar-fixed-top { z-index: 1030 !important; }';
                document.head.appendChild(style);
                return 'OK';
            } catch(e) {
                return 'ERROR: ' + e.message;
            }
        })();
    """.trimIndent()

    private val newSiteLoginFixScript = """
        (function() {
            try {
                if (window.__loginCssFixed) return;
                window.__loginCssFixed = true;
                
                var style = document.createElement('style');
                style.textContent = `
                    form#form {
                        width: 100% !important;
                        max-width: 400px !important;
                        padding: 0 16px !important;
                    }
                    form#form input[type="text"],
                    form#form input[type="password"],
                    form#form input[name="studentId"],
                    form#form input[name="password"] {
                        width: 100% !important;
                        min-width: 280px !important;
                        height: 48px !important;
                        min-height: 48px !important;
                        font-size: 16px !important;
                        padding: 12px 12px 12px 44px !important;
                        box-sizing: border-box !important;
                    }
                    form#form input[type="password"],
                    form#form input[name="password"] {
                        padding-right: 48px !important;
                    }
                    form#form .relative,
                    form#form .space-y-2 {
                        width: 100% !important;
                    }
                    form#form .absolute.left-3 {
                        left: 12px !important;
                    }
                    form#form button[type="button"] {
                        min-width: 44px !important;
                        min-height: 44px !important;
                        padding: 10px !important;
                    }
                    form#form button[type="submit"],
                    form#form .bg-primary {
                        width: 100% !important;
                        min-width: 280px !important;
                        height: 48px !important;
                        font-size: 16px !important;
                    }
                    .w-full.space-y-6 {
                        width: 100% !important;
                        max-width: 400px !important;
                    }
                `;
                document.head.appendChild(style);
                return 'OK';
            } catch(e) {
                return 'ERROR: ' + e.message;
            }
        })();
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        spoofEnabled = prefs.getBoolean(KEY_SPOOF_ENABLED, true)
        selectedCollege = prefs.getString(KEY_SELECTED_COLLEGE, COLLEGE_SIT) ?: COLLEGE_SIT
        
        useLegacySite = false
        prefs.edit().putBoolean(KEY_USE_LEGACY_SITE, false).apply()
        
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val uris = mutableListOf<Uri>()
                data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                }
                if (uris.isEmpty()) {
                    data?.data?.let { uris.add(it) }
                }
                fileUploadCallback?.onReceiveValue(uris.toTypedArray())
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        statusText = findViewById(R.id.statusText)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        defaultUserAgent = WebSettings.getDefaultUserAgent(this)
        
        setupWebView()
        setupSwipeRefresh()
        setupDownloadListener()

        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirstLaunch) {
            showCollegeSelectionDialog(isFirstLaunch = true)
        } else {
            applyCollegeTheme(selectedCollege)
            loadSelectedCollege()
            
            // Check for updates only if not first launch and enabled
            if (prefs.getBoolean(KEY_CHECK_UPDATES, true)) {
                checkForUpdates()
            }
        }
    }

    // ==================== UPDATE CHECKER ====================
    
    private fun checkForUpdates() {
        if (updateIgnoredThisSession) return
        
        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL(GITHUB_API_RELEASES)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val latestVersion = json.getString("tag_name").removePrefix("v")
                    val changelog = json.optString("body", "• Bug fixes and improvements")
                    val downloadUrl = getApkDownloadUrl(json)
                    
                    if (isNewerVersion(latestVersion, CURRENT_VERSION) && downloadUrl != null) {
                        runOnUiThread {
                            showUpdateDialog(latestVersion, changelog, downloadUrl)
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }
    
    private fun getApkDownloadUrl(json: JSONObject): String? {
        try {
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    return asset.getString("browser_download_url")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse APK URL", e)
        }
        return null
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison failed", e)
        }
        return false
    }
    
    private fun showUpdateDialog(latestVersion: String, changelog: String, downloadUrl: String) {
        if (isFinishing || isDestroyed) return
        
        updateBottomSheet = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
        updateBottomSheet?.setContentView(dialogView)
        
        val textCurrentVersion = dialogView.findViewById<TextView>(R.id.textCurrentVersion)
        val textLatestVersion = dialogView.findViewById<TextView>(R.id.textLatestVersion)
        val textChangelog = dialogView.findViewById<TextView>(R.id.textChangelog)
        val changelogHeader = dialogView.findViewById<LinearLayout>(R.id.changelogHeader)
        val changelogContainer = dialogView.findViewById<ScrollView>(R.id.changelogContainer)
        val iconExpand = dialogView.findViewById<ImageView>(R.id.iconExpandChangelog)
        val btnInstall = dialogView.findViewById<Button>(R.id.btnInstallUpdate)
        val btnViewRelease = dialogView.findViewById<Button>(R.id.btnViewRelease)
        val btnIgnore = dialogView.findViewById<Button>(R.id.btnIgnoreUpdate)
        
        textCurrentVersion?.text = CURRENT_VERSION
        textLatestVersion?.text = latestVersion
        textChangelog?.text = changelog.ifEmpty { "• Bug fixes and improvements" }
        
        // Apply theme color to install button
        val primaryColor = if (selectedCollege == COLLEGE_SIT) {
            ContextCompat.getColor(this, R.color.sit_primary)
        } else {
            ContextCompat.getColor(this, R.color.sec_primary)
        }
        btnInstall?.backgroundTintList = ColorStateList.valueOf(primaryColor)
        
        // Collapsible changelog
        var changelogExpanded = false
        changelogHeader?.setOnClickListener {
            changelogExpanded = !changelogExpanded
            changelogContainer?.visibility = if (changelogExpanded) View.VISIBLE else View.GONE
            iconExpand?.rotation = if (changelogExpanded) 180f else 0f
        }
        
        btnInstall?.setOnClickListener {
            updateBottomSheet?.dismiss()
            downloadAndInstallApk(downloadUrl, latestVersion)
        }
        
        btnViewRelease?.setOnClickListener {
            updateBottomSheet?.dismiss()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_PAGE)))
            } catch (e: Exception) {
                showToast("Could not open browser")
            }
        }
        
        btnIgnore?.setOnClickListener {
            updateIgnoredThisSession = true
            updateBottomSheet?.dismiss()
        }
        
        updateBottomSheet?.show()
    }
    
    private fun downloadAndInstallApk(downloadUrl: String, version: String) {
        try {
            val fileName = "EduMate_Unlocker_$version.apk"
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("EduMate Unlocker $version")
                .setDescription("Downloading update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            currentDownloadId = downloadManager.enqueue(request)
            
            showToast("Downloading update...")
            
            // Register receiver for download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == currentDownloadId) {
                        installApk(fileName)
                        try {
                            unregisterReceiver(this)
                        } catch (e: Exception) { }
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            showToast("Download failed: ${e.message}")
        }
    }
    
    private fun installApk(fileName: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            showToast("Please install manually from Downloads folder")
        }
    }

    // ==================== END UPDATE CHECKER ====================

    private fun showCollegeSelectionDialog(isFirstLaunch: Boolean = false) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_college_selection, null)
        
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .setCancelable(!isFirstLaunch)
            .create()

        val sitCard = dialogView.findViewById<CardView>(R.id.cardSIT)
        val secCard = dialogView.findViewById<CardView>(R.id.cardSEC)
        val legacySwitch = dialogView.findViewById<SwitchCompat>(R.id.switchLegacySite)
        
        legacySwitch.isChecked = useLegacySite
        
        legacySwitch.setOnCheckedChangeListener { _, isChecked ->
            useLegacySite = isChecked
        }
        
        if (!isFirstLaunch) {
            when (selectedCollege) {
                COLLEGE_SIT -> sitCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sit_light))
                COLLEGE_SEC -> secCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sec_light))
            }
        }

        sitCard.setOnClickListener {
            selectedCollege = COLLEGE_SIT
            saveCollegePreference()
            applyCollegeTheme(COLLEGE_SIT)
            dialog.dismiss()
            
            if (isFirstLaunch && !prefs.getBoolean(KEY_TIP_SHOWN, false)) {
                showTipDialog()
            } else {
                loadSelectedCollege()
            }
        }

        secCard.setOnClickListener {
            selectedCollege = COLLEGE_SEC
            saveCollegePreference()
            applyCollegeTheme(COLLEGE_SEC)
            dialog.dismiss()
            
            if (isFirstLaunch && !prefs.getBoolean(KEY_TIP_SHOWN, false)) {
                showTipDialog()
            } else {
                loadSelectedCollege()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showTipDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tip, null)
        
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnGotIt = dialogView.findViewById<Button>(R.id.btnGotIt)
        
        val primaryColor = if (selectedCollege == COLLEGE_SIT) {
            ContextCompat.getColor(this, R.color.sit_primary)
        } else {
            ContextCompat.getColor(this, R.color.sec_primary)
        }
        btnGotIt.backgroundTintList = ColorStateList.valueOf(primaryColor)
        
        btnGotIt.setOnClickListener {
            prefs.edit().putBoolean(KEY_TIP_SHOWN, true).apply()
            dialog.dismiss()
            loadSelectedCollege()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        settingsDialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .create()

        val btnSIT = dialogView.findViewById<CardView>(R.id.btnSIT)
        val btnSEC = dialogView.findViewById<CardView>(R.id.btnSEC)
        val textSIT = dialogView.findViewById<TextView>(R.id.textSIT)
        val textSEC = dialogView.findViewById<TextView>(R.id.textSEC)
        val switchLegacy = dialogView.findViewById<SwitchCompat>(R.id.switchLegacy)
        val switchUpdateCheck = dialogView.findViewById<SwitchCompat>(R.id.switchUpdateCheck)
        val btnClearCache = dialogView.findViewById<LinearLayout>(R.id.btnClearCache)
        val btnAbout = dialogView.findViewById<LinearLayout>(R.id.btnAbout)
        val btnGitHub = dialogView.findViewById<LinearLayout>(R.id.btnGitHub)
        
        if (selectedCollege == COLLEGE_SIT) {
            btnSIT.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sit_light))
        } else {
            btnSEC.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sec_light))
        }
        
        switchLegacy.isChecked = useLegacySite
        switchUpdateCheck?.isChecked = prefs.getBoolean(KEY_CHECK_UPDATES, true)
        
        btnSIT.setOnClickListener {
            if (selectedCollege != COLLEGE_SIT) {
                selectedCollege = COLLEGE_SIT
                saveCollegePreference()
                applyCollegeTheme(COLLEGE_SIT)
                settingsDialog?.dismiss()
                loadSelectedCollege()
            }
        }
        
        btnSEC.setOnClickListener {
            if (selectedCollege != COLLEGE_SEC) {
                selectedCollege = COLLEGE_SEC
                saveCollegePreference()
                applyCollegeTheme(COLLEGE_SEC)
                settingsDialog?.dismiss()
                loadSelectedCollege()
            }
        }
        
        switchLegacy.setOnCheckedChangeListener { _, isChecked ->
            useLegacySite = isChecked
            prefs.edit().putBoolean(KEY_USE_LEGACY_SITE, useLegacySite).apply()
            showToast("Old EduMate: ${if (useLegacySite) "ON" else "OFF"}")
            applyCollegeTheme(selectedCollege)
            settingsDialog?.dismiss()
            loadSelectedCollege()
        }
        
        switchUpdateCheck?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_CHECK_UPDATES, isChecked).apply()
        }
        
        btnClearCache.setOnClickListener {
            webView.clearCache(true)
            webView.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            showToast("Cache cleared")
            settingsDialog?.dismiss()
            webView.reload()
        }
        
        btnAbout.setOnClickListener {
            settingsDialog?.dismiss()
            showAboutDialog()
        }
        
        btnGitHub.setOnClickListener {
            settingsDialog?.dismiss()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Could not open GitHub")
            }
        }

        settingsDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        settingsDialog?.show()
    }

    private fun applyCollegeTheme(college: String) {
        val primaryColor: Int
        val darkColor: Int
        
        if (college == COLLEGE_SIT) {
            primaryColor = ContextCompat.getColor(this, R.color.sit_primary)
            darkColor = ContextCompat.getColor(this, R.color.sit_dark)
        } else {
            primaryColor = ContextCompat.getColor(this, R.color.sec_primary)
            darkColor = ContextCompat.getColor(this, R.color.sec_dark)
        }
        
        toolbar.setBackgroundColor(primaryColor)
        
        val title = if (useLegacySite) "EduMate Unlocker - $college (Legacy)" else "EduMate Unlocker - $college"
        supportActionBar?.title = title
        
        window.statusBarColor = darkColor
        window.navigationBarColor = primaryColor
        
        swipeRefresh.setColorSchemeColors(primaryColor)
        progressBar.progressTintList = ColorStateList.valueOf(primaryColor)
    }

    private fun saveCollegePreference() {
        prefs.edit()
            .putString(KEY_SELECTED_COLLEGE, selectedCollege)
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }

    private fun loadSelectedCollege() {
        val url = if (useLegacySite) {
            LEGACY_SITE_URLS[selectedCollege] ?: NEW_SITE_URLS[COLLEGE_SIT]
        } else {
            NEW_SITE_URLS[selectedCollege] ?: NEW_SITE_URLS[COLLEGE_SIT]
        }
        
        if (!useLegacySite && spoofEnabled) {
            webView.settings.userAgentString = desktopUserAgent
        } else {
            webView.settings.userAgentString = defaultUserAgent
        }
        
        webView.loadUrl(url!!)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                updateStatus("Loading...")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                
                if (!useLegacySite && spoofEnabled) {
                    view?.evaluateJavascript(desktopSpoofScript, null)
                    view?.evaluateJavascript(newSiteLoginFixScript, null)
                }
                
                if (useLegacySite && url?.contains("edumate") == true) {
                    view?.evaluateJavascript(legacyLoginFixScript, null)
                    view?.evaluateJavascript(legacyNavbarFixScript, null)
                }
                
                updateStatus("EduMate Unlocker - $selectedCollege | Ready")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.contains("sairam")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open external URL", e)
                }
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    updateStatus("Error: ${error?.description}")
                    showToast("Load failed: ${error?.description}")
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress < 100) {
                    updateStatus("Loading... $newProgress%")
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "Console: ${consoleMessage?.message()}")
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Alert")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.confirm() }
                    .show()
                return true
            }
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                
                try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                        return true
                    }
                    
                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(fallbackIntent)
                    return true
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    showToast("Could not open file picker")
                    return false
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val filename = extractFilename(url, contentDisposition, mimetype)
            handleDownloadViaJavaScript(url, filename, mimetype)
        }
    }
    
    private fun extractFilename(url: String, contentDisposition: String?, mimetype: String?): String {
        if (!contentDisposition.isNullOrEmpty()) {
            val filenamePattern = Regex("filename[*]?=[\"']?([^\"';\\s]+)[\"']?")
            val match = filenamePattern.find(contentDisposition)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        try {
            val urlPath = Uri.parse(url).lastPathSegment
            if (!urlPath.isNullOrEmpty() && urlPath.contains(".")) {
                return urlPath
            }
        } catch (e: Exception) { }
        
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "bin"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "download_$timestamp.$extension"
    }
    
    private fun handleDownloadViaJavaScript(url: String, filename: String, mimetype: String?) {
        if (url.startsWith("blob:")) {
            handleBlobDownload(filename)
        } else {
            downloadFile(url, filename, mimetype)
        }
    }

    private fun handleBlobDownload(filename: String) {
        val script = """
            (function() {
                try {
                    var links = document.querySelectorAll('a[href^="blob:"]');
                    if (links.length > 0) {
                        var blobUrl = links[links.length - 1].href;
                        fetch(blobUrl)
                            .then(response => response.blob())
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onload = function() {
                                    window.AndroidBridge && window.AndroidBridge.onBlobData(reader.result, '$filename');
                                };
                                reader.readAsDataURL(blob);
                            });
                        return 'fetching';
                    }
                    return 'no blob found';
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Blob download result: $result")
        }
    }
    
    private fun downloadFile(url: String, filename: String, mimetype: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("Downloading...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            if (!mimetype.isNullOrEmpty()) {
                request.setMimeType(mimetype)
            }
            
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            showToast("Download started: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            showToast("Download failed: ${e.message}")
        }
    }

    private fun updateStatus(status: String) {
        statusText.text = status
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        val appIcon = dialogView.findViewById<ImageView>(R.id.appIcon)
        val appNameText = dialogView.findViewById<TextView>(R.id.appNameText)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val versionText = dialogView.findViewById<TextView>(R.id.versionText)
        val taglineText = dialogView.findViewById<TextView>(R.id.taglineText)
        val creditText = dialogView.findViewById<TextView>(R.id.creditText)
        val easterEggContainer = dialogView.findViewById<FrameLayout>(R.id.easterEggContainer)
        val easterEggCard = dialogView.findViewById<CardView>(R.id.easterEggCard)
        val easterEggImage = dialogView.findViewById<ImageView>(R.id.easterEggImage)
        val easterEggError = dialogView.findViewById<TextView>(R.id.easterEggError)
        
        appIcon?.setImageResource(R.mipmap.ic_launcher)
        appNameText?.text = "EduMate Unlocker"
        titleText?.text = "404, About Page Not Found"
        versionText?.text = "v$CURRENT_VERSION"
        taglineText?.text = "Thank You for Trying out my App ;)"
        
        val creditString = "Cooked up by the.404guy"
        val spannableCredit = SpannableString(creditString)
        val startIndex = creditString.indexOf("the.404guy")
        val endIndex = startIndex + "the.404guy".length
        spannableCredit.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        creditText?.text = spannableCredit
        
        easterEggClicks = 0
        
        versionText?.setOnClickListener {
            easterEggClicks++
            
            if (easterEggClicks % 5 == 0) {
                easterEggContainer?.visibility = View.VISIBLE
                
                if (easterEggClicks == 5) {
                    easterEggCard?.visibility = View.VISIBLE
                    easterEggError?.visibility = View.GONE
                    try {
                        easterEggImage?.setImageResource(R.drawable.easter_egg_photo)
                    } catch (e: Exception) {
                        easterEggImage?.setImageResource(R.mipmap.ic_launcher)
                    }
                } else {
                    easterEggCard?.visibility = View.GONE
                    easterEggError?.visibility = View.VISIBLE
                }
                
                Handler(Looper.getMainLooper()).postDelayed({
                    easterEggContainer?.visibility = View.GONE
                }, 2000)
            }
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

package com.aura.sairamportal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EduMateUnlocker"
        private const val PREFS_NAME = "EduMateUnlockerPrefs"
        private const val KEY_SPOOF_ENABLED = "spoof_enabled"
        private const val KEY_SELECTED_COLLEGE = "selected_college"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_USE_LEGACY_SITE = "use_legacy_site"
        private const val KEY_TIP_SHOWN = "tip_shown"
        
        const val COLLEGE_SIT = "SIT"
        const val COLLEGE_SEC = "SEC"
        
        // ============================================
        // GITHUB REPOSITORY URL - EDIT THIS LINE
        // ============================================
        private const val GITHUB_REPO_URL = "https://github.com/DHIRAJ-J-S/EduMate_Unlocker"
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
    
    private var spoofEnabled = true
    private var selectedCollege = COLLEGE_SIT
    private var useLegacySite = false

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

    // CSS injection to fix small text inputs on new site (portrait mode)
    private val inputFixCssScript = """
        (function() {
            try {
                if (window.__inputFixed) return;
                window.__inputFixed = true;
                
                var style = document.createElement('style');
                style.textContent = `
                    /* Fix small input fields on mobile */
                    input[type="text"],
                    input[type="email"],
                    input[type="password"],
                    input[type="tel"],
                    input[type="number"],
                    input.form-control,
                    .form-control,
                    .MuiInputBase-input,
                    .MuiOutlinedInput-input,
                    input {
                        min-height: 48px !important;
                        height: auto !important;
                        font-size: 16px !important;
                        padding: 12px 14px !important;
                        line-height: 1.5 !important;
                        box-sizing: border-box !important;
                    }
                    
                    /* Fix password field container */
                    .MuiOutlinedInput-root,
                    .MuiInputBase-root,
                    .input-group,
                    .form-group input {
                        min-height: 48px !important;
                    }
                    
                    /* Make input containers wider */
                    .MuiFormControl-root,
                    .form-group,
                    .input-group {
                        width: 100% !important;
                        max-width: 350px !important;
                    }
                    
                    /* Fix icon buttons inside inputs */
                    .MuiIconButton-root,
                    .input-group-addon,
                    .password-toggle {
                        min-width: 44px !important;
                        min-height: 44px !important;
                        padding: 8px !important;
                    }
                    
                    /* Ensure proper spacing */
                    .MuiOutlinedInput-adornedEnd {
                        padding-right: 8px !important;
                    }
                    
                    /* Login form container */
                    form, .login-form, .auth-form {
                        width: 100% !important;
                        max-width: 400px !important;
                        padding: 0 16px !important;
                        box-sizing: border-box !important;
                    }
                `;
                document.head.appendChild(style);
                
                console.log('[EduMate] Input CSS fix applied');
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        spoofEnabled = prefs.getBoolean(KEY_SPOOF_ENABLED, true)
        selectedCollege = prefs.getString(KEY_SELECTED_COLLEGE, COLLEGE_SIT) ?: COLLEGE_SIT
        
        // Legacy mode does NOT persist across launches
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
        }
    }

    private fun showCollegeSelectionDialog(isFirstLaunch: Boolean = false) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_college_selection, null)
        
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .setCancelable(!isFirstLaunch)
            .create()

        val sitCard = dialogView.findViewById<CardView>(R.id.cardSIT)
        val secCard = dialogView.findViewById<CardView>(R.id.cardSEC)
        val legacySwitch = dialogView.findViewById<SwitchCompat>(R.id.switchLegacySite)
        
        // Set current legacy state
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
            
            // Show tip dialog only on first launch
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
            
            // Show tip dialog only on first launch
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
        
        // Apply theme color to button
        val primaryColor = if (selectedCollege == COLLEGE_SIT) {
            ContextCompat.getColor(this, R.color.sit_primary)
        } else {
            ContextCompat.getColor(this, R.color.sec_primary)
        }
        btnGotIt.backgroundTintList = ColorStateList.valueOf(primaryColor)
        
        btnGotIt.setOnClickListener {
            // Mark tip as shown
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
        val btnClearCache = dialogView.findViewById<LinearLayout>(R.id.btnClearCache)
        val btnAbout = dialogView.findViewById<LinearLayout>(R.id.btnAbout)
        val btnGitHub = dialogView.findViewById<LinearLayout>(R.id.btnGitHub)
        
        // Highlight current college
        if (selectedCollege == COLLEGE_SIT) {
            btnSIT.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sit_light))
        } else {
            btnSEC.setCardBackgroundColor(ContextCompat.getColor(this, R.color.sec_light))
        }
        
        // Set legacy switch state
        switchLegacy.isChecked = useLegacySite
        
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
        val lightColor: Int
        
        if (college == COLLEGE_SIT) {
            primaryColor = ContextCompat.getColor(this, R.color.sit_primary)
            darkColor = ContextCompat.getColor(this, R.color.sit_dark)
            lightColor = ContextCompat.getColor(this, R.color.sit_light)
        } else {
            primaryColor = ContextCompat.getColor(this, R.color.sec_primary)
            darkColor = ContextCompat.getColor(this, R.color.sec_dark)
            lightColor = ContextCompat.getColor(this, R.color.sec_light)
        }
        
        toolbar.setBackgroundColor(primaryColor)
        val siteType = if (useLegacySite) " (Legacy)" else ""
        supportActionBar?.title = "EduMate Unlocker - $college$siteType"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = darkColor
            window.navigationBarColor = primaryColor
        }
        
        progressBar.progressTintList = ColorStateList.valueOf(primaryColor)
        statusText.setBackgroundColor(lightColor)
        statusText.setTextColor(darkColor)
        swipeRefresh.setColorSchemeColors(primaryColor, darkColor)
    }

    private fun saveCollegePreference() {
        prefs.edit()
            .putString(KEY_SELECTED_COLLEGE, selectedCollege)
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .putBoolean(KEY_USE_LEGACY_SITE, useLegacySite)
            .apply()
    }

    private fun loadSelectedCollege() {
        val url: String
        
        if (useLegacySite) {
            url = LEGACY_SITE_URLS[selectedCollege] ?: LEGACY_SITE_URLS[COLLEGE_SIT]!!
            webView.settings.userAgentString = defaultUserAgent
            spoofEnabled = false
        } else {
            url = NEW_SITE_URLS[selectedCollege] ?: NEW_SITE_URLS[COLLEGE_SIT]!!
            webView.settings.userAgentString = desktopUserAgent
            spoofEnabled = true
        }
        
        val siteType = if (useLegacySite) " (Legacy)" else ""
        supportActionBar?.title = "EduMate Unlocker - $selectedCollege$siteType"
        updateStatus("Loading $selectedCollege...")
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = if (useLegacySite) defaultUserAgent else desktopUserAgent
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            defaultTextEncodingName = "UTF-8"
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        WebView.setWebContentsDebuggingEnabled(true)
        webView.addJavascriptInterface(BlobDownloadInterface(), "AndroidBlobDownloader")

        webView.webViewClient = object : WebViewClient() {
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                updateStatus("Loading...")
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                
                if (!useLegacySite && spoofEnabled) {
                    view?.evaluateJavascript(desktopSpoofScript, null)
                    // Also inject CSS fix for input fields
                    view?.evaluateJavascript(inputFixCssScript, null)
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
        
        val extension = getExtensionFromMimetype(mimetype)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "download_$timestamp.$extension"
    }
    
    private fun getExtensionFromMimetype(mimetype: String?): String {
        return when {
            mimetype?.contains("pdf") == true -> "pdf"
            mimetype?.contains("png") == true -> "png"
            mimetype?.contains("jpeg") == true || mimetype?.contains("jpg") == true -> "jpg"
            mimetype?.contains("gif") == true -> "gif"
            mimetype?.contains("webp") == true -> "webp"
            mimetype?.contains("excel") == true || mimetype?.contains("spreadsheet") == true -> "xlsx"
            mimetype?.contains("word") == true || mimetype?.contains("document") == true -> "docx"
            mimetype?.contains("image") == true -> "png"
            else -> "bin"
        }
    }
    
    private fun handleDownloadViaJavaScript(url: String, filename: String, mimetype: String?) {
        updateStatus("Downloading: $filename")
        
        val escapedUrl = url.replace("'", "\\'").replace("\\", "\\\\").replace("\n", "").replace("\r", "")
        val escapedFilename = filename.replace("'", "\\'").replace("\\", "\\\\")
        
        val script = """
            (async function() {
                try {
                    AndroidBlobDownloader.onProgress('Starting download...');
                    var url = '$escapedUrl';
                    var filename = '$escapedFilename';
                    
                    if (url.startsWith('blob:')) {
                        var imgs = document.querySelectorAll('img');
                        for (var i = 0; i < imgs.length; i++) {
                            if (imgs[i].src === url) {
                                try {
                                    var canvas = document.createElement('canvas');
                                    canvas.width = imgs[i].naturalWidth || imgs[i].width || 200;
                                    canvas.height = imgs[i].naturalHeight || imgs[i].height || 200;
                                    var ctx = canvas.getContext('2d');
                                    ctx.drawImage(imgs[i], 0, 0);
                                    var dataUrl = canvas.toDataURL('image/png');
                                    if (dataUrl && dataUrl.length > 100) {
                                        AndroidBlobDownloader.onBlobReady(dataUrl, filename.replace(/\.[^.]+$/, '.png'));
                                        return;
                                    }
                                } catch(e) {}
                            }
                        }
                    }
                    
                    const response = await fetch(url, { method: 'GET', credentials: 'include' });
                    if (response.ok) {
                        const blob = await response.blob();
                        if (blob.size > 0) {
                            AndroidBlobDownloader.onProgress('Processing ' + (blob.size / 1024).toFixed(1) + ' KB...');
                            const reader = new FileReader();
                            reader.onloadend = function() {
                                if (reader.result && reader.result.length > 50) {
                                    AndroidBlobDownloader.onBlobReady(reader.result, filename);
                                } else {
                                    AndroidBlobDownloader.onBlobError('Empty response');
                                }
                            };
                            reader.readAsDataURL(blob);
                            return;
                        }
                    }
                    AndroidBlobDownloader.onBlobError('Download failed');
                } catch(e) {
                    AndroidBlobDownloader.onBlobError(e.toString());
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
    
    private fun showDownloadCompleteDialog(file: File, filename: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_complete, null)
        
        val filenameText = dialogView.findViewById<TextView>(R.id.textFilename)
        val btnOpen = dialogView.findViewById<Button>(R.id.btnOpen)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShare)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        
        filenameText.text = filename
        
        val primaryColor = if (selectedCollege == COLLEGE_SIT) {
            ContextCompat.getColor(this, R.color.sit_primary)
        } else {
            ContextCompat.getColor(this, R.color.sec_primary)
        }
        btnOpen.backgroundTintList = ColorStateList.valueOf(primaryColor)
        btnShare.backgroundTintList = ColorStateList.valueOf(primaryColor)
        
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        btnOpen.setOnClickListener {
            dialog.dismiss()
            openFile(file)
        }
        
        btnShare.setOnClickListener {
            dialog.dismiss()
            shareFile(file)
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            showToast("No app found to open this file")
        }
    }
    
    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            showToast("Failed to share file")
        }
    }
    
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "*/*"
        }
    }
    
    inner class BlobDownloadInterface {
        @JavascriptInterface
        fun onProgress(message: String) {
            runOnUiThread { updateStatus(message) }
        }
        
        @JavascriptInterface
        fun onBlobReady(base64Data: String, filename: String) {
            runOnUiThread {
                try {
                    updateStatus("Saving: $filename")
                    
                    if (base64Data.isEmpty()) {
                        showToast("Download failed: Empty data")
                        return@runOnUiThread
                    }
                    
                    val base64Content = if (base64Data.contains(",")) {
                        base64Data.substring(base64Data.indexOf(",") + 1)
                    } else {
                        base64Data
                    }
                    
                    val data = Base64.decode(base64Content, Base64.DEFAULT)
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    
                    val file = File(downloadsDir, filename)
                    FileOutputStream(file).use { it.write(data) }
                    
                    val fileSizeKB = data.size / 1024
                    updateStatus("Downloaded: $filename ($fileSizeKB KB)")
                    
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    intent.data = Uri.fromFile(file)
                    sendBroadcast(intent)
                    
                    showDownloadCompleteDialog(file, filename)
                    
                } catch (e: Exception) {
                    showToast("Download failed: ${e.message}")
                    updateStatus("Download failed")
                }
            }
        }
        
        @JavascriptInterface
        fun onBlobError(error: String) {
            runOnUiThread {
                showToast("Download failed: $error")
                updateStatus("Download failed")
            }
        }
    }

    private fun updateStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        versionText?.text = "v1.0"
        taglineText?.text = "Thank You for Trying out my App ;)"
        
        // Credit with bold "the.404guy"
        val creditString = "Cooked up by the.404guy"
        val spannableCredit = SpannableString(creditString)
        val startIndex = creditString.indexOf("the.404guy")
        val endIndex = startIndex + "the.404guy".length
        spannableCredit.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        creditText?.text = spannableCredit
        
        // Easter egg logic:
        // 5 clicks = show photo
        // 10, 15, 20... clicks = show 404 error
        easterEggClicks = 0
        
        versionText?.setOnClickListener {
            easterEggClicks++
            
            if (easterEggClicks % 5 == 0) {
                easterEggContainer?.visibility = View.VISIBLE
                
                if (easterEggClicks == 5) {
                    // First 5 clicks - show photo in circular CardView
                    easterEggCard?.visibility = View.VISIBLE
                    easterEggError?.visibility = View.GONE
                    try {
                        easterEggImage?.setImageResource(R.drawable.easter_egg_photo)
                    } catch (e: Exception) {
                        // If image not found, show placeholder
                        easterEggImage?.setImageResource(R.mipmap.ic_launcher)
                    }
                } else {
                    // 10, 15, 20... clicks - show 404 error
                    easterEggCard?.visibility = View.GONE
                    easterEggError?.visibility = View.VISIBLE
                }
                
                // Hide after 2 seconds
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

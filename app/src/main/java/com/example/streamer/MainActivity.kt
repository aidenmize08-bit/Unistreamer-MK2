package com.example.streamer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var urlBox: EditText
    private lateinit var dl: Button
    private lateinit var shieldBtn: Button
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var sniffed: String? = null
    private var allowedSite = ""
    private var shield = true
    private var ua = ""
    private var busy = false

    private val blocked = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "popads.net", "popcash.net",
        "propellerads.com", "onclickads.net", "exoclick.com", "exosrv.com",
        "juicyads.com", "trafficjunky.com", "adsterra.com", "highperformanceformat.com",
        "revcontent.com", "taboola.com", "outbrain.com", "mgid.com", "adcash.com",
        "clickadu.com", "hilltopads.net", "ad-maven.com", "admaven.com",
        "pushwoosh.com", "zeropark.com", "richpush.co", "a-ads.com", "yllix.com",
        "popunder.net", "tsyndicate.com", "trafficstars.com", "realsrv.com",
        "acscdn.com", "profitablecpmrate.com", "effectivegatecpm.com", "pubadx.one"
    )

    private val shieldJs = "(function(){window.open=function(){return null};" +
        "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[target=_blank]'):null;" +
        "if(a){e.preventDefault();e.stopPropagation();}},true);})();"

    private fun siteOf(host: String?): String {
        val p = (host ?: "").split(".")
        return if (p.size >= 2) p.takeLast(2).joinToString(".") else host ?: ""
    }

    private fun isBlocked(host: String?) =
        host != null && blocked.any { host == it || host.endsWith(".$it") }

    private lateinit var progressBar: ProgressBar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glossy(top: Int, bottom: Int, radiusDp: Int = 20): Drawable {
        val r = dp(radiusDp).toFloat()
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = r
            setStroke(dp(1), 0xCCFFFFFF.toInt())
        }
        val shine = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xB0FFFFFF.toInt(), 0x18FFFFFF)).apply {
            cornerRadii = floatArrayOf(r, r, r, r, r / 2, r / 2, r / 2, r / 2)
        }
        return LayerDrawable(arrayOf(base, shine)).apply {
            setLayerInset(1, dp(4), dp(2), dp(4), 0)
            setLayerHeight(1, dp(17))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    private fun paint(b: Button, top: Int, bottom: Int) {
        b.background = glossy(top, bottom)
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.textSize = 13f
        b.setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
        b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.stateListAnimator = null
    }

    private fun paintShield() {
        shieldBtn.text = if (shield) "Shield: ON" else "Shield: OFF"
        if (shield) paint(shieldBtn, 0xFF9BE564.toInt(), 0xFF2FA21F.toInt())
        else paint(shieldBtn, 0xFFFFC46B.toInt(), 0xFFE67E1F.toInt())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        window.statusBarColor = 0xFF3DB2F5.toInt()
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF3DB2F5.toInt(), 0xFF8EE27A.toInt()))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFB5EBFF.toInt(), 0xFF4FBBF7.toInt())).apply { setStroke(dp(1), 0x99FFFFFF.toInt()) }
        }
        urlBox = EditText(this).apply {
            hint = "Paste a link..."
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 14f
            setTextColor(0xFF124E78.toInt())
            setHintTextColor(0xFF7BA3BF.toInt())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(0xEEFFFFFF.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFF7CC4EE.toInt())
            }
        }
        val go = Button(this).apply { text = "Go" }
        dl = Button(this).apply { text = "Save" }
        shieldBtn = Button(this)
        paint(go, 0xFF9BE564.toInt(), 0xFF2FA21F.toInt())
        paint(dl, 0xFF6FD3FF.toInt(), 0xFF1B7FD6.toInt())
        paintShield()
        val bp = { LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(dp(4), 0, 0, 0) } }
        bar.addView(urlBox, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(go, bp())
        bar.addView(dl, bp())
        bar.addView(shieldBtn, bp())

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(0xFF7ED957.toInt())
            visibility = View.GONE
        }

        web = WebView(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
            clipToOutline = true
        }
        col.addView(bar)
        col.addView(progressBar, LinearLayout.LayoutParams(-1, dp(4)))
        col.addView(web, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(8), dp(6), dp(8), dp(8)) })
        root.addView(col)
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        ua = web.settings.userAgentString

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url
                if (shield && isBlocked(u.host)) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                val s = u.toString().lowercase()
                if (Regex("\\.(mp4|webm|m3u8|mpd)(\\?|$)").containsMatchIn(s)) sniffed = u.toString()
                return null
            }

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.scheme != "http" && u.scheme != "https") return true
                if (!shield) return false
                if (isBlocked(u.host)) return true
                // Strict: the main page may only navigate within the site you typed in.
                if (r.isForMainFrame && siteOf(u.host) != allowedSite) {
                    toast("Blocked redirect to ${u.host}")
                    return true
                }
                return false
            }

            override fun onPageStarted(v: WebView, url: String, f: android.graphics.Bitmap?) {
                urlBox.setText(url)
                if (shield) v.evaluateJavascript(shieldJs, null)
            }

            override fun onPageFinished(v: WebView, url: String) {
                if (shield) v.evaluateJavascript(shieldJs, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message) = false

            override fun onProgressChanged(v: WebView, p: Int) {
                progressBar.progress = p
                progressBar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                customView = view; customCb = cb
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customCb?.onCustomViewHidden()
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        web.setDownloadListener { url, _, _, mime, _ -> startDownload(url, mime) }

        go.setOnClickListener { load(urlBox.text.toString()) }
        urlBox.setOnEditorActionListener { _, _, _ -> load(urlBox.text.toString()); true }
        shieldBtn.setOnClickListener {
            shield = !shield
            paintShield()
        }
        dl.setOnClickListener {
            val s = sniffed
            when {
                s == null -> toast("No video detected yet. Press play first.")
                s.lowercase().contains(".m3u8") -> downloadHls(s, web.url)
                s.lowercase().contains(".mpd") -> toast("DASH (.mpd) downloads are not supported yet.")
                else -> startDownload(s, null)
            }
        }

        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        shared?.let { load(it) }
    }

    private fun load(input: String) {
        var u = input.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        sniffed = null
        allowedSite = siteOf(Uri.parse(u).host)
        web.loadUrl(u)
    }

    private fun ensureStorage(): Boolean {
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            toast("Allow storage access, then tap Save again.")
            return false
        }
        return true
    }

    private fun startDownload(url: String, mime: String?) {
        if (!ensureStorage()) return
        try {
            val name = URLUtil.guessFileName(url, null, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            req.addRequestHeader("User-Agent", ua)
            req.addRequestHeader("Referer", web.url ?: url)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("Downloading $name")
        } catch (e: Exception) {
            toast("Download failed: ${e.message}")
        }
    }

    // ---------- HLS (.m3u8) downloader ----------

    private class Seg(val url: String, val key: ByteArray?, val iv: ByteArray?)

    private fun open(url: String, ref: String?): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", ua)
        ref?.let { c.setRequestProperty("Referer", it) }
        CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
        return c.inputStream
    }

    private fun text(url: String, ref: String?) = open(url, ref).bufferedReader().use { it.readText() }
    private fun absUrl(base: String, rel: String) = URL(URL(base), rel).toString()
    private fun attr(line: String, name: String) =
        Regex("$name=(\"[^\"]*\"|[^,]*)").find(line)?.groupValues?.get(1)?.trim()?.trim('"')

    private fun hexIv(s: String): ByteArray {
        val h = s.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun seqIv(seq: Long) = ByteArray(16).also { b ->
        for (i in 0 until 8) b[15 - i] = (seq shr (8 * i)).toByte()
    }

    private fun openOut(name: String, mime: String): Pair<OutputStream, () -> Unit> {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            val out = contentResolver.openOutputStream(uri)!!
            return Pair(out) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        return Pair(FileOutputStream(File(dir, name))) {}
    }

    private fun downloadHls(start: String, ref: String?) {
        if (busy) { toast("A download is already running."); return }
        if (!ensureStorage()) return
        busy = true
        toast("HLS download started. Keep the app open.")
        Thread {
            var out: OutputStream? = null
            try {
                var plUrl = start
                var pl = text(plUrl, ref)

                // Master playlist: choose the highest-bandwidth variant.
                if (pl.contains("#EXT-X-STREAM-INF")) {
                    val lines = pl.lines()
                    var best = -1L
                    var bestUrl: String? = null
                    for (i in lines.indices) {
                        if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                            val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLong() ?: 0L
                            val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                            if (next != null && bw > best) { best = bw; bestUrl = next.trim() }
                        }
                    }
                    plUrl = absUrl(plUrl, bestUrl ?: throw Exception("No video variants found"))
                    pl = text(plUrl, ref)
                }

                var seq = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(pl)?.groupValues?.get(1)?.toLong() ?: 0L
                var key: ByteArray? = null
                var ivAttr: String? = null
                var initUrl: String? = null
                val segs = ArrayList<Seg>()
                for (raw in pl.lines()) {
                    val line = raw.trim()
                    when {
                        line.startsWith("#EXT-X-KEY") -> {
                            if (attr(line, "METHOD") == "AES-128") {
                                val ku = attr(line, "URI") ?: throw Exception("Key URI missing")
                                key = open(absUrl(plUrl, ku), ref).use { it.readBytes() }
                                ivAttr = attr(line, "IV")
                            } else if (attr(line, "METHOD") == "NONE") {
                                key = null; ivAttr = null
                            } else throw Exception("Unsupported encryption (DRM)")
                        }
                        line.startsWith("#EXT-X-MAP") -> initUrl = attr(line, "URI")?.let { absUrl(plUrl, it) }
                        line.isNotEmpty() && !line.startsWith("#") -> {
                            val iv = if (key != null) (ivAttr?.let { hexIv(it) } ?: seqIv(seq)) else null
                            segs.add(Seg(absUrl(plUrl, line), key, iv))
                            seq++
                        }
                    }
                }
                if (segs.isEmpty()) throw Exception("No segments found")

                val fmp4 = initUrl != null
                val name = "video_${System.currentTimeMillis()}" + if (fmp4) ".mp4" else ".ts"
                val (o, finish) = openOut(name, if (fmp4) "video/mp4" else "video/mp2t")
                out = o

                initUrl?.let { u -> open(u, ref).use { it.copyTo(o) } }

                for ((i, s) in segs.withIndex()) {
                    var data: ByteArray? = null
                    var tries = 0
                    while (data == null) {
                        try { data = open(s.url, ref).use { it.readBytes() } }
                        catch (e: Exception) { if (++tries >= 3) throw e; Thread.sleep(800) }
                    }
                    if (s.key != null && s.iv != null) {
                        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(s.key, "AES"), IvParameterSpec(s.iv))
                        data = c.doFinal(data)
                    }
                    o.write(data)
                    val pct = (i + 1) * 100 / segs.size
                    runOnUiThread { dl.text = "$pct%" }
                }
                o.flush(); o.close(); out = null
                finish()
                toast("Saved to Downloads: $name")
            } catch (e: Exception) {
                try { out?.close() } catch (_: Exception) {}
                toast("HLS download failed: ${e.message}")
            } finally {
                busy = false
                runOnUiThread { dl.text = "Save" }
            }
        }.start()
    }

    private fun toast(m: String) = runOnUiThread {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) { web.webChromeClient?.onHideCustomView(); return true }
            if (web.canGoBack()) { web.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}

package com.pess.engineering

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import android.print.PrintManager
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.webkit.WebView as AndroidWebView
import android.util.Base64
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(webView, true) }
        webView.addJavascriptInterface(PessBridge(this), "PESSAndroid")
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                if (url.scheme == "https" && (url.host == "pess-engineering-safety.netlify.app" || url.host.endsWith(".netlify.app"))) {
                    return false
                }
                if (url.scheme == "https" || url.scheme == "mailto" || url.scheme == "tel") {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, url)); true } catch (_: Exception) { true }
                }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                installNativeDownloadHook()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                filePicker.launch("application/json")
                return true
            }
        }
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setMimeType(mimeType)
                        .addRequestHeader("User-Agent", userAgent)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PESS/" + (android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)))
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "بدأ تنزيل الملف إلى مجلد Downloads/PESS", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this, "تعذر التنزيل: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        })
        if (savedInstanceState == null) webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        else webView.restoreState(savedInstanceState)
    }

    private fun installNativeDownloadHook() {
        val js = """
          (function(){ if(window.__pessNativeHooked)return; window.__pessNativeHooked=true;
            document.addEventListener('click',function(e){
              var a=e.target && e.target.closest ? e.target.closest('a[download]') : null;
              if(!a || !a.href || !a.href.startsWith('blob:')) return;
              e.preventDefault(); e.stopPropagation();
              var name=a.getAttribute('download')||'PESS-backup.json';
              fetch(a.href).then(function(r){return r.blob()}).then(function(blob){
                var reader=new FileReader(); reader.onload=function(){
                  var b64=String(reader.result).split(',')[1]||'';
                  PESSAndroid.saveBase64File(name,blob.type||'application/octet-stream',b64);
                }; reader.readAsDataURL(blob);
              }).catch(function(err){console.error('PESS native save failed',err);});
            },true);
            var oldPrint=window.print; window.print=function(){PESSAndroid.printPage();};
          })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    inner class PessBridge(private val ctx: Context) {
        @JavascriptInterface fun saveBase64File(filename: String, mime: String, base64: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val safeName = filename.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "PESS-backup.json" }
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PESS")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw Exception("تعذر إنشاء الملف")
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw Exception("تعذر كتابة الملف")
                runOnUiThread { Toast.makeText(ctx, "تم حفظ الملف في Downloads/PESS", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(ctx, "فشل حفظ الملف: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
        @JavascriptInterface fun printPage() { runOnUiThread {
            try {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter("PESS-document")
                pm.print("PESS", adapter, PrintAttributes.Builder().build())
            } catch (e: Exception) { Toast.makeText(ctx, "تعذرت الطباعة: ${e.message}", Toast.LENGTH_LONG).show() }
        } }
    }

    override fun onSaveInstanceState(outState: Bundle) { webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onBackPressed() { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onDestroy() { if (::webView.isInitialized) { webView.stopLoading(); webView.destroy() }; super.onDestroy() }
}

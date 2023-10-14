package org.lzdev.oldtv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.VideoView
import org.intellij.lang.annotations.Language


class MainActivity : Activity() {
    private val channels: ArrayList<Channel> = ArrayList()
    private var index = 0
    private lateinit var webView: WebView
    private var currentVideoURL = ""
    private lateinit var videoView: VideoView
    private var isChannelLoaded = false

    private var mWifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isNetworkConnected()) {
                webView.loadUrl("https://vtvgo.vn")
            }
        }
    }

    @Language("js")
    private val jsCode = """
        var elements = document.querySelectorAll(".list_channel>a");
        var channels_text = "";

        elements.forEach(element => {
            channels_text += element.getAttribute("alt") + ",";
            channels_text += element.getAttribute("href") + "|";
        });

        channels_text = channels_text.slice(0, channels_text.length - 1);
        channels_text;
    """.trimIndent()

    private val mOnErrorListener: MediaPlayer.OnErrorListener = MediaPlayer.OnErrorListener { _, _, _ ->
        if (isNetworkConnected()) {
            videoView.start()
        }
        true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoView = findViewById(R.id.video_view)

        WebView.setWebContentsDebuggingEnabled(true)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = getString(R.string.user_agent)
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                runOnUiThread {
                    if (request != null) {
                        val url = request.url.toString()
                        if (url.indexOf(".m3u8") > 0) {
                            if (currentVideoURL != url) {
                                Log.e(TAG, "request URL = ${request.url}")
                                currentVideoURL = url
                                videoView.setVideoURI(Uri.parse(url))
                                videoView.start()
                            }
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isChannelLoaded) {
                    view?.evaluateJavascript(jsCode) {
                        if (it != null && it.indexOf("http") >= 0) {
                            val text = it.replace("\"", "")
                            channels.clear()
                            val channelsText = text.split("|")
                            for (line in channelsText) {
                                Log.e(TAG, line)
                                channels.add(Channel(line.split(",")[0], line.split(",")[1]))
                            }
                            isChannelLoaded = true
                        }
                    }
                }
                super.onPageFinished(view, url)
            }
        }
        webView.loadUrl("https://vtvgo.vn")

        registerWifiReceiver()

        videoView.setOnErrorListener(mOnErrorListener)
        if (isNetworkConnected()) {
            webView.loadUrl("https://vtvgo.vn")
        }
    }

    private fun isNetworkConnected(): Boolean {
        var result = false
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.run {
            cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                when {
                    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        result = true
                    }

                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        result = true
                    }
                }
            }
        }
        return result
    }

    private fun registerWifiReceiver() {
        val filter = IntentFilter()
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        this.registerReceiver(mWifiReceiver, filter)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (index < channels.size - 1) {
                    index++
                } else {
                    index = 0
                }
                return playChannel()
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (index > 0) {
                    index--
                } else {
                    index = channels.size - 1
                }
                return playChannel()
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item)
                channels.forEach {
                    adapter.add(it.name)
                }
                builder.setSingleChoiceItems(adapter, index) { dialog, which ->
                    index = which
                    playChannel()
                    dialog.dismiss()
                }
                builder.show()
            }

            KeyEvent.KEYCODE_MENU -> {
                startActivityForResult(Intent(android.provider.Settings.ACTION_SETTINGS), 0)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun playChannel(): Boolean {
        if (index >= channels.size) {
            index = 0
        }
        if (channels.size == 0) {
            webView.loadUrl("https://vtvgo.vn")
        } else {
            Log.e(TAG, channels[index].url)
            webView.loadUrl(channels[index].url)
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        videoView.pause()
    }

    override fun onResume() {
        super.onResume()
        videoView.start()
    }

    companion object {
        private const val TAG = "OLDTV_MainActivity"
    }
}

class Channel(val name: String, val url: String)
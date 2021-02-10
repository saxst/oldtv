package org.lzdev.oldtv

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
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL


class MainActivity : Activity() {
    private val playlists: ArrayList<Channel> = ArrayList()
    private lateinit var sharedPref: SharedPreferences
    private var index = 0

    private var mWifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isNetworkConnected()) {
                loadChannels()
            }
        }
    }

    private val mOnErrorListener: MediaPlayer.OnErrorListener =
        MediaPlayer.OnErrorListener { _, _, _ ->
            if (isNetworkConnected()) {
                playlists.removeAt(index)
                playChannel()
                video_view.start()
            }
            true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerWifiReceiver()

        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        index = getIndex()
        video_view.setOnErrorListener(mOnErrorListener)
        video_view.start()

        if (isNetworkConnected()) {
            loadChannels()
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

    private fun loadChannels() {
        Thread {
            val url = URL(getString(R.string.m3u_url)).openConnection()
            val inputStream: InputStream = url.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            var channel = Channel()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains("#EXTINF")) {
                    channel = Channel()
                    channel.name = line.split(",").toTypedArray()[1]
                }
                if (line.contains("http")) {
                    channel.url = line
                    playlists.add(channel)
                }
            }

            inputStream.close()
            runOnUiThread {
                playChannel()
            }
        }.start()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (index < playlists.size - 1) {
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
                    index = playlists.size - 1
                }
                return playChannel()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item)
                playlists.forEach {
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
            KeyEvent.KEYCODE_BACK -> {
                loadChannels()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun playChannel(): Boolean {
        if (index >= playlists.size) {
            index = 0
        }
        saveIndex(index)
        video_view.setVideoURI(Uri.parse(playlists[index].url))
        return true
    }

    private fun saveIndex(value: Int) {
        with(sharedPref.edit()) {
            putInt("id", value)
            apply()
        }
    }

    private fun getIndex(): Int {
        return sharedPref.getInt("id", 0)
    }

    override fun onPause() {
        super.onPause()
        video_view.pause()
    }

    override fun onResume() {
        super.onResume()
        video_view.start()
    }

    companion object {
        private const val TAG = "OLDTV_MainActivity"
    }
}

class Channel {
    lateinit var name: String
    lateinit var url: String
}
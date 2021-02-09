package org.lzdev.oldtv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL

class MainActivity : Activity() {
    private val playlists: ArrayList<String> = ArrayList()
    private lateinit var sharedPref: SharedPreferences
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        index = getIndex()
        video_view.start()
        loadChannels()
    }

    private fun loadChannels() {
        Thread {
            val url = URL(getString(R.string.m3u_url)).openConnection()
            val inputStream: InputStream = url.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains("http")) {
                    val arr = line.split(" ").toTypedArray()
                    playlists.add(arr[arr.size - 1])
                }
            }

            inputStream.close()
            runOnUiThread {
                playChannel(index)
            }
        }.start()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.e(TAG, "onKeyUp $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (index < playlists.size - 1) {
                    index++
                } else {
                    index = 0
                }
                return playChannel(index)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (index > 0) {
                    index--
                } else {
                    index = playlists.size - 1
                }
                return playChannel(index)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {}
            KeyEvent.KEYCODE_DPAD_RIGHT -> {}
            KeyEvent.KEYCODE_DPAD_CENTER -> {}
            KeyEvent.KEYCODE_MENU -> {
                startActivityForResult(Intent(android.provider.Settings.ACTION_SETTINGS), 0);
            }
            KeyEvent.KEYCODE_BACK -> {
                loadChannels()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun playChannel(index: Int): Boolean {
        if (index >= playlists.size) {
            saveIndex(0)
        } else {
            saveIndex(index)
        }
        video_view.setVideoURI(Uri.parse(playlists[index]))
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
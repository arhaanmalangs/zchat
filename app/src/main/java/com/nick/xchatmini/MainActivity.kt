package com.nick.xchatmini

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val info = TextView(this).apply {
            text = "X Chat Mini\n\n" +
                "Floating window jo doosre apps ke upar rehta hai, " +
                "aur har message ka English translation neeche dikhata hai.\n\n" +
                "1) 'Permission do' dabao -> 'Display over other apps' ON karo\n" +
                "2) 'Floating window kholo' dabao\n" +
                "3) Window me X me login karke apni chat kholo"
            textSize = 15f
        }
        val permBtn = Button(this).apply {
            text = "1. Permission do"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        val startBtn = Button(this).apply {
            text = "2. Floating window kholo"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    permBtn.performClick()
                } else {
                    val i = Intent(this@MainActivity, FloatingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
                    else startService(i)
                    moveTaskToBack(true)
                }
            }
        }
        val stopBtn = Button(this).apply {
            text = "Band karo"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingService::class.java)) }
        }

        root.addView(info)
        root.addView(permBtn)
        root.addView(startBtn)
        root.addView(stopBtn)
        setContentView(root)
    }
}

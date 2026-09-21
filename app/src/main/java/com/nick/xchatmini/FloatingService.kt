package com.nick.xchatmini

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class FloatingService : android.app.Service() {

    private lateinit var wm: WindowManager
    private lateinit var panel: View
    private lateinit var bubble: View
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var badge: TextView

    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var collapsed = false
    private var translateOn = true
    private var busy = false
    private var unread = 0
    private var alphaStep = 0
    private val alphas = floatArrayOf(1f, 0.85f, 0.7f, 0.55f)

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1, buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, buildNotification())
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildPanel()
        buildBubble()
        showPanel()
        ui.postDelayed(tick, 2500)
    }

    // ---------------- notification ----------------

    private fun buildNotification(): Notification {
        val id = "xchatmini"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(id, "X Chat Mini", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, FloatingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, id)
            .setContentTitle("X Chat Mini chal raha hai")
            .setContentText("Floating chat + auto translate")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Band karo", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    // ---------------- windows ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "InflateParams")
    private fun buildPanel() {
        panel = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        web = panel.findViewById(R.id.web)
        status = panel.findViewById(R.id.status)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.loadUrl("https://x.com/messages")

        panelParams = WindowManager.LayoutParams(
            dp(320), dp(460), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(80)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // drag
        val header = panel.findViewById<View>(R.id.header)
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = panelParams.x; sy = panelParams.y; tx = e.rawX; ty = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = sx + (e.rawX - tx).toInt()
                    panelParams.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(panel, panelParams); true
                }
                else -> false
            }
        }

        // resize
        val grip = panel.findViewById<View>(R.id.grip)
        var sw = 0; var sh = 0; var gx = 0f; var gy = 0f
        grip.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sw = panelParams.width; sh = panelParams.height; gx = e.rawX; gy = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.width = max(dp(220), min(dp(900), sw + (e.rawX - gx).toInt()))
                    panelParams.height = max(dp(200), min(dp(1400), sh + (e.rawY - gy).toInt()))
                    wm.updateViewLayout(panel, panelParams); true
                }
                else -> false
            }
        }

        panel.findViewById<View>(R.id.btnClose).setOnClickListener { stopSelf() }
        panel.findViewById<View>(R.id.btnMin).setOnClickListener { collapse() }
        panel.findViewById<View>(R.id.btnReload).setOnClickListener { web.reload() }
        panel.findViewById<View>(R.id.btnBack).setOnClickListener { if (web.canGoBack()) web.goBack() }

        val trBtn = panel.findViewById<TextView>(R.id.btnTr)
        trBtn.setOnClickListener {
            translateOn = !translateOn
            trBtn.alpha = if (translateOn) 1f else 0.4f
            if (!translateOn) web.evaluateJavascript(
                "document.querySelectorAll('.xcm-tr').forEach(function(e){e.remove()})", null
            )
        }

        val opBtn = panel.findViewById<TextView>(R.id.btnOp)
        opBtn.setOnClickListener {
            alphaStep = (alphaStep + 1) % alphas.size
            panelParams.alpha = alphas[alphaStep]
            opBtn.text = "${(alphas[alphaStep] * 100).toInt()}%"
            wm.updateViewLayout(panel, panelParams)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun buildBubble() {
        bubble = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        badge = bubble.findViewById(R.id.badge)
        bubbleParams = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(140)
        }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = bubbleParams.x; sy = bubbleParams.y; tx = e.rawX; ty = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                    bubbleParams.x = sx + dx; bubbleParams.y = sy + dy
                    wm.updateViewLayout(bubble, bubbleParams); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) expand(); true }
                else -> false
            }
        }
    }

    private fun showPanel() {
        try { wm.addView(panel, panelParams) } catch (e: Exception) { stopSelf() }
    }

    private fun collapse() {
        if (collapsed) return
        collapsed = true
        unread = 0
        badge.visibility = View.GONE
        try { wm.removeView(panel) } catch (_: Exception) {}
        try { wm.addView(bubble, bubbleParams) } catch (_: Exception) {}
    }

    private fun expand() {
        if (!collapsed) return
        collapsed = false
        unread = 0
        badge.visibility = View.GONE
        try { wm.removeView(bubble) } catch (_: Exception) {}
        try { wm.addView(panel, panelParams) } catch (_: Exception) {}
    }

    // ---------------- translation loop ----------------

    private val collectJs = """
      (function(){
        try {
          if (!document.getElementById('xcm-style')) {
            var st=document.createElement('style'); st.id='xcm-style';
            st.textContent='.xcm-tr{font-size:13px;line-height:1.35;margin-top:3px;padding-left:6px;'
              +'border-left:2px solid #1d9bf0;color:#7fd3ff;white-space:pre-wrap;}';
            (document.head||document.documentElement).appendChild(st);
          }
          if(!window.__xcmN) window.__xcmN=0;
          var out=[], nodes=document.querySelectorAll('[data-testid="tweetText"], div[dir], span[dir]');
          for (var i=0;i<nodes.length;i++){
            var n=nodes[i];
            if(n.dataset.xcmId) continue;
            if(n.querySelector('[dir],[data-testid="tweetText"]')) continue;
            if(n.closest('nav,header,[role="navigation"],[role="textbox"],[contenteditable="true"]')) continue;
            if(n.classList.contains('xcm-tr')) continue;
            var t=(n.innerText||'').trim();
            if(t.length<2||t.length>800) continue;
            var r=n.getBoundingClientRect();
            if(r.bottom< -300 || r.top > window.innerHeight+300) continue;
            var ascii=!/[^\x00-\x7F]/.test(t);
            if(ascii && t.length<40) continue;
            if(/^[\d\s:.,\/APM-]+${'$'}/i.test(t)) continue;
            n.dataset.xcmId=String(++window.__xcmN);
            out.push({id:n.dataset.xcmId,text:t});
            if(out.length>=8) break;
          }
          return JSON.stringify(out);
        } catch(e){ return '[]'; }
      })()
    """.trimIndent()

    private val tick = object : Runnable {
        override fun run() {
            if (!translateOn || busy) { ui.postDelayed(this, 2000); return }
            busy = true
            web.evaluateJavascript(collectJs) { raw ->
                val items = parseItems(raw)
                if (items.isEmpty()) {
                    busy = false
                    ui.postDelayed(this, 2000)
                } else {
                    io.execute {
                        val done = JSONArray()
                        val failed = JSONArray()
                        for (it in items) {
                            val out = Translator.translate(it.second)
                            if (out.isBlank() || out.trim().equals(it.second.trim(), true)) {
                                if (out.isBlank()) failed.put(it.first)
                            } else {
                                done.put(JSONObject().put("id", it.first).put("text", out))
                            }
                        }
                        ui.post {
                            if (done.length() > 0) {
                                web.evaluateJavascript(injectJs(done), null)
                                if (collapsed) {
                                    unread += done.length()
                                    badge.text = if (unread > 9) "9+" else unread.toString()
                                    badge.visibility = View.VISIBLE
                                }
                            }
                            if (failed.length() > 0) web.evaluateJavascript(unmarkJs(failed), null)
                            status.text = Translator.lastEngine
                            busy = false
                            ui.postDelayed(this, if (failed.length() >= 5) 12000 else 2000)
                        }
                    }
                }
            }
        }
    }

    private fun parseItems(raw: String?): List<Pair<String, String>> {
        if (raw == null) return emptyList()
        return try {
            // evaluateJavascript ek JSON string deta hai -> pehle unwrap
            val inner = if (raw.startsWith("\"")) JSONObject("{\"v\":$raw}").getString("v") else raw
            val arr = JSONArray(inner)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.getString("id") to o.getString("text")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun injectJs(list: JSONArray) = """
      (function(list){
        list.forEach(function(it){
          var el=document.querySelector('[data-xcm-id="'+it.id+'"]');
          if(!el) return;
          if(el.nextElementSibling && el.nextElementSibling.classList.contains('xcm-tr')) return;
          var d=document.createElement('div'); d.className='xcm-tr'; d.textContent=it.text;
          try{ el.parentNode.insertBefore(d, el.nextSibling); }catch(e){}
        });
      })($list)
    """.trimIndent()

    private fun unmarkJs(ids: JSONArray) = """
      (function(ids){
        ids.forEach(function(id){
          var el=document.querySelector('[data-xcm-id="'+id+'"]');
          if(el) delete el.dataset.xcmId;
        });
      })($ids)
    """.trimIndent()

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        try { wm.removeView(panel) } catch (_: Exception) {}
        try { wm.removeView(bubble) } catch (_: Exception) {}
        io.shutdownNow()
    }
}

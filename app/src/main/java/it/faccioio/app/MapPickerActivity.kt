package it.faccioio.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal const val EXTRA_MAP_LATITUDE = "map_latitude"
internal const val EXTRA_MAP_LONGITUDE = "map_longitude"

class MapPickerActivity : Activity() {
    private var selectedLatitude = Double.NaN
    private var selectedLongitude = Double.NaN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedLatitude = intent.getDoubleExtra(EXTRA_MAP_LATITUDE, Double.NaN)
        selectedLongitude = intent.getDoubleExtra(EXTRA_MAP_LONGITUDE, Double.NaN)
        if (!selectedLatitude.isFinite() || !selectedLongitude.isFinite()) {
            finish()
            return
        }

        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val instructions = TextView(this).apply {
            text = "Tocca il punto esatto oppure trascina il segnaposto"
            textSize = 16f
            setTextColor(Color.rgb(25, 43, 55))
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            gravity = Gravity.CENTER
        }
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(MapBridge(), "AndroidMap")
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        }
        val cancel = Button(this).apply {
            text = "Annulla"
            setOnClickListener { finish() }
        }
        val confirm = Button(this).apply {
            text = "Usa questo punto"
            setOnClickListener {
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra(EXTRA_MAP_LATITUDE, selectedLatitude)
                        putExtra(EXTRA_MAP_LONGITUDE, selectedLongitude)
                    }
                )
                finish()
            }
        }
        buttons.addView(cancel)
        buttons.addView(confirm)
        layout.addView(instructions)
        layout.addView(webView)
        layout.addView(buttons)
        setContentView(layout)

        webView.loadDataWithBaseURL(
            "https://www.openstreetmap.org/",
            mapHtml(selectedLatitude, selectedLongitude),
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onDestroy() {
        (findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) as? ViewGroup)
            ?.let { root ->
                fun destroyWebViews(group: ViewGroup) {
                    for (index in 0 until group.childCount) {
                        when (val child = group.getChildAt(index)) {
                            is WebView -> child.destroy()
                            is ViewGroup -> destroyWebViews(child)
                        }
                    }
                }
                destroyWebViews(root)
            }
        super.onDestroy()
    }

    private inner class MapBridge {
        @JavascriptInterface
        fun updatePosition(latitude: Double, longitude: Double) {
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                selectedLatitude = latitude
                selectedLongitude = longitude
            }
        }
    }
}

private fun mapHtml(latitude: Double, longitude: Double): String = """
    <!doctype html>
    <html lang="it">
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
      <style>html,body,#map{height:100%;margin:0} .leaflet-control-attribution{font-size:10px}</style>
    </head>
    <body>
      <div id="map"></div>
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
      <script>
        const initial = [$latitude, $longitude];
        const map = L.map('map').setView(initial, 17);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '&copy; OpenStreetMap contributors'
        }).addTo(map);
        const marker = L.marker(initial, {draggable:true}).addTo(map);
        function choose(latlng) {
          marker.setLatLng(latlng);
          AndroidMap.updatePosition(latlng.lat, latlng.lng);
        }
        map.on('click', event => choose(event.latlng));
        marker.on('dragend', event => choose(event.target.getLatLng()));
      </script>
    </body>
    </html>
""".trimIndent()


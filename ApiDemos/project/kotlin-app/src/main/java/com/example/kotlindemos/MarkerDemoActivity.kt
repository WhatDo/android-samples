/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.kotlindemos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.common_ui.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener
import com.google.android.gms.maps.GoogleMap.OnInfoWindowCloseListener
import com.google.android.gms.maps.GoogleMap.OnInfoWindowLongClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.ArrayList
import java.util.Random

/**
 * This shows how to place markers on a map.
 */
// [START maps_android_sample_marker]
class MarkerDemoActivity :
        SamplesBaseActivity(),
        OnMarkerClickListener,
        OnInfoWindowClickListener,
        OnMarkerDragListener,
        OnInfoWindowLongClickListener,
        OnInfoWindowCloseListener,
        OnMapAndViewReadyListener.OnGlobalLayoutAndMapReadyListener {

    private val TAG = MarkerDemoActivity::class.java.name

    /** This is ok to be lateinit as it is initialised in onMapReady */
    private lateinit var map: GoogleMap

    /**
     * Keeps track of the last selected marker (though it may no longer be selected).  This is
     * useful for refreshing the info window.
     *
     * Must be nullable as it is null when no marker has been selected
     */
    private var lastSelectedMarker: Marker? = null

    private val markerRainbow = ArrayList<Marker>()

    /** map to store place names and locations */
    private val places = mapOf(
            "BRISBANE" to LatLng(-27.47093, 153.0235),
            "MELBOURNE" to LatLng(-37.81319, 144.96298),
            "DARWIN" to LatLng(-12.4634, 130.8456),
            "SYDNEY" to LatLng(-33.87365, 151.20689),
            "ADELAIDE" to LatLng(-34.92873, 138.59995),
            "PERTH" to LatLng(-31.952854, 115.857342),
            "ALICE_SPRINGS" to LatLng(-24.6980, 133.8807)
    )

    /** These can be lateinit as they are set in onCreate */
    private lateinit var topText: TextView
    private lateinit var rotationBar: SeekBar
    private lateinit var flatBox: CheckBox
    private lateinit var options: RadioGroup

    private val random = Random()

    /** Demonstrates customizing the info window and/or its contents.  */
    internal inner class CustomInfoWindowAdapter : InfoWindowAdapter {

        // These are both view groups containing an ImageView with id "badge" and two
        // TextViews with id "title" and "snippet".
        private val window: View = layoutInflater.inflate(R.layout.custom_info_window, null)
        private val contents: View = layoutInflater.inflate(R.layout.custom_info_contents, null)

        override fun getInfoWindow(marker: Marker): View? {
            if (options.checkedRadioButtonId != R.id.custom_info_window) {
                // This means that getInfoContents will be called.
                return null
            }
            render(marker, window)
            return window
        }

        override fun getInfoContents(marker: Marker): View? {
            if (options.checkedRadioButtonId != R.id.custom_info_contents) {
                // This means that the default info contents will be used.
                return null
            }
            render(marker, contents)
            return contents
        }

        private fun render(marker: Marker, view: View) {
            val badge = when (marker.title!!) {
                "Brisbane" -> R.drawable.badge_qld
                "Adelaide" -> R.drawable.badge_sa
                "Sydney" -> R.drawable.badge_nsw
                "Melbourne" -> R.drawable.badge_victoria
                "Perth" -> R.drawable.badge_wa
                in "Darwin Marker 1".."Darwin Marker 4" -> R.drawable.badge_nt
                else -> 0 // Passing 0 to setImageResource will clear the image view.
            }

            view.findViewById<ImageView>(R.id.badge).setImageResource(badge)

            // Set the title and snippet for the custom info window
            val title: String? = marker.title
            val titleUi = view.findViewById<TextView>(R.id.title)

            if (title != null) {
                // Spannable string allows us to edit the formatting of the text.
                titleUi.text = SpannableString(title).apply {
                    setSpan(ForegroundColorSpan(Color.RED), 0, length, 0)
                }
            } else {
                titleUi.text = ""
            }

            val snippet: String? = marker.snippet
            val snippetUi = view.findViewById<TextView>(R.id.snippet)
            if (snippet != null && snippet.length > 12) {
                snippetUi.text = SpannableString(snippet).apply {
                    setSpan(ForegroundColorSpan(Color.MAGENTA), 0, 10, 0)
                    setSpan(ForegroundColorSpan(Color.BLUE), 12, snippet.length, 0)
                }
            } else {
                snippetUi.text = ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.marker_demo)

        topText = findViewById(R.id.top_text)

        rotationBar = findViewById<SeekBar>(R.id.rotationSeekBar).apply {
            max = 360
            setOnSeekBarChangeListener(object: OnSeekBarChangeListener {

                /** Called when the Rotation progress bar is moved */
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rotation = seekBar?.progress?.toFloat()
                    checkReadyThen { markerRainbow.map { it.rotation = rotation ?: 0f } }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                    // do nothing
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    //do nothing
                }

            } )
        }

        flatBox = findViewById(R.id.flat)

        options = findViewById<RadioGroup>(R.id.custom_info_window_options).apply {
            setOnCheckedChangeListener { _, _ ->
                if (lastSelectedMarker?.isInfoWindowShown == true) {
                    // Refresh the info window when the info window's content has changed.
                    // must deal with the possibility that lastSelectedMarker has changed in
                    // another thread between the null check and this line, do this with !!
                    lastSelectedMarker?.showInfoWindow()
                }
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        OnMapAndViewReadyListener(mapFragment, this)
        applyInsets(findViewById<View?>(R.id.map_container))
    }

    /**
     * This is the callback that is triggered when the GoogleMap has loaded and is ready for use
     */
    override fun onMapReady(googleMap: GoogleMap?) {

        // return early if the map was not initialised properly
        map = googleMap ?: return

        // create bounds that encompass every location we reference
        val boundsBuilder = LatLngBounds.Builder()
        // include all places we have markers for on the map
        places.keys.map { place -> boundsBuilder.include(places.getValue(place)) }
        val bounds = boundsBuilder.build()

        with(map) {
            // Hide the zoom controls as the button panel will cover it.
            uiSettings.isZoomControlsEnabled = false

            // Setting an info window adapter allows us to change the both the contents and
            // look of the info window.
            setInfoWindowAdapter(CustomInfoWindowAdapter())

            // Set listeners for marker events.  See the bottom of this class for their behavior.
            setOnMarkerClickListener(this@MarkerDemoActivity)
            setOnInfoWindowClickListener(this@MarkerDemoActivity)
            setOnMarkerDragListener(this@MarkerDemoActivity)
            setOnInfoWindowCloseListener(this@MarkerDemoActivity)
            setOnInfoWindowLongClickListener(this@MarkerDemoActivity)

            // Override the default content description on the view, for accessibility mode.
            // Ideally this string would be localised.
            setContentDescription("Map with lots of markers.")

            moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
        }

        // Add lots of markers to the googleMap.
        addMarkersToMap()

    }

    /**
     * Show all the specified markers on the map
     */
    private fun addMarkersToMap() {

        val placeDetailsMap = mutableMapOf(
                // Uses a coloured icon
                "BRISBANE" to PlaceDetails(
                        position = places.getValue("BRISBANE"),
                        title = "Brisbane",
                        snippet = "Population: 2,074,200",
                        icon = BitmapDescriptorFactory
                                .defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                ),

                // Uses a custom icon with the info window popping out of the center of the icon.
                "SYDNEY" to PlaceDetails(
                        position = places.getValue("SYDNEY"),
                        title = "Sydney",
                        snippet = "Population: 4,627,300",
                        icon = BitmapDescriptorFactory.fromResource(R.drawable.arrow),
                        infoWindowAnchorX = 0.5f,
                        infoWindowAnchorY = 0.5f
                ),

                // Will create a draggable marker. Long press to drag.
                "MELBOURNE" to PlaceDetails(
                        position = places.getValue("MELBOURNE"),
                        title = "Melbourne",
                        snippet = "Population: 4,137,400",
                        draggable = true
                ),

                // Use a vector drawable resource as a marker icon.
                "ALICE_SPRINGS" to PlaceDetails(
                        position = places.getValue("ALICE_SPRINGS"),
                        title = "Alice Springs",
                        icon = vectorToBitmap(
                                R.drawable.ic_android, Color.parseColor("#A4C639"))
                ),

                // More markers for good measure
                "PERTH" to PlaceDetails(
                        position = places.getValue("PERTH"),
                        title = "Perth",
                        snippet = "Population: 1,738,800"
                ),

                "ADELAIDE" to PlaceDetails(
                        position = places.getValue("ADELAIDE"),
                        title = "Adelaide",
                        snippet = "Population: 1,213,000"
                )

        )

        // add 4 markers on top of each other in Darwin with varying z-indexes
        (0 until 4).map {
            placeDetailsMap.put(
                "DARWIN ${it + 1}", PlaceDetails(
                    position = places.getValue("DARWIN"),
                    title = "Darwin Marker ${it + 1}",
                    snippet = "z-index initially ${it + 1}",
                    zIndex = it.toFloat()
                )
            )
        }

        // place markers for each of the defined locations
        placeDetailsMap.keys.map {
            with(placeDetailsMap.getValue(it)) {
                map.addMarker(MarkerOptions()
                        .position(position)
                        .title(title)
                        .snippet(snippet)
                        .icon(icon)
                        .infoWindowAnchor(infoWindowAnchorX, infoWindowAnchorY)
                        .draggable(draggable)
                        .zIndex(zIndex))

            }
        }

        // Creates a marker rainbow demonstrating how to create default marker icons of different
        // hues (colors).
        val numMarkersInRainbow = 12
        (0 until numMarkersInRainbow).mapTo(markerRainbow) {
            map.addMarker(MarkerOptions().apply{
                position(LatLng(
                    -30 + 10 * Math.sin(it * Math.PI / (numMarkersInRainbow - 1)),
                    135 - 10 * Math.cos(it * Math.PI / (numMarkersInRainbow - 1))))
                title("Marker $it")
                icon(BitmapDescriptorFactory.defaultMarker((it * 360 / numMarkersInRainbow)
                                                               .toFloat()))
                flat(flatBox.isChecked)
                rotation(rotationBar.progress.toFloat())
            })!!
        }
    }

    /**
     * Demonstrates converting a [Drawable] to a [BitmapDescriptor],
     * for use as a marker icon.
     */
    private fun vectorToBitmap(@DrawableRes id : Int, @ColorInt color : Int): BitmapDescriptor {
        val vectorDrawable: Drawable? = ResourcesCompat.getDrawable(resources, id, null)
        if (vectorDrawable == null) {
            Log.e(TAG, "Resource not found")
            return BitmapDescriptorFactory.defaultMarker()
        }
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        DrawableCompat.setTint(vectorDrawable, color)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** Called when the Clear button is clicked.  */
    @Suppress("UNUSED_PARAMETER")
    fun onClearMap(view: View) {
        checkReadyThen { map.clear() }
    }

    /** Called when the Reset button is clicked.  */
    @Suppress("UNUSED_PARAMETER")
    fun onResetMap(view: View) {
        checkReadyThen {
            map.clear()
            addMarkersToMap()
        }
    }

    /** Called when the Flat check box is checked or unchecked */
    @Suppress("UNUSED_PARAMETER")
    fun onToggleFlat(view: View) {
        checkReadyThen { markerRainbow.map { marker -> marker.isFlat = flatBox.isChecked } }
    }

    //
    // Marker related listeners.
    //
    override fun onMarkerClick(marker : Marker): Boolean {

        // Markers have a z-index that is settable and gettable.
        marker.zIndex += 1.0f
        Toast.makeText(this, "${marker.title} z-index set to ${marker.zIndex}",
                Toast.LENGTH_SHORT).show()

        lastSelectedMarker = marker

        if (marker.position == places.getValue("PERTH")) {
            // This causes the marker at Perth to bounce into position when it is clicked.
            val handler = Handler()
            val start = SystemClock.uptimeMillis()
            val duration = 1500

            val interpolator = BounceInterpolator()

            handler.post(object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val t = Math.max(
                            1 - interpolator.getInterpolation(elapsed.toFloat() / duration), 0f)
                    marker.setAnchor(0.5f, 1.0f + 2 * t)

                    // Post again 16ms later.
                    if (t > 0.0) {
                        handler.postDelayed(this, 16)
                    }
                }
            })
        } else if (marker.position == places.getValue("ADELAIDE")) {
            // This causes the marker at Adelaide to change color and alpha.
            marker.apply {
                setIcon(BitmapDescriptorFactory.defaultMarker(random.nextFloat() * 360))
                alpha = random.nextFloat()
            }
        }

        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false
    }

    override fun onInfoWindowClick(marker : Marker) {
        Toast.makeText(this, "Click Info Window", Toast.LENGTH_SHORT).show()
    }

    override fun onInfoWindowClose(marker : Marker) {
        Toast.makeText(this, "Close Info Window", Toast.LENGTH_SHORT).show()
    }

    override fun onInfoWindowLongClick(marker : Marker) {
        Toast.makeText(this, "Info Window long click", Toast.LENGTH_SHORT).show()
    }

    override fun onMarkerDragStart(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag_start)
    }

    override fun onMarkerDragEnd(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag_end)
    }

    override fun onMarkerDrag(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag, marker.position.latitude, marker.position.longitude)
    }

    /**
     * Checks if the map is ready, the executes the provided lambda function
     *
     * @param stuffToDo the code to be executed if the map is ready
     */
    private fun checkReadyThen(stuffToDo : () -> Unit) {
        if (!::map.isInitialized) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show()
        } else {
            stuffToDo()
        }
    }
}
// [END maps_android_sample_marker]

/**
 * This stores the details of a place that used to draw a marker
 */
class PlaceDetails(
        val position: LatLng,
        val title: String = "Marker",
        val snippet: String? = null,
        val icon: BitmapDescriptor = BitmapDescriptorFactory.defaultMarker(),
        val infoWindowAnchorX: Float = 0.5F,
        val infoWindowAnchorY: Float = 0F,
        val draggable: Boolean = false,
        val zIndex: Float = 0F)
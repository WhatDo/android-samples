/**
 * DO NOT EDIT THIS FILE.
 *
 * This source code was autogenerated from source code within the `app/src/gms` directory
 * and is not intended for modifications. If any edits should be made, please do so in the
 * corresponding file under the `app/src/gms` directory.
 */
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

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.maps.StreetViewPanorama
import com.google.android.libraries.maps.SupportStreetViewPanoramaFragment
import com.google.android.libraries.maps.model.LatLng
import com.google.android.libraries.maps.model.StreetViewPanoramaCamera
import com.google.android.libraries.maps.model.StreetViewPanoramaLink

/**
 * This shows how to create an activity with access to all the options in Panorama
 * which can be adjusted dynamically.
 */
class StreetViewPanoramaNavigationDemoActivity : AppCompatActivity() {

    // George St, Sydney
    private val sydney = LatLng(-33.87365, 151.20689)
    // Cole St, San Francisco
    private val sanFrancisco = LatLng(37.769263, -122.450727)
    // Santorini, Greece using the Pano ID of the location instead of a LatLng object
    private val santoriniPanoId = "WddsUw1geEoAAAQIt9RnsQ"
    // LatLng with no panorama
    private val invalid = LatLng(-45.125783, 151.276417)

    // The amount in degrees by which to scroll the camera
    private val PAN_BY_DEGREES = 30
    // The amount of zoom
    private val ZOOM_BY = 0.5f

    private lateinit var streetViewPanorama: StreetViewPanorama
    private lateinit var customDurationBar: SeekBar

    private val duration: Long
        get() = customDurationBar.progress.toLong()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.common_ui.R.layout.activity_street_view_panorama_navigation_demo)

        val streetViewPanoramaFragment = supportFragmentManager
                .findFragmentById(com.example.common_ui.R.id.streetviewpanorama) as SupportStreetViewPanoramaFragment
        streetViewPanoramaFragment.getStreetViewPanoramaAsync { panorama ->
            streetViewPanorama = panorama
            // Only set the panorama to sydney on startup (when no panoramas have been
            // loaded which is when the savedInstanceState is null).
            if (savedInstanceState == null) {
                streetViewPanorama.setPosition(sydney)
            }
        }

        customDurationBar = findViewById(com.example.common_ui.R.id.duration_bar)
    }

    /**
     * Checks if the map is ready, the executes the provided lamdba function
     *
     * @param stuffToDo the code to be executed if [streetViewPanorama] is ready
     */
    private fun checkReadyThen(stuffToDo : () -> Unit) {
        if (!::streetViewPanorama.isInitialized) {
            Toast.makeText(this, com.example.common_ui.R.string.map_not_ready, Toast.LENGTH_SHORT).show()
        } else {
            stuffToDo()
        }
    }

    /**
     * Called when the Go To Location button is pressed
     */
    fun onGoToLocation(view: View) {
        when (view.id) {
            com.example.common_ui.R.id.sydney -> streetViewPanorama.setPosition(sydney)
            com.example.common_ui.R.id.sanfrancisco -> streetViewPanorama.setPosition(sanFrancisco)
            com.example.common_ui.R.id.santorini -> streetViewPanorama.setPosition(santoriniPanoId)
            com.example.common_ui.R.id.invalid -> streetViewPanorama.setPosition(invalid)
        }
    }

    /**
     * This function is called from the listeners, and builds the street view panorama with the
     * specified arguments.
     */
    private fun updateStreetViewPanorama(zoom: Float, tilt: Float, bearing: Float) {
        streetViewPanorama.animateTo(StreetViewPanoramaCamera.Builder().apply {
            zoom(zoom)
            tilt(tilt)
            bearing(bearing)
        } .build(), duration)
    }

    fun onButtonClicked(view: View) {
        checkReadyThen {
            with(streetViewPanorama.panoramaCamera) {
                when (view.id) {
                    com.example.common_ui.R.id.zoom_in -> updateStreetViewPanorama(zoom + ZOOM_BY, tilt, bearing)
                    com.example.common_ui.R.id.zoom_out -> updateStreetViewPanorama(zoom - ZOOM_BY, tilt, bearing)
                    com.example.common_ui.R.id.pan_left -> updateStreetViewPanorama(zoom, tilt, bearing - PAN_BY_DEGREES)
                    com.example.common_ui.R.id.pan_right -> updateStreetViewPanorama(zoom, tilt, bearing + PAN_BY_DEGREES)
                    com.example.common_ui.R.id.pan_up -> {
                        var newTilt = tilt + PAN_BY_DEGREES
                        if (newTilt > 90) newTilt = 90f
                        updateStreetViewPanorama(zoom, newTilt, bearing)
                    }
                    com.example.common_ui.R.id.pan_down -> {
                        var newTilt = tilt - PAN_BY_DEGREES
                        if (newTilt < -90) newTilt = -90f
                        updateStreetViewPanorama(zoom, newTilt, bearing)
                    }
                }
            }
        }
    }

    fun onRequestPosition(view: View) {
        checkReadyThen {
            streetViewPanorama.location?.let {
                Toast.makeText(view.context, streetViewPanorama.location.position.toString(),
                        Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onMovePosition(view: View) {
        val location = streetViewPanorama.location
        val camera = streetViewPanorama.panoramaCamera
        location?.links?.let {
            val link = location.links.findClosestLinkToBearing(camera.bearing)
            streetViewPanorama.setPosition(link.panoId)
        }
    }

    /** Extension function to find the closest link from a point. */
    private fun Array<StreetViewPanoramaLink>.findClosestLinkToBearing(
            bearing: Float
    ): StreetViewPanoramaLink {

        // Find the difference between angle a and b as a value between 0 and 180.
        val findNormalizedDifference = fun (a: Float, b: Float): Float {
            val diff = a - b
            val normalizedDiff = diff - (360 * Math.floor((diff / 360.0f).toDouble())).toFloat()
            return if ((normalizedDiff < 180.0f)) normalizedDiff else 360.0f - normalizedDiff
        }

        var minBearingDiff = 360f
        var closestLink = this[0]
        for (link in this) {
            if (minBearingDiff > findNormalizedDifference(bearing, link.bearing)) {
                minBearingDiff = findNormalizedDifference(bearing, link.bearing)
                closestLink = link
            }
        }
        return closestLink
    }
}

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

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment

/**
 * Helper class that will delay triggering the OnMapReady callback until both the GoogleMap and the
 * View having completed initialization. This is only necessary if a developer wishes to immediately
 * invoke any method on the GoogleMap that also requires the View to have finished layout
 * (ie. anything that needs to know the View's true size like snapshotting).
 */
class OnMapAndViewReadyListener(
        private val mapFragment: SupportMapFragment,
        private val toBeNotified: OnGlobalLayoutAndMapReadyListener
) : OnGlobalLayoutListener,
        OnMapReadyCallback {

    private val mapView: View? = mapFragment.view

    private var isViewReady = false
    private var isMapReady = false
    private var map: GoogleMap? = null

    /** A listener that needs to wait for both the GoogleMap and the View to be initialized.  */
    interface OnGlobalLayoutAndMapReadyListener {
        fun onMapReady(googleMap: GoogleMap?)
    }

    init {
        registerListeners()
    }

    private fun registerListeners() {
        // View layout.
        mapView?.let {
            if (it.width != 0 && it.height != 0) {
                // View has already completed layout.
                isViewReady = true
            } else {
                // Map has not undergone layout, register a View observer.
                it.viewTreeObserver.addOnGlobalLayoutListener(this)
            }
        }
        // GoogleMap. Note if the GoogleMap is already ready it will still fire the callback later.
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        // NOTE: The GoogleMap API specifies the listener is removed just prior to invocation.
        map = googleMap
        isMapReady = true
        fireCallbackIfReady()
    }

    // We use the new method when supported
    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")  // We check which build version we are using.
    override fun onGlobalLayout() {
        // Remove our listener.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            mapView?.viewTreeObserver?.removeGlobalOnLayoutListener(this)
        } else {
            mapView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
        }
        isViewReady = true
        fireCallbackIfReady()
    }

    private fun fireCallbackIfReady() {
        if (isViewReady && isMapReady) {
            toBeNotified.onMapReady(map)
        }
    }
}

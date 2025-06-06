// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.example.kotlindemos

import android.os.Bundle
import android.view.View
import com.example.common_ui.R

import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng

/**
 * This shows how to create a simple activity with streetview
 */
class StreetViewPanoramaBasicDemoActivity : SamplesBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_view_panorama_basic_demo)

        val streetViewPanoramaFragment =
            supportFragmentManager.findFragmentById(R.id.streetviewpanorama) as SupportStreetViewPanoramaFragment?

        streetViewPanoramaFragment?.getStreetViewPanoramaAsync { panorama ->
            // Only set the panorama to SYDNEY on startup (when no panoramas have been
            // loaded which is when the savedInstanceState is null).
            savedInstanceState ?: panorama.setPosition(SYDNEY)
        }
        applyInsets(findViewById<View?>(R.id.map_container))
    }

    companion object {
        // George St, Sydney
        private val SYDNEY = LatLng(-33.87365, 151.20689)
    }
}
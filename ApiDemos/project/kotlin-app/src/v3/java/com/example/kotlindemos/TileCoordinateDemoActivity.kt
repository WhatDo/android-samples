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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.maps.GoogleMap
import com.google.android.libraries.maps.OnMapReadyCallback
import com.google.android.libraries.maps.SupportMapFragment
import com.google.android.libraries.maps.model.Tile
import com.google.android.libraries.maps.model.TileOverlayOptions
import com.google.android.libraries.maps.model.TileProvider
import java.io.ByteArrayOutputStream

/**
 * This demonstrates tile overlay coordinates.
 */
class TileCoordinateDemoActivity : AppCompatActivity(), OnMapReadyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.common_ui.R.layout.tile_coordinate_demo)
        val mapFragment = supportFragmentManager.findFragmentById(com.example.common_ui.R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        val coordTileProvider: TileProvider = CoordTileProvider(this.applicationContext)
        map.addTileOverlay(TileOverlayOptions().tileProvider(coordTileProvider))
    }

    private class CoordTileProvider(context: Context) : TileProvider {
        private val scaleFactor: Float
        private val borderTile: Bitmap
        override fun getTile(x: Int, y: Int, zoom: Int): Tile {
            val coordTile = drawTileCoords(x, y, zoom)
            val stream = ByteArrayOutputStream()
            coordTile!!.compress(Bitmap.CompressFormat.PNG, 0, stream)
            val bitmapData = stream.toByteArray()
            return Tile((TILE_SIZE_DP * scaleFactor).toInt(),
                (TILE_SIZE_DP * scaleFactor).toInt(), bitmapData)
        }

        private fun drawTileCoords(x: Int, y: Int, zoom: Int): Bitmap? {
            // Synchronize copying the bitmap to avoid a race condition in some devices.
            var copy: Bitmap? = null
            synchronized(borderTile) { copy = borderTile.copy(Bitmap.Config.ARGB_8888, true) }
            val canvas = Canvas(copy!!)
            val tileCoords = "($x, $y)"
            val zoomLevel = "zoom = $zoom"
            /* Paint is not thread safe. */
            val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mTextPaint.textAlign = Paint.Align.CENTER
            mTextPaint.textSize = 18 * scaleFactor
            canvas.drawText(tileCoords, TILE_SIZE_DP * scaleFactor / 2,
                TILE_SIZE_DP * scaleFactor / 2, mTextPaint)
            canvas.drawText(zoomLevel, TILE_SIZE_DP * scaleFactor / 2,
                TILE_SIZE_DP * scaleFactor * 2 / 3, mTextPaint)
            return copy
        }

        companion object {
            private const val TILE_SIZE_DP = 256
        }

        init {
            /* Scale factor based on density, with a 0.6 multiplier to increase tile generation
             * speed */
            scaleFactor = context.resources.displayMetrics.density * 0.6f
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            borderPaint.style = Paint.Style.STROKE
            borderTile = Bitmap.createBitmap((TILE_SIZE_DP * scaleFactor).toInt(),
                (TILE_SIZE_DP * scaleFactor).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(borderTile)
            canvas.drawRect(0f, 0f, TILE_SIZE_DP * scaleFactor, TILE_SIZE_DP * scaleFactor,
                borderPaint)
        }
    }
}

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

package com.google.maps.example;

//[START maps_android_mapsactivity]

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int ANIMATION_COUNT = 800;

    private GoogleMap mMap;

    private CameraPosition lastCameraPosition;
    private int duplicatedPositionCount = 0;

    private boolean emulateWork = true;

    private ViewGroup root;
    private final List<View> views = new ArrayList<>();
    private final Random random = new Random();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // add some views
        root = findViewById(R.id.root);
        for (int i = 0; i < ANIMATION_COUNT; i++) {
            // add some views
            View view = new View(this);
            int dimension = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            view.setLayoutParams(new ViewGroup.MarginLayoutParams(dimension, dimension));
            view.setBackgroundColor(Color.rgb(random.nextFloat(), random.nextFloat(), random.nextFloat()));
            view.setTranslationX(random.nextFloat() * root.getWidth());
            view.setTranslationY(random.nextFloat() * root.getHeight());
            root.addView(view);
            views.add(view);
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     *
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions()
                .position(sydney)
                .title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        lastCameraPosition = mMap.getCameraPosition();
        mMap.setOnCameraMoveListener(() -> {
            CameraPosition newCameraPosition = mMap.getCameraPosition();
            if (lastCameraPosition.equals(newCameraPosition)) {
                duplicatedPositionCount++;
                Log.d("MapsExample", "got duplicated camera position: " + duplicatedPositionCount);
            } else {
                duplicatedPositionCount = 0;
            }
            lastCameraPosition = newCameraPosition;
            doEmulateWork();
        });
    }

    private void doEmulateWork() {
        if (emulateWork) {
            for (View view : views) {
                view.animate()
                        .translationX(random.nextFloat() * root.getWidth())
                        .translationY(random.nextFloat() * root.getHeight())
                        .start();
            }
        }
    }
}
//[END maps_android_mapsactivity]

package edu.msudenvercs3013.lab04

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import edu.msudenvercs3013.lab04.databinding.ActivityMapsBinding

class MapsFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding

    private lateinit var mMap: GoogleMap
    private var marker: Marker? = null
    private var parkedLocation: LatLng? = null

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private val fusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ActivityMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                getLocation { updateMapLocation(it) }
            } else {
                showPermissionRationale {
                    requestPermissionLauncher.launch(ACCESS_FINE_LOCATION)
                }
            }
        }

        val buttonParkedHere: Button = view.findViewById(R.id.button_parked_here)
        buttonParkedHere.setOnClickListener {
            parkedLocation?.let {
                updateMapLocation(it)
            } ?: run {
                Toast.makeText(requireContext(), "Please place a marker on the map first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap.apply {
            setOnMapClickListener { latLng ->
                addOrMoveSelectedPositionMarker(latLng)
            }
        }

        when {
            hasLocationPermission() -> getLocation{updateMapLocation(it)}
            shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION) -> {
                showPermissionRationale {
                    requestPermissionLauncher.launch(ACCESS_FINE_LOCATION)
                }
            }
            else -> requestPermissionLauncher.launch(ACCESS_FINE_LOCATION)
        }
    }

    private fun getLocation(onLocationReceived: (LatLng) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLocation = LatLng(it.latitude, it.longitude)
                onLocationReceived(userLocation)
            }
        }
    }


    private fun updateMapLocation(location: LatLng) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 7f))
    }

    private fun addMarkerAtLocation(
        location: LatLng, title: String,
        markerIcon: BitmapDescriptor? = null
    ) = mMap.addMarker(
        MarkerOptions().title(title).position(location)
            .apply { markerIcon?.let { icon(markerIcon) } }
    )

    private fun getBitmapDescriptorFromVector(@DrawableRes vectorDrawableResourceId: Int): BitmapDescriptor? {
        val bitmap = ContextCompat.getDrawable(requireContext(),
            vectorDrawableResourceId)?.let { vectorDrawable ->
            vectorDrawable.setBounds(0, 0,
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight)
            val drawableWithTint = DrawableCompat
                .wrap(vectorDrawable)
            DrawableCompat.setTint(drawableWithTint,
                Color.RED)
            val bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawableWithTint.draw(canvas)
            bitmap
        }?: return null
        return BitmapDescriptorFactory.fromBitmap(bitmap)
            .also { bitmap?.recycle() }
    }

    private fun addOrMoveSelectedPositionMarker(latLng: LatLng) {
        if (marker == null) {
            marker = addMarkerAtLocation(latLng, "Your Car", getBitmapDescriptorFromVector(R.drawable.car_icon))
        } else {
            marker?.position = latLng
        }
        parkedLocation = latLng
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun showPermissionRationale(
        positiveAction: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("Location permission")
            .setMessage("We need your permission to find your current position")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                positiveAction()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create().show()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
}
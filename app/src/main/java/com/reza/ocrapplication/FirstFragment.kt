package com.reza.ocrapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.reza.ocrapplication.data.OcrModel
import com.reza.ocrapplication.databinding.FragmentFirstBinding
import com.reza.ocrapplication.utils.PermissionResultListener
import com.reza.ocrapplication.utils.RequestPermissionListener
import com.reza.ocrapplication.utils.handleOnRequestPermissionResult
import com.reza.ocrapplication.utils.requestPermissions
import com.reza.ocrapplication.utils.showDialog


class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private lateinit var dbRef: DatabaseReference

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        dbRef = FirebaseDatabase.getInstance().getReference("OcrData")

        binding.buttonFirst.setOnClickListener {
            requestMultiplePermissionWithListener()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // multiple permissions
    private val multiplePermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private val multiplePermissionsCode = 2111

    private fun requestMultiplePermissionWithListener() {
        requestPermissions(
            multiplePermissions,
            multiplePermissionsCode,
            object : RequestPermissionListener {
                override fun onPermissionRationaleShouldBeShown(requestPermission: () -> Unit) {
                    showDialog(
                        message = "Please allow permissions to use this feature",
                        textPositive = "OK",
                        positiveListener = {
                            requestPermission.invoke()
                        },
                        textNegative = "Cancel"
                    )
                }

                override fun onPermissionPermanentlyDenied(openAppSetting: () -> Unit) {
                    showDialog(
                        message = "Permission Disabled, Please allow permissions to use this feature",
                        textPositive = "OK",
                        positiveListener = {
                            openAppSetting.invoke()
                        },
                        textNegative = "Cancel"
                    )
                }

                override fun onPermissionGranted() {
                    getLastLocation()
                    captureImage()
                }
            })
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // multiple permission
        handleOnRequestPermissionResult(
            multiplePermissionsCode,
            requestCode,
            permissions,
            grantResults,
            object : PermissionResultListener {
                override fun onPermissionRationaleShouldBeShown() {
                    showToast("permission denied")
                }

                override fun onPermissionPermanentlyDenied() {
                    showToast("permission permanently disabled")
                }

                override fun onPermissionGranted() {
                    getLastLocation()
                    captureImage()
                }
            }
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(context, "Fragment $message", Toast.LENGTH_SHORT).show()
    }

    private fun captureImage() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        resultLauncher.launch(intent)
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data

                //Extract the image
                val bundle = data!!.extras
                val bitmap = bundle?.get("data") as Bitmap?

                //Create a FirebaseVisionImage object from your image/bitmap.
                val firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap!!)
                val firebaseVision = FirebaseVision.getInstance()
                val firebaseVisionTextRecognizer = firebaseVision.onDeviceTextRecognizer

                //Process the Image
                val task = firebaseVisionTextRecognizer.processImage(firebaseVisionImage)
                task.addOnSuccessListener { firebaseVisionText: FirebaseVisionText ->
                    //Set recognized text from image in our TextView
                    val text = firebaseVisionText.text

                    navigateToNextScreen(bitmap, text)
                }
                task.addOnFailureListener { e: Exception ->
                    Toast.makeText(
                        binding.root.context,
                        e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                }
            } else {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            latitude = mLastLocation.latitude
            longitude = mLastLocation.longitude
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun navigateToNextScreen(imageCapture: Bitmap, ocrText: String) {
        saveOcrData(ocrText, latitude.toString(), longitude.toString())

        val bundleNextScreen = Bundle()
        bundleNextScreen.putParcelable("image", imageCapture)
        bundleNextScreen.putString("ocr_text", ocrText)
        bundleNextScreen.putString("lat", latitude.toString())
        bundleNextScreen.putString("long", longitude.toString())

        findNavController().navigate(
            R.id.action_FirstFragment_to_SecondFragment,
            bundleNextScreen
        )
    }

    private fun saveOcrData(
        ocrText: String,
        ocrDistance: String,
        ocrEstimate: String
    ) {
        val ocrId = dbRef.push().key!!

        val dataOcr = OcrModel(ocrId, ocrText, ocrDistance, ocrEstimate)

        dbRef.child(ocrId).setValue(dataOcr)
            .addOnCompleteListener {
               showToast("Data inserted successfully")

            }.addOnFailureListener { err ->
              showToast("Error ${err.message}")
            }

    }

}
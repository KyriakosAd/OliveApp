package com.example.orgolive

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.orgolive.ui.theme.OrgOliveTheme
import com.google.android.gms.common.SignInButton
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.engine.LocationEngineCallback
import com.mapbox.mapboxsdk.location.engine.LocationEngineRequest
import com.mapbox.mapboxsdk.location.engine.LocationEngineResult
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.permissions.PermissionsManager
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val database = Firebase.database

data class Grove(
    val key: String? = null,
    val owner: String = "",
    val variety: String = "",
    val organic: Boolean = false,
    val sprayed: Boolean = false,
    val coordinates: List<LatLng> = listOf()
)

class SharedViewModel : ViewModel() {
    var isAddingCoordinates by mutableStateOf(false)
    var isDeletingCoordinates by mutableStateOf(false)
    val previewCoordinates = MutableLiveData(mutableListOf<LatLng>())
    val previewCollection = MutableLiveData<FeatureCollection>()
    var selectedGrove = MutableLiveData<Grove?>()
    var mapboxMap = mutableStateOf<MapboxMap?>(null)
    val groves = mutableListOf<Grove>()
    var isCallbackEnabled by mutableStateOf(false)
}

class MapActivity : ComponentActivity() {
    private lateinit var signInWithGoogle: SignInWithGoogle
    private lateinit var sharedViewModel: SharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signInWithGoogle = SignInWithGoogle(this, onSignOut = {})
        signInWithGoogle.setupGoogleSignIn()

        sharedViewModel = ViewModelProvider(this)[SharedViewModel::class.java]

        setContent {
            OrgOliveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        TopBar(
                            onSignInClick = { signInWithGoogle.signIn() },
                            isSignedIn = signInWithGoogle.isSignedIn.value,
                            onSignOutClick = { signInWithGoogle.signOut() },
                            sharedViewModel
                        )
                        MapView(
                            Modifier.weight(1.2f), sharedViewModel,
                            isSignedIn = signInWithGoogle.isSignedIn.value
                        )
                    }
                }
            }
        }
    }
}

fun displayNotification(context: Context, title: String, message: String) {
    val channelId = "notification_channel"
    val notificationId = 101
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            0
        )
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification Title"
            val descriptionText = "Notification Description"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.bell)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun TopBar(
    onSignInClick: () -> Unit,
    isSignedIn: Boolean,
    onSignOutClick: () -> Unit,
    sharedViewModel: SharedViewModel
) {
    var callback: LocationEngineCallback<LocationEngineResult>? = null
    val contextLocal = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isSignedIn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    contextLocal as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
            Button(
                onClick = {
                    sharedViewModel.mapboxMap.value?.let { map ->
                        if (PermissionsManager.areLocationPermissionsGranted(contextLocal)) {
                            val locationComponent = map.locationComponent
                            locationComponent.isLocationComponentEnabled = false
                            callback?.let { call ->
                                locationComponent.locationEngine?.removeLocationUpdates(call)
                                sharedViewModel.isCallbackEnabled = false
                            }
                        }
                    }
                    onSignOutClick()
                },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(Color.Red)
            ) {
                Text(
                    text = "Sign Out",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
            Button(onClick = {
                sharedViewModel.mapboxMap.value?.let { map ->
                    map.style?.let { style ->
                        if (PermissionsManager.areLocationPermissionsGranted(contextLocal)) {
                            val locationComponent = map.locationComponent
                            if (sharedViewModel.isCallbackEnabled) {
                                locationComponent.cameraMode = CameraMode.TRACKING
                            } else {
                                locationComponent.activateLocationComponent(
                                    LocationComponentActivationOptions.builder(contextLocal, style)
                                        .build()
                                )
                                locationComponent.isLocationComponentEnabled = true
                                locationComponent.cameraMode = CameraMode.TRACKING
                                val request = LocationEngineRequest.Builder(1000)
                                    .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                                    .setMaxWaitTime(3000)
                                    .build()

                                callback = object : LocationEngineCallback<LocationEngineResult> {
                                    override fun onSuccess(result: LocationEngineResult?) {
                                        if (sharedViewModel.isCallbackEnabled) {
                                            val location = result?.lastLocation
                                            if (location != null) {
                                                val userLocation =
                                                    LatLng(location.latitude, location.longitude)
                                                //Log.e("LocationChange", "Location: ${location.latitude}, ${location.longitude}")
                                                val closestGrove =
                                                    sharedViewModel.groves.minByOrNull { grove ->
                                                        if (grove.coordinates.isNotEmpty()) {
                                                            grove.coordinates.minOf { coordinate ->
                                                                coordinate.distanceTo(userLocation)
                                                            }
                                                        } else {
                                                            Double.MAX_VALUE
                                                        }
                                                    }
                                                if (closestGrove != null) {
                                                    if ((!closestGrove.sprayed) && (!closestGrove.organic)) {
                                                        closestGrove.coordinates.forEach { coordinate ->
                                                            if (coordinate.distanceTo(userLocation) < 10) {
                                                                val updatedGrove = Grove(
                                                                    key = closestGrove.key,
                                                                    owner = closestGrove.owner,
                                                                    variety = closestGrove.variety,
                                                                    organic = false,
                                                                    sprayed = true,
                                                                    coordinates = closestGrove.coordinates
                                                                )
                                                                val grovesRef =
                                                                    database.getReference("groves")
                                                                grovesRef.child(closestGrove.key.toString())
                                                                    .setValue(updatedGrove)
                                                            }
                                                        }
                                                    } else if (closestGrove.organic) {
                                                        closestGrove.coordinates.forEach { coordinate ->
                                                            if (coordinate.distanceTo(userLocation) < 20) {
                                                                displayNotification(
                                                                    contextLocal,
                                                                    "Organic Grove Nearby",
                                                                    "Owner: ${closestGrove.owner}, Olive Variety: ${closestGrove.variety}"
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    override fun onFailure(exception: Exception) {
                                        exception.localizedMessage?.let {
                                            Log.d("LocationChange", it)
                                        }
                                    }
                                }

                                callback?.let { call ->
                                    locationComponent.locationEngine?.requestLocationUpdates(
                                        request,
                                        call,
                                        Looper.getMainLooper()
                                    )
                                    sharedViewModel.isCallbackEnabled = true
                                }
                            }
                        } else {
                            ActivityCompat.requestPermissions(
                                contextLocal as Activity,
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                0
                            )
                        }
                    }
                }
            }) {
                Text("Show Location")
            }
        } else {
            AndroidView(factory = { context ->
                SignInButton(context).apply {
                    setSize(SignInButton.SIZE_STANDARD)
                    setOnClickListener {
                        onSignInClick()
                    }
                }
            })
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapView(modifier: Modifier = Modifier, sharedViewModel: SharedViewModel, isSignedIn: Boolean) {
    var selectedLocation by remember { mutableStateOf(LatLng(37.7909, 26.7042)) }
    var isAddGroveFormVisible by remember { mutableStateOf(false) }
    var isEditGroveFormVisible by remember { mutableStateOf(false) }
    val previewCoordinates by sharedViewModel.previewCoordinates.observeAsState(initial = mutableListOf())

    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            Mapbox.getInstance(context)
            val mapView = MapView(context)
            val styleUrl = context.getString(R.string.style_url)
            mapView.onCreate(null)

            mapView.getMapAsync { map ->
                sharedViewModel.mapboxMap.value = map
                map.setStyle(styleUrl) { style ->
                    if (PermissionsManager.areLocationPermissionsGranted(context) && isSignedIn && sharedViewModel.isCallbackEnabled) {
                        val locationComponent = map.locationComponent
                        if (locationComponent.isLocationComponentActivated) {
                            val locationComponentActivationOptions =
                                LocationComponentActivationOptions.builder(context, style).build()
                            locationComponent.activateLocationComponent(
                                locationComponentActivationOptions
                            )
                        }
                    }

                    val drawableGreen =
                        AppCompatResources.getDrawable(context, R.drawable.ic_green_marker)
                    val bitmapGreen = BitmapUtils.getBitmapFromDrawable(drawableGreen)
                    style.addImage("marker-green", bitmapGreen!!)

                    val drawableBlue =
                        AppCompatResources.getDrawable(context, R.drawable.ic_blue_marker)
                    val bitmapBlue = BitmapUtils.getBitmapFromDrawable(drawableBlue)
                    style.addImage("marker-blue", bitmapBlue!!)

                    val drawableRed =
                        AppCompatResources.getDrawable(context, R.drawable.ic_red_marker)
                    val bitmapRed = BitmapUtils.getBitmapFromDrawable(drawableRed)
                    style.addImage("marker-red", bitmapRed!!)

                    val drawableBlack =
                        AppCompatResources.getDrawable(context, R.drawable.ic_black_marker)
                    val bitmapBlack = BitmapUtils.getBitmapFromDrawable(drawableBlack)
                    style.addImage("marker-black", bitmapBlack!!)

                    style.addSource(GeoJsonSource("grove-source"))
                    style.addSource(GeoJsonSource("preview-source"))

                    val iconLayer = SymbolLayer("grove-icons", "grove-source")
                        .withProperties(
                            PropertyFactory.iconImage("{marker}"),
                            PropertyFactory.iconAllowOverlap(true)
                        )
                    style.addLayer(iconLayer)

                    val previewLayer = SymbolLayer("preview-icons", "preview-source")
                        .withProperties(
                            PropertyFactory.iconImage("marker-black"),
                            PropertyFactory.iconAllowOverlap(true)
                        )
                    style.addLayerAbove(previewLayer, "grove-icons")

                    val textLayer = SymbolLayer("grove-labels", "grove-source")
                        .withProperties(
                            PropertyFactory.textField("{title}"),
                            PropertyFactory.textSize(13f),
                            PropertyFactory.textColor(
                                ContextCompat.getColor(
                                    context,
                                    android.R.color.black
                                )
                            ),
                            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                            PropertyFactory.textOffset(arrayOf(0f, -0.75f))
                        )
                    style.addLayer(textLayer)

                    val grovesRef = database.getReference("groves")
                    grovesRef.addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            sharedViewModel.groves.clear()
                            dataSnapshot.children.mapNotNullTo(sharedViewModel.groves) {
                                it.getValue(
                                    Grove::class.java
                                )
                            }

                            val features = sharedViewModel.groves.flatMap { grove ->
                                grove.coordinates.map { coordinate ->
                                    Feature.fromGeometry(
                                        Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                                        JsonObject().apply {
                                            addProperty(
                                                "title",
                                                grove.owner + "\n(" + grove.variety + ")"
                                            )
                                            addProperty(
                                                "marker", when {
                                                    grove.organic -> "marker-green"
                                                    grove.sprayed -> "marker-blue"
                                                    else -> "marker-red"
                                                }
                                            )
                                        }
                                    )
                                }
                            }

                            val featureCollection = FeatureCollection.fromFeatures(features)

                            style.getSourceAs<GeoJsonSource>("grove-source")
                                ?.setGeoJson(featureCollection)
                        }

                        override fun onCancelled(databaseError: DatabaseError) {
                            CoroutineScope(Dispatchers.IO).launch {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Error: ${databaseError.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    })

                    map.addOnMapClickListener { point ->
                        if (isAddGroveFormVisible || isEditGroveFormVisible) {
                            if (sharedViewModel.isAddingCoordinates) {
                                previewCoordinates += point
                                val previewFeatures = previewCoordinates.map { coordinate ->
                                    Feature.fromGeometry(
                                        Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                                        JsonObject().apply {
                                            addProperty("marker", "marker-black")
                                        }
                                    )
                                }
                                val previewCollection =
                                    FeatureCollection.fromFeatures(previewFeatures)
                                sharedViewModel.previewCollection.value = previewCollection
                            } else if (sharedViewModel.isDeletingCoordinates) {
                                val closest = previewCoordinates.minByOrNull { coordinate ->
                                    coordinate.distanceTo(point)
                                }
                                if (closest != null && closest.distanceTo(point) < 40) {
                                    previewCoordinates.remove(closest)
                                    val previewFeatures = previewCoordinates.map { coordinate ->
                                        Feature.fromGeometry(
                                            Point.fromLngLat(
                                                coordinate.longitude,
                                                coordinate.latitude
                                            ),
                                            JsonObject().apply {
                                                addProperty("marker", "marker-black")
                                            }
                                        )
                                    }
                                    val previewCollection =
                                        FeatureCollection.fromFeatures(previewFeatures)
                                    sharedViewModel.previewCollection.value = previewCollection
                                }
                            } else if (isEditGroveFormVisible) {
                                val closestGrove = sharedViewModel.groves.minByOrNull { grove ->
                                    if (grove.coordinates.isNotEmpty()) {
                                        grove.coordinates.minOf { coordinate ->
                                            coordinate.distanceTo(point)
                                        }
                                    } else {
                                        Double.MAX_VALUE
                                    }
                                }
                                if (closestGrove != null) {
                                    val clonedGrove = Grove(
                                        closestGrove.key,
                                        closestGrove.owner,
                                        closestGrove.variety,
                                        closestGrove.organic,
                                        closestGrove.sprayed,
                                        closestGrove.coordinates.toMutableList()
                                    )
                                    sharedViewModel.selectedGrove.value = clonedGrove
                                }
                            } else {
                                selectedLocation = point
                            }
                        } else {
                            selectedLocation = point
                        }
                        true
                    }
                    sharedViewModel.previewCollection.observe(lifecycleOwner) { previewCollection ->
                        style.let {
                            it.getSourceAs<GeoJsonSource>("preview-source")
                                ?.setGeoJson(previewCollection)
                        }
                    }

                    sharedViewModel.selectedGrove.observe(lifecycleOwner) { selectedGrove ->
                        if (selectedGrove != null) {
                            val selectedGroveFeatures =
                                selectedGrove.coordinates.map { coordinate ->
                                    Feature.fromGeometry(
                                        Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                                        JsonObject().apply {
                                            addProperty("marker", "marker-black")
                                        }
                                    )
                                }
                            val selectedGroveCollection =
                                FeatureCollection.fromFeatures(selectedGroveFeatures)
                            style.getSourceAs<GeoJsonSource>("preview-source")
                                ?.setGeoJson(selectedGroveCollection)
                        } else {
                            val previewCollection = FeatureCollection.fromFeatures(emptyList())
                            style.getSourceAs<GeoJsonSource>("preview-source")
                                ?.setGeoJson(previewCollection)
                        }
                    }

                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(selectedLocation.latitude, selectedLocation.longitude))
                        .zoom(13.0)
                        .build()
                }
            }
            mapView
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .height(38.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (!isAddGroveFormVisible) {
            Button(
                shape = RectangleShape,
                onClick = {
                    isEditGroveFormVisible = !isEditGroveFormVisible
                    isAddGroveFormVisible = false
                    if (!isEditGroveFormVisible) {
                        sharedViewModel.isAddingCoordinates = false
                        sharedViewModel.isDeletingCoordinates = false

                        sharedViewModel.previewCoordinates.value?.clear()
                        val previewCollection = FeatureCollection.fromFeatures(emptyList())
                        sharedViewModel.previewCollection.value = previewCollection
                    }
                }
            ) {
                Text(
                    text = if (isEditGroveFormVisible) "Hide" else "Edit Grove",
                    fontSize = 16.sp
                )
            }
        }
        if (!isEditGroveFormVisible && !isAddGroveFormVisible) {
            Spacer(modifier = Modifier.width(15.dp))
        }
        if (!isEditGroveFormVisible) {
            Button(
                shape = RectangleShape,
                onClick = {
                    isAddGroveFormVisible = !isAddGroveFormVisible
                    isEditGroveFormVisible = false
                    if (!isAddGroveFormVisible) {
                        sharedViewModel.isAddingCoordinates = false
                        sharedViewModel.isDeletingCoordinates = false

                        sharedViewModel.previewCoordinates.value?.clear()
                        val previewCollection = FeatureCollection.fromFeatures(emptyList())
                        sharedViewModel.previewCollection.value = previewCollection
                    }
                }
            ) {
                Text(
                    text = if (isAddGroveFormVisible) "Hide" else "Add Grove",
                    fontSize = 16.sp
                )
            }
        }
    }

    if (isAddGroveFormVisible) {
        Box(
            modifier = Modifier.fillMaxHeight(0.38f)
        ) {
            AddGroveForm(modifier, sharedViewModel)
        }
    } else if (isEditGroveFormVisible) {
        Box(
            modifier = Modifier.fillMaxHeight(0.38f)
        ) {
            EditGroveForm(modifier, sharedViewModel)
        }
    }
}

@Composable
fun AddGroveForm(modifier: Modifier = Modifier, sharedViewModel: SharedViewModel) {
    val selectedOwnerName = remember { mutableStateOf(TextFieldValue()) }
    val selectedVarietyName = remember { mutableStateOf(TextFieldValue()) }
    var isOrganic by remember { mutableStateOf(false) }
    var isSprayed by remember { mutableStateOf(false) }
    val previewCoordinates by sharedViewModel.previewCoordinates.observeAsState(initial = mutableListOf())

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 6.dp)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Coordinates: ",
            fontSize = 16.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val addingButton = remember { mutableStateOf<ToggleButton?>(null) }
            val deletingButton = remember { mutableStateOf<ToggleButton?>(null) }

            LaunchedEffect(sharedViewModel.isAddingCoordinates) {
                addingButton.value?.isChecked = sharedViewModel.isAddingCoordinates
            }

            LaunchedEffect(sharedViewModel.isDeletingCoordinates) {
                deletingButton.value?.isChecked = sharedViewModel.isDeletingCoordinates
            }

            AndroidView(
                factory = { context ->
                    ToggleButton(context).apply {
                        textOn = "Stop Adding"
                        textOff = "Start Adding"
                        isChecked = sharedViewModel.isAddingCoordinates
                        setOnCheckedChangeListener { _, isChecked ->
                            sharedViewModel.isAddingCoordinates = isChecked
                            if (isChecked) {
                                sharedViewModel.isDeletingCoordinates = false
                            }
                        }
                        addingButton.value = this
                    }
                }
            )

            Spacer(modifier = Modifier.width(5.dp))

            AndroidView(
                factory = { context ->
                    ToggleButton(context).apply {
                        textOn = "Stop Deleting"
                        textOff = "Start Deleting"
                        isChecked = sharedViewModel.isDeletingCoordinates
                        setOnCheckedChangeListener { _, isChecked ->
                            sharedViewModel.isDeletingCoordinates = isChecked
                            if (isChecked) {
                                sharedViewModel.isAddingCoordinates = false
                            }
                        }
                        deletingButton.value = this
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = selectedOwnerName.value,
            onValueChange = { selectedOwnerName.value = it },
            label = { Text("Owner's Name") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = selectedVarietyName.value,
            onValueChange = { selectedVarietyName.value = it },
            label = { Text("Olive Variety") }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isSprayed) {
                Checkbox(
                    checked = isOrganic,
                    onCheckedChange = { isOrganic = it }
                )
                Text("Organic")
            }

            Spacer(modifier = Modifier.width(15.dp))

            if (!isOrganic) {
                Checkbox(
                    checked = isSprayed,
                    onCheckedChange = { isSprayed = it }
                )
                Text("Sprayed")
            }
        }

        Button(
            onClick = {
                val ownerName = selectedOwnerName.value.text
                val varietyName = selectedVarietyName.value.text
                if (ownerName.isEmpty() || varietyName.isEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "No owner or variety name entered! Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    val grovesRef = database.getReference("groves")
                    val key = grovesRef.push().key
                    if (key != null) {
                        val grove = Grove(
                            key,
                            ownerName,
                            varietyName,
                            isOrganic,
                            isSprayed,
                            previewCoordinates
                        )
                        grovesRef.child(key).setValue(grove)
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Grove successfully added!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        sharedViewModel.previewCoordinates.value?.clear()
                        val previewCollection = FeatureCollection.fromFeatures(emptyList())
                        sharedViewModel.previewCollection.value = previewCollection
                    }
                }
            }) {
            Text("Submit")

        }
    }
}

@Composable
fun EditGroveForm(modifier: Modifier = Modifier, sharedViewModel: SharedViewModel) {
    val selectedOwnerName = remember { mutableStateOf(TextFieldValue()) }
    val selectedVarietyName = remember { mutableStateOf(TextFieldValue()) }
    var isOrganic by remember { mutableStateOf(false) }
    var isSprayed by remember { mutableStateOf(false) }
    val previewCoordinates by sharedViewModel.previewCoordinates.observeAsState(initial = mutableListOf())

    val selectedGrove by sharedViewModel.selectedGrove.observeAsState(initial = null)
    LaunchedEffect(selectedGrove) {
        selectedGrove?.let {
            selectedOwnerName.value = TextFieldValue(it.owner, TextRange(it.owner.length))
            selectedVarietyName.value = TextFieldValue(it.variety, TextRange(it.variety.length))
            isOrganic = it.organic
            isSprayed = it.sprayed
            previewCoordinates.clear()
            previewCoordinates += it.coordinates
        }
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 6.dp)
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val addingButton = remember { mutableStateOf<ToggleButton?>(null) }
        val deletingButton = remember { mutableStateOf<ToggleButton?>(null) }
        Text(
            text = "Coordinates: ",
            fontSize = 16.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            LaunchedEffect(sharedViewModel.isAddingCoordinates) {
                addingButton.value?.isChecked = sharedViewModel.isAddingCoordinates
            }

            LaunchedEffect(sharedViewModel.isDeletingCoordinates) {
                deletingButton.value?.isChecked = sharedViewModel.isDeletingCoordinates
            }

            AndroidView(
                factory = { context ->
                    ToggleButton(context).apply {
                        textOn = "Stop Adding"
                        textOff = "Start Adding"
                        isChecked = sharedViewModel.isAddingCoordinates
                        setOnCheckedChangeListener { _, isChecked ->
                            if (sharedViewModel.selectedGrove.value == null) {
                                Toast.makeText(context, "Select a grove first.", Toast.LENGTH_SHORT)
                                    .show()
                                sharedViewModel.isAddingCoordinates = false
                                addingButton.value?.isChecked = false
                            } else {
                                sharedViewModel.isAddingCoordinates = isChecked
                                if (isChecked) {
                                    sharedViewModel.isDeletingCoordinates = false
                                    deletingButton.value?.isChecked = false
                                }
                            }
                        }
                        addingButton.value = this
                    }
                }
            )

            Spacer(modifier = Modifier.width(5.dp))

            AndroidView(
                factory = { context ->
                    ToggleButton(context).apply {
                        textOn = "Stop Deleting"
                        textOff = "Start Deleting"
                        isChecked = sharedViewModel.isDeletingCoordinates
                        setOnCheckedChangeListener { _, isChecked ->
                            if (sharedViewModel.selectedGrove.value == null) {
                                Toast.makeText(context, "Select a grove first.", Toast.LENGTH_SHORT)
                                    .show()
                                sharedViewModel.isDeletingCoordinates = false
                                deletingButton.value?.isChecked = false
                            } else {
                                sharedViewModel.isDeletingCoordinates = isChecked
                                if (isChecked) {
                                    sharedViewModel.isAddingCoordinates = false
                                    addingButton.value?.isChecked = false
                                }
                            }
                        }
                        deletingButton.value = this
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = selectedOwnerName.value,
            onValueChange = { selectedOwnerName.value = it },
            label = { Text("Owner's Name") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = selectedVarietyName.value,
            onValueChange = { selectedVarietyName.value = it },
            label = { Text("Olive Variety") }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isSprayed) {
                Checkbox(
                    checked = isOrganic,
                    onCheckedChange = { isOrganic = it }
                )
                Text("Organic")
            }

            Spacer(modifier = Modifier.width(15.dp))

            if (!isOrganic) {
                Checkbox(
                    checked = isSprayed,
                    onCheckedChange = { isSprayed = it }
                )
                Text("Sprayed")
            }
        }

        Button(
            onClick = {
                if (sharedViewModel.selectedGrove.value == null) {
                    Toast.makeText(context, "Select a grove first.", Toast.LENGTH_SHORT).show()
                } else {
                    val ownerName = selectedOwnerName.value.text
                    val varietyName = selectedVarietyName.value.text
                    if (ownerName.isEmpty() || varietyName.isEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "No owner or variety name entered! Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        selectedGrove?.let {
                            if (it.key != null) {
                                if (previewCoordinates.isEmpty()) {
                                    val grovesRef = database.getReference("groves")
                                    grovesRef.child(it.key).removeValue()
                                } else {
                                    val updatedGrove = Grove(
                                        it.key,
                                        ownerName,
                                        varietyName,
                                        isOrganic,
                                        isSprayed,
                                        previewCoordinates
                                    )
                                    val grovesRef = database.getReference("groves")
                                    grovesRef.child(it.key).setValue(updatedGrove)
                                }
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Grove successfully edited!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                sharedViewModel.isDeletingCoordinates = false
                                deletingButton.value?.isChecked = false
                                sharedViewModel.isAddingCoordinates = false
                                addingButton.value?.isChecked = false

                                sharedViewModel.previewCoordinates.value?.clear()
                                val previewCollection = FeatureCollection.fromFeatures(emptyList())
                                sharedViewModel.previewCollection.value = previewCollection
                                sharedViewModel.selectedGrove.value = null
                            }
                        }
                    }
                }
            }) {
            Text("Edit")
        }
    }
}
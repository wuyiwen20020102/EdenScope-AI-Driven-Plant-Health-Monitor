package com.example.planthealthmonitorapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amplifyframework.core.Amplify
import com.amplifyframework.ui.authenticator.SignedInState
import com.amplifyframework.ui.authenticator.enums.AuthenticatorStep
import com.amplifyframework.ui.authenticator.rememberAuthenticatorState
import com.amplifyframework.ui.authenticator.ui.Authenticator
import com.example.planthealthmonitorapplication.databinding.PhotoLayoutBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.core.graphics.toColorInt

enum class BottomBarTab {
    HOME,
    PHOTO,
    PROFILE,
    LIST
}

class MainActivity : AppCompatActivity() {
    private val usernameState = mutableStateOf("")
    private val nameState = mutableStateOf("")

    companion object {
        var shouldRefreshPhotos = false
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private var initialPhotoLoadDone = false
    }

    override fun onResume() {
        super.onResume()
        if (shouldRefreshPhotos) {
            Log.d(TAG, "Activity resumed with shouldRefreshPhotos=true, will refresh photos")
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val locationPermissionGranted = grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED
                
            Log.d(TAG, "Location permission granted: $locationPermissionGranted")
            
            if (locationPermissionGranted) {
                Toast.makeText(
                    this,
                    "Location access granted. Your photos will now appear on the map.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processGalleryImage(uri)
                shouldRefreshPhotos = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContent {
            MyAppTheme {
                MainComponents()
            }
        }
    }
    
    private fun openGallery() {
        val locationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!locationPermission) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Location Permission Recommended")
                .setMessage("To show your images on the map, enable location permission?")
                .setPositiveButton("Enable") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                    launchGalleryPicker()
                }
                .setNegativeButton("No Thanks") { _, _ ->
                    launchGalleryPicker()
                }
                .show()
        } else {
            launchGalleryPicker()
        }
    }
    
    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }
    
    private fun processGalleryImage(uri: Uri) {
        try {
            val originalBitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
            val finalBitmap = originalBitmap?.let { bitmap ->
                adjustBitmapOrientation(this, bitmap, uri)
            } ?: return
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
            val username = usernameState.value
            val imageName = username + "_" + UUID.randomUUID().toString() + ".jpg"
            val byteArrayOutputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
            val imageData = byteArrayOutputStream.toByteArray()
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || 
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (locationPermission) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    val baseDescription = "Uploaded from Gallery at ${SimpleDateFormat("hh:mm a", Locale.US).format(Date())}"
                    val descriptionWithLocation = if (location != null) {
                        "$baseDescription at [${String.format("%.5f", location.latitude)}, " +
                        "${String.format("%.5f", location.longitude)}]"
                    } else {
                        baseDescription
                    }
                    val note = UserData.Note(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        description = descriptionWithLocation,
                        imageName = imageName,
                        healthStatus = UserData.HealthStatus.HEALTHY,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )

                    note.image = finalBitmap
                    Backend.storeImage(imageName, imageData)
                    Backend.createNote(note)
                    UserData.addNote(note)
                    val locationMsg = if (location != null) " with location data" else ""
                    Log.d(TAG, "Gallery image processed with location: ${location?.latitude}, ${location?.longitude}")
                }
            } else {
                val note = UserData.Note(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = "Uploaded from Gallery at ${SimpleDateFormat("hh:mm a", Locale.US).format(Date())}"
                )
                
                note.imageName = imageName
                note.image = finalBitmap
                Backend.storeImage(imageName, imageData)
                Backend.createNote(note)
                UserData.addNote(note)
                Toast.makeText(this, 
                    "Image uploaded without location. Enable location permission for full features.", 
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "Gallery image processed without location (no permission)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing gallery image: ${e.message}", e)
            Toast.makeText(this, "Failed to process gallery image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun adjustBitmapOrientation(context: Activity, bitmap: Bitmap, imageUri: Uri): Bitmap {
        var rotatedBitmap = bitmap
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return rotatedBitmap
    }

    @Composable
    fun MainComponents() {
        var currentTab by rememberSaveable { mutableStateOf(BottomBarTab.HOME) }
        FetchUsernameAndName(usernameState, nameState)

        val authenticatorState = rememberAuthenticatorState(
            initialStep = AuthenticatorStep.SignIn,
            signUpForm = {
                name(true)
                username()
                password()
                confirmPassword()
                email()
            }
        )

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        selected = currentTab == BottomBarTab.HOME,
                        onClick = {
                            currentTab = BottomBarTab.HOME
                        }
                    )

                    // List
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Photo, contentDescription = "Photo") },
                        selected = currentTab == BottomBarTab.PHOTO,
                        onClick = {
                            // Show normal search content
                            currentTab = BottomBarTab.PHOTO
                        }
                    )

                    // Person
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        selected = currentTab == BottomBarTab.PROFILE,
                        onClick = {
                            // If not signed in, we show Authenticator;
                            // if signed in, maybe show the user’s profile
                            currentTab = BottomBarTab.PROFILE
                        }
                    )

                    // List
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "List") },
                        selected = currentTab == BottomBarTab.LIST,
                        onClick = {
                            // Show normal search content
                            currentTab = BottomBarTab.LIST
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    BottomBarTab.HOME -> HomeContent()
                    BottomBarTab.PHOTO -> PhotoContent(
                        onSignInRequested = { currentTab = BottomBarTab.PROFILE }
                        // Pass the current tab value
                    )
                    BottomBarTab.PROFILE -> {
                        // Use the Amplify Authenticator for sign in/out
                        Authenticator(state = authenticatorState) { signedInState ->
                            SignedInContent(signedInState) {
                                // After signing out, switch back to Home (or any tab you choose)
                                currentTab = BottomBarTab.HOME
                            }
                        }
                    }
                    BottomBarTab.LIST -> ListContent()  // Another layout you might define
                }
            }
        }
    }

    @Composable
    fun FetchUsernameAndName(usernameState: MutableState<String>, nameState:MutableState<String>) {
        LaunchedEffect(Unit) {
            Amplify.Auth.getCurrentUser(
                { authUser ->
                    usernameState.value = authUser.username
                    Log.d(TAG, "Fetched username: ${authUser.username}")
                },
                { error ->
                    Log.e(TAG, "Failed to fetch username", error)
                    usernameState.value = "Guest"
                }
            )

            Amplify.Auth.fetchUserAttributes(
                { attributes ->
                    val nameAttr = attributes.find { it.key.keyString == "name" }?.value
                    nameState.value = nameAttr.toString()
                },
                { error ->
                    Log.e(TAG, "Failed to fetch user attributes", error)
                    nameState.value = "Guest"
                }
            )
        }
    }

    @Composable
    fun SignedInContent(state: SignedInState, onSignOutComplete: () -> Unit) {
        val scope = rememberCoroutineScope()
        val notesList by UserData.notes().observeAsState(initial = mutableListOf())
        val displayName = nameState.value.ifBlank { "Guest" }
        val username = usernameState.value
        val profileCardAnimation = remember { androidx.compose.animation.core.Animatable(0f) }
        val statsCardAnimation = remember { androidx.compose.animation.core.Animatable(0f) }
        val profileColor = MaterialTheme.colorScheme.primaryContainer
        val profileTextColor = MaterialTheme.colorScheme.onPrimaryContainer

        LaunchedEffect(Unit) {
            // Fetch user attributes
            Amplify.Auth.fetchUserAttributes(
                { attributes ->
                    val nameAttr = attributes.find { it.key.keyString == "name" }?.value
                    nameState.value = nameAttr ?: ""
                    usernameState.value = state.user.username
                },
                { error ->
                    Log.e(TAG, "Failed to fetch user attributes", error)
                    nameState.value = ""
                }
            )

            profileCardAnimation.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            statsCardAnimation.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, delayMillis = 100, easing = FastOutSlowInEasing)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .scale(profileCardAnimation.value)
                        .alpha(profileCardAnimation.value),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = profileColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(4.dp)
                                .border(
                                    width = 4.dp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Display the first letter of the name as the avatar
                            Text(
                                text = displayName.firstOrNull()?.toString()?.uppercase() ?: "G",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // User Info
                        Text(
                            text = "Welcome back!",
                            style = MaterialTheme.typography.titleMedium,
                            color = profileTextColor.copy(alpha = 0.7f)
                        )
                        
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = profileTextColor,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.titleSmall,
                            color = profileTextColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .scale(statsCardAnimation.value)
                        .alpha(statsCardAnimation.value),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Your EdenScope Stats",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Images count stat
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                    .padding(vertical = 16.dp)
                            ) {
                                Text(
                                    text = "${notesList.size}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Images",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Account age (just a static example)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                    .padding(vertical = 16.dp)
                            ) {
                                val baseCredits = notesList.size * 5
                                val healthyBonus = notesList.count { it.healthStatus == UserData.HealthStatus.HEALTHY } * 3
                                val attentionBonus = notesList.count { it.healthStatus == UserData.HealthStatus.NEEDS_ATTENTION }
                                val uniqueDays = notesList.map { it.name.split("-").take(3).joinToString("-") }.toSet().size
                                val streakBonus = if (uniqueDays > 1) 10 else 0
                                val totalCredits = baseCredits + healthyBonus + attentionBonus + streakBonus
                                
                                Text(
                                    text = "$totalCredits",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Credits",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                        
                                        // Info icon with tooltip on click
                                        Icons.Default.Info.let { icon ->
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Credit info",
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .size(14.dp)
                                                    .clickable {
                                                        // Show a toast explaining credits
                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "Credits: 5 per photo + 3 per healthy plant + 1 per plant needing attention + 10 streak bonus for multiple days",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    },
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            state.signOut()
                            onSignOutComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(statsCardAnimation.value),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sign Out",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PhotoContent(onSignInRequested: () -> Unit) {
        val isSignedIn by UserData.isSignedIn.observeAsState(initial = false)
        val refreshTrigger = remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            if (shouldRefreshPhotos) {
                Log.d(TAG, "Detected shouldRefreshPhotos flag is set to true")
                shouldRefreshPhotos = false
                refreshTrigger.intValue++
            }
        }

        if (isSignedIn) {
            LaunchedEffect(refreshTrigger.intValue) {
                if (!initialPhotoLoadDone || refreshTrigger.intValue > 0) {
                    Log.d(TAG, "Loading photo data - initial load: ${!initialPhotoLoadDone}, refresh needed: ${refreshTrigger.intValue > 0}")
                    Backend.queryNotes()
                    initialPhotoLoadDone = true
                } else {
                    Log.d(TAG, "Skipping photo data load - already loaded and no refresh triggered")
                }
            }
        }

        if (!isSignedIn) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("You are not signed in. Please sign in to view photos.")
                Button(onClick = onSignInRequested) {
                    Text("Sign In")
                }
            }
        }
        else {
            AndroidViewBinding(
                factory = { inflater, parent, _ ->
                    PhotoLayoutBinding.inflate(inflater, parent, false)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                setupRecyclerView(contentImage.itemList)

                fabAdd.setOnClickListener {
                    val intent = Intent(it.context, CameraActivity::class.java)
                    intent.putExtra("username", usernameState.value)
                    it.context.startActivity(intent)
                    shouldRefreshPhotos = true
                }

                fabGallery.setOnClickListener {
                    openGallery()
                }
            }
        }
    }

    @Composable
    fun ListContent() {
        var refreshCounter by remember { mutableIntStateOf(0) }
        val animatedAlpha = remember(refreshCounter) { 
            androidx.compose.animation.core.Animatable(initialValue = if (refreshCounter % 2 == 0) 0.99f else 1.0f) 
        }

        LaunchedEffect(refreshCounter) {
            Log.d(TAG, "Refresh triggered: $refreshCounter")
            UserData.notifyObserver()
            kotlinx.coroutines.delay(50)
            animatedAlpha.animateTo(
                targetValue = if (refreshCounter % 2 == 0) 1.0f else 0.99f,
                animationSpec = tween(
                    durationMillis = 50,
                    easing = FastOutSlowInEasing
                )
            )
        }

        val forceRefresh: () -> Unit = {
            Log.d(TAG, "Force refresh requested: current=$refreshCounter")
            refreshCounter++
        }
        val isSignedIn by UserData.isSignedIn.observeAsState(initial = false)
        val notesList by UserData.notes().observeAsState(initial = mutableListOf())
        val healthIssues by UserData.healthIssues().observeAsState(initial = mutableListOf())
        val treatments by UserData.treatments().observeAsState(initial = mutableListOf())
        val healthStats by UserData.healthStatistics().observeAsState(initial = UserData.HealthStatistics())
        val savedItems by UserData.savedItems().observeAsState(initial = mutableListOf())
        var selectedFilter by remember { mutableStateOf("All") }
        
        if (!isSignedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Please sign in to view your plant health history")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { /* Navigate to profile tab */ }) {
                    Text("Sign In")
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
                    .alpha(animatedAlpha.value)
            ) {
                item {
                    Text(
                        text = "Plant Health Management",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = "Timeline",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Analysis History",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FilterChip("All", selectedFilter == "All") { selectedFilter = "All" }
                                FilterChip("Healthy", selectedFilter == "Healthy") { selectedFilter = "Healthy" }
                                FilterChip("Issues", selectedFilter == "Issues") { selectedFilter = "Issues" }
                            }

                            val filteredNotes = when (selectedFilter) {
                                "Healthy" -> notesList.filter { it.healthStatus == UserData.HealthStatus.HEALTHY }
                                "Issues" -> notesList.filter { it.healthStatus != UserData.HealthStatus.HEALTHY }
                                else -> notesList
                            }.sortedByDescending { it.name }

                            if (filteredNotes.isNotEmpty()) {
                                filteredNotes.take(5).forEach { note ->
                                    AnalysisHistoryItem(note)
                                }
                                
                                if (filteredNotes.size > 5) {
                                    Button(
                                        onClick = { /* Show more */ },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("View All History")
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (selectedFilter) {
                                            "Healthy" -> "No healthy plants found. Keep up with your treatments!"
                                            "Issues" -> "No plants with issues found. Great job maintaining plant health!"
                                            else -> "No analyses yet. Capture plant images to begin tracking health."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Health Issues",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Health Issues",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HealthIssueCategory(
                                "Diseases", 
                                healthStats.diseaseCount, 
                                "#F44336"
                            )
                            HealthIssueCategory(
                                "Nutrient Deficiencies", 
                                healthStats.nutrientCount, 
                                "#FF9800"
                            )
                            HealthIssueCategory(
                                "Water Stress", 
                                healthStats.waterCount, 
                                "#2196F3"
                            )
                            HealthIssueCategory(
                                "Pests", 
                                healthStats.pestCount, 
                                "#4CAF50"
                            )
                            
                            // Show message if no issues
                            if (healthIssues.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No health issues detected. Your plants are healthy!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha =.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "treatments_section_${treatments.count { !it.isCompleted }}_${refreshCounter}") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Healing,
                                    contentDescription = "Treatments",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Treatment Recommendations",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val activeTreatments = treatments.filter { !it.isCompleted }
                            
                            if (activeTreatments.isNotEmpty()) {
                                activeTreatments.take(3).forEach { treatment ->
                                    TreatmentRecommendation(
                                        treatment.title,
                                        treatment.description,
                                        when (treatment.priority) {
                                            UserData.IssueSeverity.HIGH -> "High"
                                            UserData.IssueSeverity.MEDIUM -> "Medium"
                                            UserData.IssueSeverity.LOW -> "Low"
                                        },
                                        isActive = true,
                                        onMarkComplete = { UserData.completeTreatment(treatment.id) },
                                        forceRefresh = forceRefresh
                                    )
                                }

                                if (activeTreatments.size > 3) {
                                    Button(
                                        onClick = {
                                            android.app.AlertDialog.Builder(this@MainActivity)
                                                .setTitle("All Active Treatments")
                                                .setMessage(activeTreatments.joinToString("\n\n") { 
                                                    "${it.title}\nPriority: ${it.priority}\n${it.description}" 
                                                })
                                                .setPositiveButton("Close", null)
                                                .show()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("View All Treatments (${activeTreatments.size})")
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No active treatments. Your plants are healthy!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Statistics",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Plant Health Insights",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            // Stats summary
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatisticItem("Healthy", "${healthStats.healthyCount}", "#4CAF50")
                                StatisticItem("Treatment", "${healthStats.needsAttentionCount}", "#2196F3")
                                StatisticItem("Critical", "${healthStats.criticalCount}", "#F44336")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Progress bar for improvement
                            Text(
                                text = "Overall Health Trend",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            LinearProgressIndicator(
                                progress = healthStats.healthTrend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF4CAF50),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            
                            val trendPercentage = (healthStats.healthTrend * 100).toInt()
                            Text(
                                text = "$trendPercentage% overall plant health",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                item(key = "saved_items_section_${savedItems.size}_${refreshCounter}") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorites",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved Items",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (savedItems.isNotEmpty()) {
                                val savedNotes = notesList.filter { note -> savedItems.contains(note.id) }
                                
                                if (savedNotes.isNotEmpty()) {
                                    savedNotes.take(3).forEach { note ->
                                        AnalysisHistoryItem(
                                            note = note,
                                            showSavedIcon = true,
                                            onUnsave = { UserData.unsaveItem(note.id) },
                                            forceRefresh = forceRefresh
                                        )
                                    }
                                    
                                    if (savedNotes.size > 3) {
                                        Button(
                                            onClick = { /* View all saved */ },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Text("View All Saved Items (${savedNotes.size})")
                                        }
                                    }
                                } else {
                                    EmptySavedItemsMessage()
                                }
                            } else {
                                EmptySavedItemsMessage()

                                if (notesList.isNotEmpty()) {
                                    val latestNote = notesList.maxByOrNull { it.name }
                                    Button(
                                        onClick = { 
                                            latestNote?.let {
                                                UserData.saveItem(it.id)

                                                runOnUiThread {
                                                    forceRefresh()

                                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                        forceRefresh()
                                                    }, 100)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        enabled = latestNote != null
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add to saved items",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Latest Analysis")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun EmptySavedItemsMessage() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Star items to save them for quick reference",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
    
    @Composable
    private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    
    @Composable
    private fun AnalysisHistoryItem(
        note: UserData.Note, 
        showSavedIcon: Boolean = false,
        onUnsave: (() -> Unit)? = null,
        forceRefresh: () -> Unit = {}
    ) {
        val animationState = remember { androidx.compose.animation.core.Animatable(1f) }

        LaunchedEffect(showSavedIcon) {
            animationState.animateTo(0.98f, tween(100))
            animationState.animateTo(1f, tween(100))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .scale(animationState.value),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (note.image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = note.image!!.asImageBitmap(),
                        contentDescription = "Plant image thumbnail",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Plant image thumbnail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = note.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Analyzed on ${note.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusText, statusColor) = when (note.healthStatus) {
                    UserData.HealthStatus.HEALTHY -> Pair(
                        "Healthy", 
                        Color(0xFF4CAF50)
                    )
                    UserData.HealthStatus.NEEDS_ATTENTION -> Pair(
                        "Attention", 
                        Color(0xFFFF9800)
                    )
                    UserData.HealthStatus.CRITICAL -> Pair(
                        "Critical", 
                        Color(0xFFF44336)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }

                if (showSavedIcon) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Saved",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onUnsave?.invoke()
                                runOnUiThread {
                                    forceRefresh()
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        forceRefresh()
                                    }, 100)
                                }
                             }
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                UserData.saveItem(note.id)
                                runOnUiThread {
                                    forceRefresh()
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        forceRefresh()
                                    }, 100)
                                }
                            }
                    )
                }
            }
        }
    }
    
    @Composable
    private fun HealthIssueCategory(name: String, count: Int, colorHex: String) {
        val color = Color(colorHex.toColorInt())
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    
    @Composable
    private fun TreatmentRecommendation(
        title: String, 
        description: String, 
        priority: String, 
        isActive: Boolean = false,
        onMarkComplete: () -> Unit = {},
        forceRefresh: () -> Unit = {}
    ) {
        val animationState = remember { androidx.compose.animation.core.Animatable(1f) }

        LaunchedEffect(isActive) {
            animationState.animateTo(0.97f, tween(100))
            animationState.animateTo(1f, tween(100))
        }
        val priorityColor = when (priority) {
            "High" -> Color(0xFFF44336)
            "Medium" -> Color(0xFFFF9800)
            else -> Color(0xFF4CAF50)
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .scale(animationState.value),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = if (isActive) 
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            else null
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(priorityColor)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = priority,
                        style = MaterialTheme.typography.bodySmall,
                        color = priorityColor
                    )
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (isActive) {
                    Button(
                        onClick = {
                            onMarkComplete()
                            runOnUiThread {
                                forceRefresh()
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    forceRefresh()
                                }, 100)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Mark Complete")
                    }
                }
            }
        }
    }
    
    @Composable
    private fun StatisticItem(label: String, value: String, colorHex: String) {
        val color = Color(colorHex.toColorInt())
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular indicator
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }

    @Composable
    fun HomeContent() {
        val notesList by UserData.notes().observeAsState(initial = mutableListOf())
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(37.4275, -122.1697), 10f)  // Default location (Palo Alto)
        }
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val locationPermissionState = rememberPermissionState(locationPermission)
        var hasLocationPermission by remember { mutableStateOf(locationPermissionState.value) }

        LaunchedEffect(Unit) {
            if (!hasLocationPermission) {
                locationPermissionState.launchPermissionRequest()
            } else {
                updateMapToCurrentLocation(cameraPositionState)
            }
        }

        LaunchedEffect(locationPermissionState.value) {
            hasLocationPermission = locationPermissionState.value
            if (hasLocationPermission) {
                updateMapToCurrentLocation(cameraPositionState)
            }
        }

        val notesWithLocation = notesList.filter { it.hasLocation }

        LaunchedEffect(notesWithLocation) {
            if (notesWithLocation.isNotEmpty()) {
                val mostRecentNote = notesWithLocation.maxByOrNull { it.name }
                mostRecentNote?.let { note ->
                    if (note.latitude != null && note.longitude != null) {
                        val latLng = LatLng(note.latitude!!, note.longitude!!)
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Plant Health Map",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!hasLocationPermission) {
                Button(
                    onClick = { 
                        locationPermissionState.launchPermissionRequest() 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text("Grant Location Permission")
                }
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true,
                    mapToolbarEnabled = true
                )
            ) {
                for (note in notesWithLocation) {
                    if (note.latitude != null && note.longitude != null) {
                        val position = LatLng(note.latitude!!, note.longitude!!)
                        val markerColor = when (note.healthStatus) {
                            UserData.HealthStatus.HEALTHY -> BitmapDescriptorFactory.HUE_GREEN
                            UserData.HealthStatus.NEEDS_ATTENTION -> BitmapDescriptorFactory.HUE_YELLOW
                            UserData.HealthStatus.CRITICAL -> BitmapDescriptorFactory.HUE_RED
                        }
                        
                        MarkerInfoWindow(
                            state = rememberMarkerState(position = position),
                            title = note.description,
                            snippet = "Captured: ${note.timestamp}",
                            icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                            onClick = {
                                note.imageName?.let { imageName ->
                                    val intent = Intent(this@MainActivity, ProcessedImageActivity::class.java).apply {
                                        putExtra("IMAGE_KEY", imageName)
                                        putExtra("IMAGE_NAME", note.name)
                                    }
                                    startActivity(intent)
                                }
                                true
                            }
                        ) { marker ->
                            Column(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = marker.title ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = marker.snippet ?: "No details",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Health: ${note.healthStatus.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (note.healthStatus) {
                                        UserData.HealthStatus.HEALTHY -> Color(0xFF4CAF50)
                                        UserData.HealthStatus.NEEDS_ATTENTION -> Color(0xFFFF9800)
                                        UserData.HealthStatus.CRITICAL -> Color(0xFFF44336)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Plant Health Summary",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Total locations: ${notesWithLocation.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    val healthyCnt = notesWithLocation.count { it.healthStatus == UserData.HealthStatus.HEALTHY }
                    val attentionCnt = notesWithLocation.count { it.healthStatus == UserData.HealthStatus.NEEDS_ATTENTION }
                    val criticalCnt = notesWithLocation.count { it.healthStatus == UserData.HealthStatus.CRITICAL }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatusIndicator("Healthy", healthyCnt, Color(0xFF4CAF50))
                        StatusIndicator("Attention", attentionCnt, Color(0xFFFF9800))
                        StatusIndicator("Critical", criticalCnt, Color(0xFFF44336))
                    }
                }
            }
        }
    }
    
    @Composable
    private fun StatusIndicator(label: String, count: Int, color: Color) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$label: $count",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateMapToCurrentLocation(cameraPositionState: CameraPositionState) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        try {
            if (locationPermission) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        Log.d(TAG, "Last known location found: ${it.latitude}, ${it.longitude}")
                        val latLng = LatLng(it.latitude, it.longitude)
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                    }
                }

                val priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                val cancellationSource = com.google.android.gms.tasks.CancellationTokenSource()
                
                fusedLocationClient.getCurrentLocation(priority, cancellationSource.token)
                    .addOnSuccessListener { location ->
                        location?.let {
                            Log.d(TAG, "Current location found: ${it.latitude}, ${it.longitude}")
                            val latLng = LatLng(it.latitude, it.longitude)
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error getting current location", e)
                        Toast.makeText(
                            this,
                            "Couldn't get current location. Using default location.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Log.d(TAG, "Location permission not granted")
                Toast.makeText(
                    this,
                    "Location permission not granted. Map will use default location.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map location", e)
        }
    }

    @Composable
    fun rememberPermissionState(permission: String): PermissionState {
        val context = LocalContext.current
        val initiallyGranted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        val permissionState = remember {
            PermissionState(
                permission = permission,
                isGranted = initiallyGranted
            )
        }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            permissionState.isGranted = isGranted
            Log.d(TAG, "Permission $permission result: $isGranted")

            if (isGranted) {
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> {
                        Log.d(TAG, "Location permission granted, updating map")
                    }
                    Manifest.permission.CAMERA -> {
                        Log.d(TAG, "Camera permission granted")
                    }
                }
            }
        }

        permissionState.launcher = launcher

        LaunchedEffect(Unit) {
            val currentlyGranted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            if (currentlyGranted != permissionState.isGranted) {
                permissionState.isGranted = currentlyGranted
            }
        }

        return permissionState
    }

    class PermissionState(
        val permission: String,
        isGranted: Boolean = false
    ) {
        var isGranted by mutableStateOf(isGranted)
        internal lateinit var launcher: androidx.activity.result.ActivityResultLauncher<String>
        val value: Boolean get() = isGranted
        fun launchPermissionRequest() {
            if (!isGranted && ::launcher.isInitialized) {
                launcher.launch(permission)
            }
        }
    }

    @Composable
    fun MyAppTheme(useDarkTheme: Boolean = isSystemInDarkTheme(),
                   content: @Composable () -> Unit) {
        val colors = if (!useDarkTheme) {
            LightColorScheme
        } else {
            DarkColorScheme
        }

        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }

    private var recyclerViewAdapter: NoteRecyclerViewAdapter? = null
    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.setHasFixedSize(false)
        val gridLayoutManager = GridLayoutManager(this, 3)
        gridLayoutManager.initialPrefetchItemCount = 20
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.recycledViewPool.clear()
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 0)

        if (recyclerViewAdapter == null) {
            Log.d(TAG, "Creating new adapter instance")
            recyclerViewAdapter = NoteRecyclerViewAdapter(mutableListOf())

            UserData.notes().observe(this) { notes ->
                Log.d(TAG, "Note observer received ${notes.size} notes")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    recyclerViewAdapter?.updateNotes(notes)
                    recyclerView.invalidate()
                }
            }
        }

        recyclerView.adapter = recyclerViewAdapter
        recyclerView.setItemViewCacheSize(100)
        recyclerView.itemAnimator = null
        recyclerView.post {
            recyclerView.invalidate()
            recyclerView.requestLayout()
        }
    }

    private val DarkColorScheme = darkColorScheme(
        primary = Color(0xFFBB86FC),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF3700B3),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF03DAC6),
        onSecondary = Color.Black,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        error = Color(0xFFCF6679),
        onError = Color.Black,
        surfaceVariant = Color(0xFF3F3F3F),
        onSurfaceVariant = Color.White
    )

    private val LightColorScheme = lightColorScheme(
        primary = Color(0xFFBB86FC),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF3700B3),
        onPrimaryContainer = Color.Black,
        secondary = Color(0xFF03DAC6),
        onSecondary = Color.Black,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        error = Color(0xFFCF6679),
        onError = Color.White,
        surfaceVariant = Color(0xFF484848),
        onSurfaceVariant = Color.Black
    )

    private fun runOnUiThread(action: () -> Unit) {
        if (android.os.Looper.getMainLooper() == android.os.Looper.myLooper()) {
            action()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(action)
        }
    }
}
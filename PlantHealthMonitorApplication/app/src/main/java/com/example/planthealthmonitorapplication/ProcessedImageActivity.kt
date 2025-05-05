package com.example.planthealthmonitorapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.planthealthmonitorapplication.databinding.ActivityProcessedImageBinding

class ProcessedImageActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProcessedImageBinding
    private val TAG = "ProcessedImageActivity"
    private var processedImageUrl: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessedImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up back button
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // Set up share button
        binding.shareButton.setOnClickListener {
            shareAnalysis()
        }
        
        // Get the image key from the intent
        val imageKey = intent.getStringExtra("IMAGE_KEY")
        val imageName = intent.getStringExtra("IMAGE_NAME") ?: "Plant Analysis"
        
        // Set the image name/title
        binding.imageTitleText.text = imageName
        
        if (imageKey == null) {
            // Show error if no image key was provided
            showError("No image key provided")
            return
        }
        
        // Log the image key for debugging
        Log.d(TAG, "Received image key from intent: $imageKey")
        
        // Show loading indicator
        showLoading(true)
        
        // Load original image first as fallback
        loadOriginalImage(imageKey)
        
        // Get the current user's identity ID and then load the processed image
        Backend.getCurrentUserIdentityId { identityId ->
            if (identityId != null) {
                // Now that we have the user identity, load the processed image
                tryLoadProcessedImage(imageKey, identityId)
            } else {
                // Failed to get identity ID, still try with the best guess from the key
                Log.e(TAG, "Failed to get identity ID, falling back to key parsing")
                tryLoadProcessedImageWithoutIdentityId(imageKey)
            }
        }
    }
    
    private fun shareAnalysis() {
        if (processedImageUrl.isNotEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Plant Analysis from EdenScope")
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out my plant analysis from EdenScope!\n\n$processedImageUrl")
            startActivity(Intent.createChooser(shareIntent, "Share Analysis"))
        } else {
            Toast.makeText(this, "Analysis not ready for sharing yet", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun tryLoadProcessedImage(imageKey: String, identityId: String) {
        Log.d(TAG, "Trying to load processed image for key: $imageKey with identity ID: $identityId")
        
        // The S3 bucket and AWS backend will handle the path mapping and file transformation
        // The URL transformation happens inside getImageUrlAsync when processed=true
        val processedKey = imageKey
        
        Log.d(TAG, "Using original image key (URL will be transformed): $processedKey")
        
        // Use the non-blocking async method to get the URL
        Backend.getImageUrlAsync(imageKey, processed = true) { amplifyUrl ->
            if (amplifyUrl.isNotEmpty()) {
                Log.d(TAG, "Received Amplify URL for processed image: $amplifyUrl")
                this.processedImageUrl = amplifyUrl
                
                // Create request options
                val requestOptions = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.ic_launcher_background)
                
                // Load the processed image
                runOnUiThread {
                    Glide.with(this)
                        .load(amplifyUrl)
                        .apply(requestOptions)
                        .into(binding.processedImageView)
                    
                    // Update status text and hide progress bar
                    binding.analysisStatusText.text = "Analysis Complete"
                    binding.analysisStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                    showLoading(false)
                    
                    // Enable share button
                    binding.shareButton.isEnabled = true
                }
            } else {
                Log.e(TAG, "Failed to get URL for processed image")
                runOnUiThread {
                    binding.analysisStatusText.text = "Processing..."
                    binding.analysisStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))
                    showLoading(false)
                }
            }
        }
    }
    
    // Fallback method if we can't get the identity ID
    private fun tryLoadProcessedImageWithoutIdentityId(imageKey: String) {
        Log.d(TAG, "Trying to load processed image without identity ID for key: $imageKey")
        
        // Use the async version that doesn't block the UI thread
        Backend.getImageUrlAsync(imageKey, processed = true) { amplifyUrl ->
            if (amplifyUrl.isNotEmpty()) {
                Log.d(TAG, "Received Amplify URL for processed image (fallback): $amplifyUrl")
                this.processedImageUrl = amplifyUrl
                
                // Create request options
                val requestOptions = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.ic_launcher_background)
                
                // Load the image with Glide
                runOnUiThread {
                    Glide.with(this)
                        .load(amplifyUrl)
                        .apply(requestOptions)
                        .into(binding.processedImageView)
                    
                    // Update status text
                    binding.analysisStatusText.text = "Analysis Complete"
                    binding.analysisStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                    showLoading(false)
                    
                    // Enable share button
                    binding.shareButton.isEnabled = true
                }
            } else {
                Log.e(TAG, "Failed to get URL for processed image (fallback)")
                runOnUiThread {
                    binding.analysisStatusText.text = "Processing..."
                    binding.analysisStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))
                    showLoading(false)
                }
            }
        }
    }
    
    private fun loadOriginalImage(imageKey: String) {
        Log.d(TAG, "Loading original image as fallback: $imageKey")
        
        // Get the URL for the original image using async method
        Backend.getImageUrlAsync(imageKey, processed = false) { originalImageUrl ->
            if (originalImageUrl.isEmpty()) {
                runOnUiThread {
                    binding.processedImageView.setImageResource(R.drawable.ic_launcher_background)
                    showLoading(false)
                }
                return@getImageUrlAsync
            }
            
            // Setup Glide request options
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()  // Changed to centerCrop to fill the collapsing toolbar
                .error(R.drawable.ic_launcher_background)
            
            // Load the image with Glide on the UI thread
            runOnUiThread {
                Glide.with(this)
                    .load(originalImageUrl)
                    .apply(requestOptions)
                    .into(binding.processedImageView)
                
                binding.analysisStatusText.text = "Analyzing..."
            }
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.analysisStatusText.text = message
        binding.analysisStatusText.setTextColor(getColor(android.R.color.holo_red_light))
        showLoading(false)
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.shareButton.isEnabled = !isLoading
    }
}
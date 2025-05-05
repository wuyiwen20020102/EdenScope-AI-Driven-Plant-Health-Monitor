package com.example.planthealthmonitorapplication

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlin.concurrent.thread

// this is a single cell (row) in the list of Notes
class NoteRecyclerViewAdapter(
    private val values: MutableList<UserData.Note>?) :
    RecyclerView.Adapter<NoteRecyclerViewAdapter.ViewHolder>() {

    // Create RequestOptions once to be reused
    private val requestOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        // Remove placeholder to prevent flickering
        //.placeholder(R.drawable.ic_launcher_background)
        .override(300, 300) // Resize images to this size (adjust based on your UI needs)
        .dontAnimate() // Disable animations to prevent flickering

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_image, parent, false)
        return ViewHolder(view)
    }

    // Cache for loaded images to prevent flickering
    private val imageCache = mutableMapOf<String, Bitmap>()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = values?.get(position)
        if (note != null) {
            // Set tag for identifying this view
            holder.imageView.tag = note.id
            
            // Check if we have this image in our cache - fastest option
            val cachedBitmap = imageCache[note.id]
            if (cachedBitmap != null) {
                // Use cached bitmap directly - immediate display with no flicker
                holder.imageView.setImageBitmap(cachedBitmap)
            } else if (note.image != null) {
                // Cache the bitmap for future use
                imageCache[note.id] = note.image!!
                
                // Load directly from bitmap - skip Glide to avoid flicker
                holder.imageView.setImageBitmap(note.image)
            } else {
                val imageName = note.imageName
                if (imageName != null) {
                    // Get URL in a more lightweight way
                    val url = Backend.getImageUrl(imageName)
                    
                    // Simpler approach without problematic listener
                    Glide.with(holder.imageView.context)
                        .load(url)
                        .apply(requestOptions)
                        .skipMemoryCache(false) // Use memory cache
                        .into(holder.imageView)
                        
                    // Pre-cache bitmap in the background
                    thread {
                        try {
                            // Use Backend method directly to get the bitmap
                            Backend.retrieveImage(imageName) { bitmap ->
                                if (bitmap != null) {
                                    // Cache the bitmap for future use
                                    imageCache[note.id] = bitmap
                                    Log.d("NoteRecyclerAdapter", "Cached bitmap for ${note.id}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NoteRecyclerAdapter", "Failed to cache bitmap: ${e.message}")
                        }
                    }
                } else {
                    // Only set the background drawable if absolutely necessary
                    if (holder.imageView.drawable == null) {
                        holder.imageView.setImageResource(R.drawable.ic_launcher_background)
                    }
                }
            }
            
            // IMPORTANT: Always set the click listener regardless of image source
            // Set an on-click listener to open the processed image view
            holder.imageView.setOnClickListener { view ->
                Log.d("NoteRecyclerAdapter", "Image clicked, imageName: ${note.imageName}")
                note.imageName?.let { imageName ->
                    // Start the ProcessedImageActivity
                    val intent = Intent(view.context, ProcessedImageActivity::class.java).apply {
                        putExtra("IMAGE_KEY", imageName)
                        putExtra("IMAGE_NAME", note.name)
                    }
                    view.context.startActivity(intent)
                } ?: run {
                    // If no image name, show a toast message
                    Toast.makeText(view.context, "No image available to analyze", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Set a long-click listener to show a popup menu for deletion
            holder.imageView.setOnLongClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.image_popup_menu, popup.menu)
                
                // Add "View Analysis" option to the menu
                popup.menu.add(R.string.view_analysis).setOnMenuItemClickListener {
                    note.imageName?.let { imageName ->
                        // Start the ProcessedImageActivity
                        val intent = Intent(view.context, ProcessedImageActivity::class.java).apply {
                            putExtra("IMAGE_KEY", imageName)
                            putExtra("IMAGE_NAME", note.name)
                        }
                        view.context.startActivity(intent)
                    } ?: run {
                        // If no image name, show a toast message
                        Toast.makeText(view.context, "No image available to analyze", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                
                popup.setOnMenuItemClickListener { menuItem ->
                    if (menuItem.itemId == R.id.action_delete) {
                        val pos = holder.adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            val noteToDelete = values?.get(pos)
                            // We no longer remove from values array here, since Backend.deleteNote will handle it
                            // but we still update the UI immediately
                            notifyItemRemoved(pos)
                            noteToDelete?.let {
                                // Clear Glide image cache when image is deleted
                                Glide.with(holder.imageView.context).clear(holder.imageView)
                                
                                Backend.deleteNote(it)
                                it.imageName?.let { imageName ->
                                    Backend.deleteImage(imageName)
                                }
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                popup.show()
                true
            }
        }
    }

    override fun getItemCount() = values?.size ?: 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image)
    }
    
    // Helper method to set a new list of notes
    fun updateNotes(newNotes: List<UserData.Note>) {
        values?.clear()
        values?.addAll(newNotes)
        notifyDataSetChanged()
    }
}
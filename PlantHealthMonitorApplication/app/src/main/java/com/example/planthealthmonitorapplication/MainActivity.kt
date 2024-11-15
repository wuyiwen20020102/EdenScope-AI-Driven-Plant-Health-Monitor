package com.example.planthealthmonitorapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.planthealthmonitorapplication.databinding.ActivityMainBinding
import com.example.planthealthmonitorapplication.databinding.ContentMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var contentBinding: ContentMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // prepare our List view and RecyclerView (cells)
        contentBinding = ContentMainBinding.bind(binding.root.findViewById(R.id.content_main))
        setupRecyclerView(contentBinding.itemList)

        // Set up the authentication button and observer for sign-in status
        setupAuthButton(UserData)

        UserData.isSignedIn.observe(this, Observer<Boolean> { isSignedIn ->
            Log.i(TAG, "isSignedIn changed : $isSignedIn")
            if (isSignedIn) {
                binding.fabAuth.setImageResource(R.drawable.ic_baseline_lock_open)
                Log.d(TAG, "Showing fabADD")
                binding.fabAdd.show()
                binding.fabAdd.animate().translationY(0.0F - 1.1F * binding.fabAuth.customSize)
            } else {
                binding.fabAuth.setImageResource(R.drawable.ic_baseline_lock)
                Log.d(TAG, "Hiding fabADD")
                binding.fabAdd.hide()
                binding.fabAdd.animate().translationY(0.0F)
            }
        })

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(
        "super.onActivityResult(requestCode, resultCode, data)",
        "androidx.appcompat.app.AppCompatActivity"
    )
    )
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    // recycler view is the list of cells
    private fun setupRecyclerView(recyclerView: RecyclerView) {

        // update individual cell when the Note data are modified
        UserData.notes().observe(this, Observer<MutableList<UserData.Note>> { notes ->
            Log.d(TAG, "Note observer received ${notes.size} notes")

            // let's create a RecyclerViewAdapter that manages the individual cells
            recyclerView.adapter = NoteRecyclerViewAdapter(notes)
        })

        // add a touch gesture handler to manager the swipe to delete gesture
        val itemTouchHelper = ItemTouchHelper(SwipeCallback(this))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // Sets up the floating action button (fabAuth) for authentication
    private fun setupAuthButton(userData: UserData) {
        binding.fabAuth.setOnClickListener { view ->
            val authButton = view as FloatingActionButton
            if (userData.isSignedIn.value == true) {
                authButton.setImageResource(R.drawable.ic_baseline_lock)
                Backend.signOut()
            } else {
                authButton.setImageResource(R.drawable.ic_baseline_lock_open)
                Backend.signIn(this)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
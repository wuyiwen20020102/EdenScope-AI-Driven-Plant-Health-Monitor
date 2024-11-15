package com.example.planthealthmonitorapplication

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.api.graphql.model.ModelMutation
import com.amplifyframework.api.graphql.model.ModelQuery
import com.amplifyframework.auth.AuthChannelEventName
import com.amplifyframework.auth.AuthException
import com.amplifyframework.core.Amplify
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.cognito.result.AWSCognitoAuthSignOutResult
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.AuthSessionResult
import com.amplifyframework.core.InitializationStatus
import com.amplifyframework.datastore.generated.model.NoteData
import com.amplifyframework.hub.HubChannel
import com.amplifyframework.hub.HubEvent
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.options.StorageDownloadFileOptions
import com.amplifyframework.storage.options.StorageRemoveOptions
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.amplifyframework.storage.s3.AWSS3StoragePlugin
import java.io.File
import java.io.FileInputStream

object Backend {
    private const val TAG = "Backend"

    fun initialize(applicationContext: Context) : Backend {
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSApiPlugin())
            Amplify.addPlugin(AWSS3StoragePlugin())

            Amplify.configure(applicationContext)
            Log.i(TAG, "Initialized Amplify")
        } catch (e: AmplifyException) {
            Log.e(TAG, "Could not initialize Amplify", e)
        }

        Log.i(TAG, "registering hub event")

        // Listen to auth events
        Amplify.Hub.subscribe(HubChannel.AUTH) { hubEvent: HubEvent<*> ->
            when (hubEvent.name) {
                InitializationStatus.SUCCEEDED.toString() -> {
                    Log.i(TAG, "Amplify successfully initialized")
                }
                InitializationStatus.FAILED.toString() -> {
                    Log.i(TAG, "Amplify initialization failed")
                }
                else -> {
                    when (AuthChannelEventName.valueOf(hubEvent.name)) {
                        AuthChannelEventName.SIGNED_IN -> {
                            updateUserData(true)
                            Log.i(TAG, "HUB : SIGNED_IN")
                        }
                        AuthChannelEventName.SIGNED_OUT -> {
                            updateUserData(false)
                            Log.i(TAG, "HUB : SIGNED_OUT")
                        }
                        else -> Log.i(TAG, """HUB EVENT:${hubEvent.name}""")
                    }
                }
            }
        }

        Log.i(TAG, "retrieving session status")

        // Check if user is already authenticated from a previous execution
        Amplify.Auth.fetchAuthSession(
            { result ->
                Log.i(TAG, result.toString())
                val cognitoAuthSession = result as AWSCognitoAuthSession
                // Update UI based on authentication status
                this.updateUserData(cognitoAuthSession.isSignedIn)
                val identityIdResult = cognitoAuthSession.identityIdResult
                if (identityIdResult.type == AuthSessionResult.Type.SUCCESS) {
                    val identityId = identityIdResult.value
                    Log.i(TAG, "IdentityId: $identityId")
                } else {
                    val error = identityIdResult.error
                    Log.i(TAG, "IdentityId not present because: $error")
                }
            },
            { error -> Log.i(TAG, error.toString()) }
        )

        return this
    }

    private fun updateUserData(withSignedInStatus : Boolean) {
        UserData.setSignedIn(withSignedInStatus)

        val notes = UserData.notes().value
        val isEmpty = notes?.isEmpty() ?: true

        // Query notes when signed in and we do not have notes yet
        if (withSignedInStatus && isEmpty) {
            this.queryNotes()
        } else {
            UserData.resetNotes()
        }
    }

    fun signOut() {
        Log.i(TAG, "Initiate Signout Sequence")

        Amplify.Auth.signOut { result ->
            when (result) {
                is AWSCognitoAuthSignOutResult.CompleteSignOut -> {
                    // Sign-out completed fully and without errors.
                    Log.i(TAG, "Signed out successfully!")
                    // Update user data or UI as needed
                    updateUserData(false)
                }
                is AWSCognitoAuthSignOutResult.PartialSignOut -> {
                    // Sign-out completed with some errors.
                    Log.w(TAG, "Signed out with partial success.")
                    result.hostedUIError?.let {
                        Log.e(TAG, "HostedUI Error", it.exception)
                    }
                    result.globalSignOutError?.let {
                        Log.e(TAG, "GlobalSignOut Error", it.exception)
                    }
                    result.revokeTokenError?.let {
                        Log.e(TAG, "RevokeToken Error", it.exception)
                    }
                    // You may still want to update user data
                    updateUserData(false)
                }
                is AWSCognitoAuthSignOutResult.FailedSignOut -> {
                    // Sign-out failed, user is still signed in.
                    Log.e(TAG, "Sign out failed", result.exception)
                    // Optionally, handle the failure (e.g., show an error message)
                }
            }
        }
    }

    fun signIn(callingActivity: Activity) {
        Log.i(TAG, "Initiate Signin Sequence")

        Amplify.Auth.signInWithWebUI(
            callingActivity,
            { result: AuthSignInResult -> Log.i(TAG, result.toString()) },
            { error: AuthException -> Log.e(TAG, error.toString()) }
        )
    }

    fun queryNotes() {
        Log.i(TAG, "Querying notes")

        Amplify.API.query(
            ModelQuery.list(NoteData::class.java),
            { response ->
                Log.i(TAG, "Queried")
                for (noteData in response.data) {
                    Log.i(TAG, noteData.name)
                    // Add all notes to UserData
                    UserData.addNote(UserData.Note.from(noteData))
                }
            },
            { error -> Log.e(TAG, "Query failure", error) }
        )
    }

    fun createNote(note: UserData.Note) {
        Log.i(TAG, "Creating note")

        Amplify.API.mutate(
            ModelMutation.create(note.data),
            { response ->
                if (response.hasErrors()) {
                    Log.e(TAG, response.errors.first().message)
                } else {
                    Log.i(TAG, "Created Note with id: ${response.data.id}")
                    // Optionally add the note to UserData
                    // UserData.addNote(note)
                }
            },
            { error -> Log.e(TAG, "Create failed", error) }
        )
    }

    fun deleteNote(note: UserData.Note?) {
        if (note == null) return

        Log.i(TAG, "Deleting note ${note.id}")

        Amplify.API.mutate(
            ModelMutation.delete(note.data),
            { response ->
                if (response.hasErrors()) {
                    Log.e(TAG, response.errors.first().message)
                } else {
                    Log.i(TAG, "Deleted Note with id: ${response.data.id}")
                    // Optionally remove the note from UserData
                    // UserData.deleteNoteById(note.id)
                }
            },
            { error -> Log.e(TAG, "Delete failed", error) }
        )
    }

    fun storeImage(filePath: String, key: String) {
        val file = File(filePath)
        val options = StorageUploadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        Amplify.Storage.uploadFile(
            key,
            file,
            options,
            { progress -> Log.i(TAG, "Fraction completed: ${progress.fractionCompleted}") },
            { result -> Log.i(TAG, "Successfully uploaded: " + result.key) },
            { error -> Log.e(TAG, "Upload failed", error) }
        )
    }

    fun deleteImage(key : String) {

        val options = StorageRemoveOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        Amplify.Storage.remove(
            key,
            options,
            { result -> Log.i(TAG, "Successfully removed: " + result.key) },
            { error -> Log.e(TAG, "Remove failure", error) }
        )
    }

    fun retrieveImage(key: String, completed : (image: Bitmap) -> Unit) {
        val options = StorageDownloadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        val file = File.createTempFile("image", ".image")

        Amplify.Storage.downloadFile(
            key,
            file,
            options,
            { progress -> Log.i(TAG, "Fraction completed: ${progress.fractionCompleted}") },
            { result ->
                Log.i(TAG, "Successfully downloaded: ${result.file.name}")
                val imageStream = FileInputStream(file)
                val image = BitmapFactory.decodeStream(imageStream)
                completed(image)
            },
            { error -> Log.e(TAG, "Download Failure", error) }
        )
    }
}



package com.example.planthealthmonitorapplication

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amplifyframework.datastore.generated.model.NoteData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// a singleton to hold user data (this is a ViewModel pattern, without inheriting from ViewModel)
object UserData {

    private const val TAG = "UserData"

    //
    // observable properties
    //

    // signed in status
    private val _isSignedIn = MutableLiveData(false)
    var isSignedIn: LiveData<Boolean> = _isSignedIn

    fun setSignedIn(newValue : Boolean) {
        // use postvalue() to make the assignation on the main (UI) thread
        _isSignedIn.postValue(newValue)
    }

    // the notes
    private val _notes = MutableLiveData<MutableList<Note>>(mutableListOf())
    
    // plant health data
    private val _healthIssues = MutableLiveData<MutableList<HealthIssue>>(mutableListOf())
    private val _treatments = MutableLiveData<MutableList<Treatment>>(mutableListOf())
    private val _healthStatistics = MutableLiveData(HealthStatistics())
    private val _savedItems = MutableLiveData<MutableList<String>>(mutableListOf())

    // please check https://stackoverflow.com/questions/47941537/notify-observer-when-item-is-added-to-list-of-livedata
    private fun <T> MutableLiveData<T>.notifyObserver() {
        // Always use postValue for consistent behavior and to ensure updates are processed
        // even if multiple updates are triggered in quick succession
        val currentValue = this.value
        this.postValue(currentValue)
    }
    
    fun notifyObserver() {
        // Get current values first
        val currentNotes = _notes.value ?: mutableListOf()
        val currentTreatments = _treatments.value ?: mutableListOf()
        val currentHealthIssues = _healthIssues.value ?: mutableListOf()
        val currentSavedItems = _savedItems.value ?: mutableListOf()
        
        // Post all values to ensure immediate updates across the UI
        _notes.postValue(currentNotes)
        _treatments.postValue(currentTreatments)
        _healthIssues.postValue(currentHealthIssues)
        _savedItems.postValue(currentSavedItems)
        
        // Update health statistics after everything else
        updateHealthStatistics()
    }

    fun notes() : LiveData<MutableList<Note>>  = _notes
    fun healthIssues(): LiveData<MutableList<HealthIssue>> = _healthIssues
    fun treatments(): LiveData<MutableList<Treatment>> = _treatments
    fun healthStatistics(): LiveData<HealthStatistics> = _healthStatistics
    fun savedItems(): LiveData<MutableList<String>> = _savedItems

    fun addNote(n : Note) {
        val notes = _notes.value
        if (notes != null) {
            // Check if note with same ID already exists
            val existingIndex = notes.indexOfFirst { it.id == n.id }
            if (existingIndex >= 0) {
                // Replace existing note
                notes[existingIndex] = n
            } else {
                // Add new note
                notes.add(n)
            }
            _notes.notifyObserver()
            
            // Process the note to extract plant health data
            processPlantHealthData(n)
        } else {
            Log.e(TAG, "addNote : note collection is null !!")
        }
    }
    
    private fun processPlantHealthData(note: Note) {
        // Access the processed image if available
        if (note.imageName != null) {
            analyzeProcessedImage(note)
        } else {
            // Default to healthy if no image
            note.healthStatus = HealthStatus.HEALTHY
            updateHealthStatistics()
        }
    }
    
    private fun analyzeProcessedImage(note: Note) {
        // If we already have the image loaded, analyze it immediately
        if (note.image != null) {
            determineHealthStatusFromImage(note, note.image!!)
            return
        }
        
        // Otherwise, if we only have the image name, retrieve the processed version
        if (note.imageName != null) {
            // Check if processed image exists
            Backend.checkProcessedImageExists(note.imageName!!) { exists ->
                if (exists) {
                    Log.d(TAG, "Processed image exists, retrieving for analysis: ${note.imageName}")
                    // Get the processed image for analysis
                    Backend.retrieveImage(note.imageName!!) { bitmap ->
                        determineHealthStatusFromImage(note, bitmap)
                    }
                } else {
                    Log.d(TAG, "Processed image doesn't exist yet, using original: ${note.imageName}")
                    // Fallback to using original image
                    Backend.retrieveImage(note.imageName!!) { bitmap ->
                        note.image = bitmap
                        note.healthStatus = HealthStatus.HEALTHY // Default to healthy when using original
                        updateHealthStatistics()
                    }
                }
            }
        } else {
            // No image to analyze, default to healthy
            note.healthStatus = HealthStatus.HEALTHY
            updateHealthStatistics()
        }
    }
    
    private fun determineHealthStatusFromImage(note: Note, bitmap: Bitmap) {
        Log.d(TAG, "Analyzing image for health issues: ${note.imageName}")
        
        // Store the bitmap in the note for display
        note.image = bitmap
        
        // Clear previous health issues for this note
        _healthIssues.value?.removeAll { it.noteId == note.id }
        _treatments.value?.removeAll { it.noteId == note.id }
        
        // Map diseases to severity levels
        val diseaseSeverityMap = mapOf(
            "healthy/minor_issue" to HealthStatus.HEALTHY,
            
            // Medium severity diseases
            "Scab Leaf" to HealthStatus.NEEDS_ATTENTION,
            "leaf spot" to HealthStatus.NEEDS_ATTENTION,
            "Gray leaf spot" to HealthStatus.NEEDS_ATTENTION,
            "Powdery mildew leaf" to HealthStatus.NEEDS_ATTENTION,
            "Septoria leaf spot" to HealthStatus.NEEDS_ATTENTION,
            "mold leaf" to HealthStatus.NEEDS_ATTENTION,
            "two spotted spider mites leaf" to HealthStatus.NEEDS_ATTENTION,
            "leaf mosaic virus" to HealthStatus.NEEDS_ATTENTION,
            "nutrient deficiency" to HealthStatus.NEEDS_ATTENTION,
            "water stress" to HealthStatus.NEEDS_ATTENTION,
            
            // High severity diseases
            "rust leaf" to HealthStatus.CRITICAL,
            "leaf blight" to HealthStatus.CRITICAL,
            "leaf early blight" to HealthStatus.CRITICAL,
            "leaf late blight" to HealthStatus.CRITICAL,
            "Early blight leaf" to HealthStatus.CRITICAL,
            "leaf bacterial spot" to HealthStatus.CRITICAL,
            "leaf yellow virus" to HealthStatus.CRITICAL,
            "leaf black rot" to HealthStatus.CRITICAL,
            "severe nutrient deficiency" to HealthStatus.CRITICAL,
            "severe water stress" to HealthStatus.CRITICAL
        )
        
        // Map for disease type classification
        val diseaseTypeMap = mapOf(
            // Disease types
            "Scab Leaf" to HealthIssueType.DISEASE,
            "rust leaf" to HealthIssueType.DISEASE,
            "leaf spot" to HealthIssueType.DISEASE,
            "Gray leaf spot" to HealthIssueType.DISEASE,
            "leaf blight" to HealthIssueType.DISEASE,
            "leaf early blight" to HealthIssueType.DISEASE,
            "leaf late blight" to HealthIssueType.DISEASE,
            "Powdery mildew leaf" to HealthIssueType.DISEASE,
            "Early blight leaf" to HealthIssueType.DISEASE,
            "Septoria leaf spot" to HealthIssueType.DISEASE,
            "leaf bacterial spot" to HealthIssueType.DISEASE,
            "mold leaf" to HealthIssueType.DISEASE,
            "leaf black rot" to HealthIssueType.DISEASE,
            "leaf mosaic virus" to HealthIssueType.DISEASE,
            "leaf yellow virus" to HealthIssueType.DISEASE,
            
            // Pest types
            "two spotted spider mites leaf" to HealthIssueType.PEST,
            "aphid infestation" to HealthIssueType.PEST,
            "whitefly infestation" to HealthIssueType.PEST,
            
            // Nutrient types
            "nutrient deficiency" to HealthIssueType.NUTRIENT,
            "severe nutrient deficiency" to HealthIssueType.NUTRIENT,
            "nitrogen deficiency" to HealthIssueType.NUTRIENT,
            "phosphorus deficiency" to HealthIssueType.NUTRIENT,
            "potassium deficiency" to HealthIssueType.NUTRIENT,
            "iron deficiency" to HealthIssueType.NUTRIENT,
            
            // Water stress types
            "water stress" to HealthIssueType.WATER,
            "severe water stress" to HealthIssueType.WATER,
            "overwatering" to HealthIssueType.WATER,
            "underwatering" to HealthIssueType.WATER
        )
        
        // Rather than analyzing the image ourselves, we'll check the filename pattern to
        // see if we can extract disease information from there
        val imagePath = note.imageName ?: ""
        
        // Get the backend to provide the image analysis results
        // This would normally be done through a proper API, but we'll simulate it for now
        Backend.getImageAnalysisResults(imagePath) { analysisResults ->
            runOnUiThread {
                processAnalysisResults(note, analysisResults, diseaseSeverityMap, diseaseTypeMap)
            }
        }
        
        // Default to healthy until we get the analysis results
        note.healthStatus = HealthStatus.HEALTHY
        
        // Update UI
        notifyObserver()
        updateHealthStatistics()
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
    
    private fun processAnalysisResults(
        note: Note, 
        analysisResults: List<String>, 
        diseaseSeverityMap: Map<String, HealthStatus>,
        diseaseTypeMap: Map<String, HealthIssueType>
    ) {
        Log.d(TAG, "Processing analysis results: $analysisResults")
        
        // Skip if no results
        if (analysisResults.isEmpty()) {
            Log.d(TAG, "No analysis results found, keeping as healthy")
            return
        }
        
        // Count occurrences of each disease label
        val labelCounts = mutableMapOf<String, Int>()
        for (label in analysisResults) {
            labelCounts[label] = labelCounts.getOrDefault(label, 0) + 1
        }
        
        // Start with the assumption the plant is healthy
        var worstStatus = HealthStatus.HEALTHY
        
        // Process each detected disease label
        for ((label, count) in labelCounts) {
            val status = diseaseSeverityMap[label] ?: HealthStatus.NEEDS_ATTENTION
            
            // Update to the worst status found
            if (status.ordinal > worstStatus.ordinal) {
                worstStatus = status
            }
            
            // Skip creating health issues for healthy plants
            if (label == "healthy/minor_issue") continue
            
            // Create a health issue for each detected disease
            val issueType = diseaseTypeMap[label] ?: HealthIssueType.DISEASE
            val severity = when (status) {
                HealthStatus.CRITICAL -> IssueSeverity.HIGH
                HealthStatus.NEEDS_ATTENTION -> IssueSeverity.MEDIUM
                else -> IssueSeverity.LOW
            }
            
            // Adjust severity based on count of the same issue
            val adjustedSeverity = when {
                count > 2 -> IssueSeverity.HIGH
                count > 1 -> severity
                else -> if (severity == IssueSeverity.HIGH) IssueSeverity.MEDIUM else severity
            }
            
            // Create the health issue
            createHealthIssue(note, formatDiseaseLabel(label), adjustedSeverity, issueType)
        }
        
        // Set the plant's overall health status
        note.healthStatus = worstStatus
        Log.d(TAG, "Updated health status to: ${note.healthStatus}")
        
        // Update UI
        notifyObserver()
        updateHealthStatistics()
    }
    
    // Format disease label to be more readable
    private fun formatDiseaseLabel(label: String): String {
        // Convert label like "leaf spot" to "Leaf Spot"
        return label.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun createHealthIssue(note: Note, name: String, severity: IssueSeverity, type: HealthIssueType) {
        val newIssue = HealthIssue(
            id = UUID.randomUUID().toString(),
            name = name,
            severity = severity,
            type = type,
            noteId = note.id,
            timestamp = System.currentTimeMillis()
        )
        
        // Add issue and create treatment recommendation
        _healthIssues.value?.add(newIssue)
        createTreatmentForIssue(newIssue)
        
        // Notify observers
        _healthIssues.notifyObserver()
    }
    
    private fun createTreatmentForIssue(issue: HealthIssue) {
        val treatmentText = when (issue.type) {
            HealthIssueType.DISEASE -> {
                when (issue.name) {
                    "Leaf Spot" -> "Apply copper-based fungicide to affected areas. Ensure proper air circulation."
                    "Powdery Mildew" -> "Apply neem oil or sulfur-based fungicide. Remove severely affected leaves."
                    "Root Rot" -> "Reduce watering frequency. Repot with fresh, well-draining soil if possible."
                    else -> "Identify disease and apply appropriate fungicide. Remove affected plant parts."
                }
            }
            HealthIssueType.PEST -> "Apply insecticidal soap to affected areas. Consider biological controls like beneficial insects."
            HealthIssueType.NUTRIENT -> {
                when (issue.name) {
                    "Nitrogen Deficiency" -> "Apply balanced fertilizer with increased nitrogen content."
                    "Phosphorus Deficiency" -> "Apply phosphorus-rich fertilizer. Test soil pH and adjust if needed."
                    else -> "Apply balanced fertilizer. Consider soil testing for specific deficiencies."
                }
            }
            HealthIssueType.WATER -> "Adjust watering schedule. Ensure proper drainage. Check for root health."
        }
        
        val treatment = Treatment(
            id = UUID.randomUUID().toString(),
            title = "Treat ${issue.name}",
            description = treatmentText,
            priority = issue.severity,
            isCompleted = false,
            issueId = issue.id,
            noteId = issue.noteId,
            timestamp = System.currentTimeMillis()
        )
        
        _treatments.value?.add(treatment)
        _treatments.notifyObserver()
    }
    
    private fun updateHealthStatistics() {
        // Create a new statistics object to ensure complete refresh
        val statistics = HealthStatistics()
        
        // Count issues by type - use local variables for thread safety
        val currentHealthIssues = _healthIssues.value ?: mutableListOf()
        statistics.diseaseCount = currentHealthIssues.count { it.type == HealthIssueType.DISEASE }
        statistics.pestCount = currentHealthIssues.count { it.type == HealthIssueType.PEST }
        statistics.nutrientCount = currentHealthIssues.count { it.type == HealthIssueType.NUTRIENT }
        statistics.waterCount = currentHealthIssues.count { it.type == HealthIssueType.WATER }
        
        // Count plants by health status - use local variables for thread safety
        val currentNotes = _notes.value ?: mutableListOf()
        statistics.healthyCount = currentNotes.count { it.healthStatus == HealthStatus.HEALTHY }
        statistics.needsAttentionCount = currentNotes.count { it.healthStatus == HealthStatus.NEEDS_ATTENTION }
        statistics.criticalCount = currentNotes.count { it.healthStatus == HealthStatus.CRITICAL }
        
        // Calculate health trend (simplified approach)
        val totalPlants = statistics.healthyCount + statistics.needsAttentionCount + statistics.criticalCount
        if (totalPlants > 0) {
            statistics.healthTrend = statistics.healthyCount.toFloat() / totalPlants.toFloat()
        }
        
        // Force immediate update of the statistics
        _healthStatistics.postValue(statistics)
        
        // Log the update for debugging
        Log.d(TAG, "Updated health statistics - Healthy: ${statistics.healthyCount}, " +
                "Needs Attention: ${statistics.needsAttentionCount}, " +
                "Critical: ${statistics.criticalCount}")
    }
    
    fun completeTreatment(treatmentId: String) {
        Log.d(TAG, "Completing treatment: $treatmentId")
        
        val treatments = _treatments.value ?: mutableListOf()
        val treatmentIndex = treatments.indexOfFirst { it.id == treatmentId }
        
        if (treatmentIndex >= 0) {
            // Update the treatment
            treatments[treatmentIndex].isCompleted = true
            _treatments.postValue(treatments) // Force immediate update
            
            // Update corresponding health issue
            val issueId = treatments[treatmentIndex].issueId
            val issues = _healthIssues.value ?: mutableListOf()
            val issueIndex = issues.indexOfFirst { it.id == issueId }
            
            if (issueIndex >= 0) {
                // Reduce severity of the issue
                if (issues[issueIndex].severity == IssueSeverity.HIGH) {
                    issues[issueIndex].severity = IssueSeverity.MEDIUM
                } else if (issues[issueIndex].severity == IssueSeverity.MEDIUM) {
                    issues[issueIndex].severity = IssueSeverity.LOW
                }
                _healthIssues.postValue(issues) // Force immediate update
                
                // Update note health status
                val noteId = issues[issueIndex].noteId
                val notes = _notes.value ?: mutableListOf()
                val noteIndex = notes.indexOfFirst { it.id == noteId }
                
                if (noteIndex >= 0) {
                    // Check if all treatments for this note are completed
                    val pendingTreatments = treatments.filter { 
                        it.noteId == noteId && !it.isCompleted 
                    }
                    
                    if (pendingTreatments.isEmpty()) {
                        notes[noteIndex].healthStatus = HealthStatus.HEALTHY
                    } else {
                        notes[noteIndex].healthStatus = HealthStatus.NEEDS_ATTENTION
                    }
                    _notes.postValue(notes) // Force immediate update
                }
                
                // Update health statistics
                updateHealthStatistics()
                
                // Force an immediate UI refresh for all data
                notifyObserver()
                
                // Log completion for debugging
                Log.d(TAG, "Treatment completed successfully, UI updates sent")
            }
        } else {
            Log.e(TAG, "Treatment not found: $treatmentId")
        }
    }
    
    fun saveItem(noteId: String) {
        Log.d(TAG, "Saving item: $noteId")
        
        val currentSavedItems = _savedItems.value ?: mutableListOf()
        if (!currentSavedItems.contains(noteId)) {
            // Update the saved items
            currentSavedItems.add(noteId)
            _savedItems.postValue(currentSavedItems) // Force immediate update
            
            // Log the update for debugging
            Log.d(TAG, "Item saved successfully, saved items: ${currentSavedItems.size}")
            
            // Force an immediate UI refresh for all data
            notifyObserver()
        } else {
            Log.d(TAG, "Item already saved: $noteId")
        }
    }
    
    fun unsaveItem(noteId: String) {
        Log.d(TAG, "Unsaving item: $noteId")
        
        val currentSavedItems = _savedItems.value ?: mutableListOf()
        if (currentSavedItems.remove(noteId)) {
            // Update the saved items
            _savedItems.postValue(currentSavedItems) // Force immediate update
            
            // Log the update for debugging
            Log.d(TAG, "Item unsaved successfully, saved items: ${currentSavedItems.size}")
            
            // Force an immediate UI refresh for all data
            notifyObserver()
        } else {
            Log.d(TAG, "Item not found in saved items: $noteId")
        }
    }
    
    fun addNoteBatch(notes: List<Note>) {
        val currentNotes = _notes.value
        if (currentNotes != null) {
            // Only add notes that don't already exist with the same ID
            for (note in notes) {
                val existingIndex = currentNotes.indexOfFirst { it.id == note.id }
                if (existingIndex >= 0) {
                    // Replace existing note
                    currentNotes[existingIndex] = note
                } else {
                    // Add new note
                    currentNotes.add(note)
                    // Process plant health data for new notes
                    processPlantHealthData(note)
                }
            }
            _notes.notifyObserver()
        } else {
            Log.e(TAG, "addNoteBatch : note collection is null !!")
        }
    }
    
    fun deleteNote(at: Int) : Note?  {
        val note = _notes.value?.removeAt(at)
        _notes.notifyObserver()
        
        // Clean up associated health data if note is deleted
        if (note != null) {
            // Remove health issues for this note
            _healthIssues.value?.removeAll { it.noteId == note.id }
            _healthIssues.notifyObserver()
            
            // Remove treatments for this note
            _treatments.value?.removeAll { it.noteId == note.id }
            _treatments.notifyObserver()
            
            // Remove from saved items if present
            _savedItems.value?.remove(note.id)
            _savedItems.notifyObserver()
            
            // Update health statistics
            updateHealthStatistics()
        }
        
        return note
    }
    
    fun deleteNoteById(id: String) : Note? {
        val notes = _notes.value
        val index = notes?.indexOfFirst { it.id == id } ?: -1
        val note = if (index >= 0) notes?.removeAt(index) else null
        _notes.notifyObserver()
        
        // Clean up associated health data if note is deleted
        if (note != null) {
            // Remove health issues for this note
            _healthIssues.value?.removeAll { it.noteId == note.id }
            _healthIssues.notifyObserver()
            
            // Remove treatments for this note
            _treatments.value?.removeAll { it.noteId == note.id }
            _treatments.notifyObserver()
            
            // Remove from saved items if present
            _savedItems.value?.remove(note.id)
            _savedItems.notifyObserver()
            
            // Update health statistics
            updateHealthStatistics()
        }
        
        return note
    }

    fun resetNotes() {
        // Clear all data when signing out
        this._notes.value?.clear()
        _notes.notifyObserver()
        
        this._healthIssues.value?.clear()
        _healthIssues.notifyObserver()
        
        this._treatments.value?.clear()
        _treatments.notifyObserver()
        
        this._savedItems.value?.clear()
        _savedItems.notifyObserver()
        
        _healthStatistics.postValue(HealthStatistics())
    }

    // Health data models
    enum class HealthStatus {
        HEALTHY, NEEDS_ATTENTION, CRITICAL
    }
    
    enum class HealthIssueType {
        DISEASE, PEST, NUTRIENT, WATER
    }
    
    enum class IssueSeverity {
        LOW, MEDIUM, HIGH
    }
    
    data class HealthIssue(
        val id: String,
        val name: String,
        var severity: IssueSeverity,
        val type: HealthIssueType,
        val noteId: String,
        val timestamp: Long
    )

    data class Treatment(
        val id: String,
        val title: String,
        val description: String,
        val priority: IssueSeverity,
        var isCompleted: Boolean,
        val issueId: String,
        val noteId: String,
        val timestamp: Long
    )

    data class HealthStatistics(
        var healthyCount: Int = 0,
        var needsAttentionCount: Int = 0,
        var criticalCount: Int = 0,
        var diseaseCount: Int = 0,
        var pestCount: Int = 0,
        var nutrientCount: Int = 0,
        var waterCount: Int = 0,
        var healthTrend: Float = 0.5f
    )

    // a note data class
    data class Note(
        val id: String,
        val name: String,
        val description: String,
        var imageName: String? = null,
        var healthStatus: HealthStatus = HealthStatus.HEALTHY,
        var latitude: Double? = null,
        var longitude: Double? = null
    ) {
        override fun toString(): String = name

        // bitmap image
        var image : Bitmap? = null
        
        // Timestamp from the name (assuming format YYYY-MM-DD-HH-mm-ss-SSS)
        val timestamp: String
            get() {
                return try {
                    val datePart = name.split("-").take(6).joinToString("-")
                    val parsedDate = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).parse(datePart)
                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(parsedDate ?: Date())
                } catch (e: Exception) {
                    name // Fallback to using name directly if parsing fails
                }
            }
        
        // Location is available if both lat and long are non-null
        val hasLocation: Boolean
            get() = latitude != null && longitude != null

        // Return an API NoteData from this Note object
        val data: NoteData
            get() = NoteData.builder()
                .name(this.name)
                .description(this.description)
                .image(this.imageName)
                .id(this.id)
                .latitude(this.latitude?.toString())
                .longitude(this.longitude?.toString())
                .build()

        // static function to create a Note from a NoteData API object
        companion object {
            fun from(noteData : NoteData) : Note {
                val result = Note(
                    noteData.id,
                    noteData.name,
                    noteData.description,
                    noteData.image,
                    HealthStatus.HEALTHY,
                    noteData.latitude?.toDoubleOrNull(),
                    noteData.longitude?.toDoubleOrNull()
                )

                if (noteData.image != null) {
                    Backend.retrieveImage(noteData.image!!) {
                        result.image = it

                        // force a UI update
                        with(UserData) { notifyObserver() }
                    }
                }

                return result
            }
        }
    }
}
package com.example.apptodos

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import java.time.ZoneId
import java.time.Instant
import java.time.DateTimeException
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.apptodos.room.Priority
import com.example.apptodos.room.Task
import com.example.apptodos.room.TaskDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class EditTask : AppCompatActivity() {

    val db by lazy { TaskDB(this) }
    private var currentTaskId: Int = -1
    private var currentTask: Task? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale("id", "ID"))
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale("id", "ID"))

    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var actvCategory: AutoCompleteTextView
    private lateinit var rgPriority: RadioGroup
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnSaveChanges: Button
    private lateinit var switchReminder: SwitchCompat
    private lateinit var btnCancelEdit: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted.")
                Toast.makeText(this, "Izin notifikasi diberikan.", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("Permission", "Notification permission denied.")
                Toast.makeText(
                    this,
                    "Izin notifikasi ditolak. Pengingat tidak dapat diaktifkan.",
                    Toast.LENGTH_LONG
                ).show()
                switchReminder.isChecked = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_task)

        etTitle = findViewById(R.id.etTaskTitle)
        etDescription = findViewById(R.id.etTaskDescription)
        actvCategory = findViewById(R.id.actvCategory)
        rgPriority = findViewById(R.id.rgPriority)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        btnSaveChanges = findViewById(R.id.btnSave)
        switchReminder = findViewById(R.id.switchReminder)
        btnCancelEdit = findViewById(R.id.btnCancel)
        btnCancelEdit.setOnClickListener { finish() }


        setupDropdowns()

        currentTaskId = intent.getIntExtra(MainActivity.EXTRA_TASK_ID, -1)

        if (currentTaskId == -1) {
            Toast.makeText(this, "Error: Task ID tidak valid.", Toast.LENGTH_SHORT).show()
            Log.e("EditTaskActivity", "Invalid Task ID received.")
            finish()
            return
        }

        loadTaskData()
        setupButtonClickListeners()
    }

    fun cancelReminders(context: Context, taskId: Int) {
        val tag = "reminder_$taskId"
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
        Log.d("ReminderScheduler", "All reminders cancelled for task ID: $taskId (Tag: $tag) from EditTask")
    }

    fun scheduleReminders(context: Context, taskId: Int, taskTitle: String, deadlineMillis: Long) {
        val reminderConfigs = listOf(
            Pair(TimeUnit.HOURS.toMillis(1), "1 jam"),
            Pair(TimeUnit.MINUTES.toMillis(30), "30 menit")
        )

        for ((leadTimeMillis, leadTimeDescription) in reminderConfigs) {
            val triggerTimeMillis = deadlineMillis - leadTimeMillis
            val currentTimeMillis = System.currentTimeMillis()
            val initialDelay = triggerTimeMillis - currentTimeMillis

            if (initialDelay > 0) {
                val data = Data.Builder()
                    .putInt(ReminderWorker.KEY_TASK_ID, taskId)
                    .putString(ReminderWorker.KEY_TASK_TITLE, taskTitle)
                    .putString(ReminderWorker.KEY_LEAD_TIME_DESCRIPTION, leadTimeDescription)
                    .build()

                val uniqueWorkName = "reminder_work_${taskId}_${leadTimeMillis}"
                val commonTag = "reminder_$taskId"

                val reminderWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(commonTag)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    uniqueWorkName, ExistingWorkPolicy.REPLACE, reminderWorkRequest
                )
                Log.d("ReminderScheduler", "Reminder ($leadTimeDescription) scheduled from EditTask for task ID: $taskId at $triggerTimeMillis. WorkName: $uniqueWorkName")
            } else {
                Log.d("ReminderScheduler", "Reminder ($leadTimeDescription) not scheduled from EditTask for task ID: $taskId. Deadline invalid or in the past for this lead time.")
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permission", "Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("Izin Notifikasi Diperlukan")
                        .setMessage("Aplikasi ini memerlukan izin untuk menampilkan notifikasi pengingat tugas.")
                        .setPositiveButton("Mengerti") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Batal") { dialog, _ ->
                            dialog.dismiss()
                            switchReminder.isChecked = false
                        }.show()
                    Log.w("Permission", "Showing rationale for notification permission.")
                }
                else -> {
                    Log.d("Permission", "Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Log.d("Permission", "No runtime notification permission needed for this Android version.")
        }
    }

    private fun setupDropdowns() {
        val categoryItems = resources.getStringArray(R.array.category_array)
        val categoryAdapter = ArrayAdapter(this, R.layout.dropdown_item, categoryItems)
        actvCategory.setAdapter(categoryAdapter)
    }

    private fun loadTaskData() {
        CoroutineScope(Dispatchers.IO).launch {
            currentTask = db.taskDao().getTaskById(currentTaskId)
            withContext(Dispatchers.Main) {
                currentTask?.let { task ->
                    populateUI(task)
                } ?: run {
                    Toast.makeText(this@EditTask, "Error: Tugas tidak ditemukan.", Toast.LENGTH_SHORT).show()
                    Log.e("EditTaskActivity", "Task with ID $currentTaskId not found.")
                    finish()
                }
            }
        }
    }

    private fun populateUI(task: Task) {
        etTitle.setText(task.title)
        etDescription.setText(task.description)
        actvCategory.setText(task.category, false)

        rgPriority.check(
            when (task.priority) {
                Priority.Rendah -> R.id.rbLow
                Priority.Sedang -> R.id.rbMedium
                Priority.Tinggi -> R.id.rbHigh
            }
        )

        task.dueDateTimeMillis?.let { millis ->
            try {
                val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                selectedDate = localDateTime.toLocalDate()
                selectedTime = localDateTime.toLocalTime()
            } catch (e: DateTimeException) {
                Log.e("EditTask", "Error parsing dueDateTimeMillis to LocalDate/Time: $millis", e)
                selectedDate = null
                selectedTime = null
            }
        } ?: run {
            selectedDate = null
            selectedTime = null
        }
        updateDateTimeButtons()
    }

    private fun setupButtonClickListeners() {
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectTime.setOnClickListener { showTimePicker() }
        btnSaveChanges.setOnClickListener { saveChanges() }

        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (selectedDate == null || selectedTime == null) {
                    Toast.makeText(this, "Pilih tanggal dan waktu terlebih dahulu untuk mengaktifkan pengingat.", Toast.LENGTH_LONG).show()
                    switchReminder.isChecked = false
                    return@setOnCheckedChangeListener
                }
                checkAndRequestNotificationPermission()
            }
        }
    }

    private fun updateDateTimeButtons() {
        btnSelectDate.text = selectedDate?.format(dateFormatter) ?: getString(R.string.pilih_tanggal)
        btnSelectTime.text = selectedTime?.format(timeFormatter) ?: getString(R.string.pilih_waktu)
    }

    private fun showDatePicker() {
        val initialDate = selectedDate ?: LocalDate.now()
        DatePickerDialog(
            this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                updateDateTimeButtons()
                if (switchReminder.isChecked && selectedTime == null) {
                    Toast.makeText(this, "Jangan lupa pilih waktu untuk pengingat.", Toast.LENGTH_SHORT).show()
                }
            },
            initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth
        ).apply {
            datePicker.minDate = Calendar.getInstance().timeInMillis
        }.show()
    }

    private fun showTimePicker() {
        if (selectedDate == null) {
            Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        val initialTime = selectedTime ?: LocalTime.now()
        TimePickerDialog(
            this, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                updateDateTimeButtons()
            },
            initialTime.hour, initialTime.minute, true
        ).show()
    }

    private fun saveChanges() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val category = actvCategory.text.toString().trim()
        val priority = when (rgPriority.checkedRadioButtonId) {
            R.id.rbLow -> Priority.Rendah
            R.id.rbMedium -> Priority.Sedang
            R.id.rbHigh -> Priority.Tinggi
            else -> { Toast.makeText(this, "Pilih prioritas.", Toast.LENGTH_SHORT).show(); return }
        }

        if (title.isEmpty()) {
            etTitle.error = "Judul tidak boleh kosong."; return
        }
        if (category.isEmpty()) {
            actvCategory.error = "Kategori tidak boleh kosong"; return
        }
        etTitle.error = null; actvCategory.error = null

        val reminderEnabled = switchReminder.isChecked

        if (reminderEnabled && (selectedDate == null || selectedTime == null)) {
            Toast.makeText(this, "Tanggal dan waktu harus dipilih untuk pengingat aktif.", Toast.LENGTH_LONG).show()
            return
        }

        var finalDueDateMillis: Long? = null
        if (selectedDate != null) {
            val timeToUse = selectedTime ?: LocalTime.MIDNIGHT
            try {
                finalDueDateMillis = LocalDateTime.of(selectedDate!!, timeToUse)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                // --- Validasi Waktu Deadline (Minimal 1 Jam dari Sekarang) ---
                val oneHourFromNowMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
                if (finalDueDateMillis < oneHourFromNowMillis) {
                    Toast.makeText(this, "Waktu deadline minimal harus 1 jam dari sekarang.", Toast.LENGTH_LONG).show()
                    Log.w("EditTask", "Validation failed: Deadline $finalDueDateMillis is not at least 1 hour from now.")
                    return
                }
                // --- Akhir Validasi Waktu Deadline ---

            } catch (e: Exception) {
                Log.e("EditTask", "Error converting new date/time to millis", e)
                Toast.makeText(this, "Format tanggal/waktu tidak valid.", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (reminderEnabled) {
                Toast.makeText(this, "Tanggal dan waktu diperlukan untuk pengingat aktif.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val updatedTask = currentTask?.copy(
            title = title,
            description = description,
            category = category,
            priority = priority,
            dueDateTimeMillis = finalDueDateMillis
        ) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            db.taskDao().updateTask(updatedTask)
            Log.d("EditTaskActivity", "Task ID $currentTaskId updated successfully.")

            cancelReminders(applicationContext, updatedTask.id)

            if (reminderEnabled && updatedTask.dueDateTimeMillis != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@EditTask, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditTask, "Izin notifikasi diperlukan. Pengingat tidak dijadwalkan.", Toast.LENGTH_LONG).show()
                    }
                    Log.w("EditTask", "Cannot schedule reminders for task ${updatedTask.id}: Notification permission not granted.")
                } else {
                    scheduleReminders(applicationContext, updatedTask.id, updatedTask.title, updatedTask.dueDateTimeMillis!!)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@EditTask, "Tugas berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
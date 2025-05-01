package com.example.apptodos // Sesuaikan package

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

        // Inisialisasi View Components
        etTitle = findViewById(R.id.etTaskTitle)
        etDescription = findViewById(R.id.etTaskDescription)
        actvCategory = findViewById(R.id.actvCategory)
        rgPriority = findViewById(R.id.rgPriority)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        btnSaveChanges = findViewById(R.id.btnSave)
        switchReminder = findViewById(R.id.switchReminder)

        setupDropdowns()

        currentTaskId = intent.getIntExtra(MainActivity.EXTRA_TASK_ID, -1)

        if (currentTaskId == -1) {
            Toast.makeText(this, "Error: Task ID tidak valid.", Toast.LENGTH_SHORT).show()
            Log.e("EditTaskActivity", "Invalid Task ID received.")
            finish() // Kembali ke MainActivity
            return
        }

        loadTaskData()
        setupButtonClickListeners()
    }

    fun cancelReminder(context: Context, taskId: Int) {
        val uniqueWorkName = "reminder_work_$taskId"
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        Log.d("ReminderScheduler", "Reminder cancelled for task ID: $taskId")
    }

    fun scheduleReminder(context: Context, taskId: Int, taskTitle: String, deadlineMillis: Long) {
        val oneHourInMillis = TimeUnit.HOURS.toMillis(1)
        val triggerTimeMillis = deadlineMillis - oneHourInMillis
        val currentTimeMillis = System.currentTimeMillis()
        val initialDelay = triggerTimeMillis - currentTimeMillis

        if (initialDelay > 0) {
            val data = Data.Builder()
                .putInt(ReminderWorker.KEY_TASK_ID, taskId)
                .putString(ReminderWorker.KEY_TASK_TITLE, taskTitle)
                .build()
            val uniqueWorkName = "reminder_work_$taskId"
            val reminderWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_$taskId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName, ExistingWorkPolicy.REPLACE, reminderWorkRequest
            )
            Log.d(
                "ReminderScheduler",
                "Reminder scheduled for task ID: $taskId at $triggerTimeMillis"
            )
        } else {
            Log.d(
                "ReminderScheduler",
                "Reminder not scheduled for task ID: $taskId. Deadline invalid."
            )
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
                            requestPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
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
            Log.d("Permission", "No runtime notification permission needed.")
        }
    }

    private fun setupDropdowns() {
        val categoryItems = resources.getStringArray(R.array.category_array)
        val categoryAdapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryItems)
        actvCategory.setAdapter(categoryAdapter)
    }


    private fun loadTaskData() {
        CoroutineScope(Dispatchers.IO).launch {
            currentTask = db.taskDao().getTaskById(currentTaskId)
            withContext(Dispatchers.Main) {
                if (currentTask != null) {
                    populateUI(currentTask!!)
                } else {
                    Toast.makeText(
                        this@EditTask,
                        "Error: Task tidak ditemukan.",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("EditTaskActivity", "Task with ID $currentTaskId not found in database.")
                    finish()
                }
            }
        }
    }

    private fun populateUI(task: Task) {
        etTitle.setText(task.title)
        etDescription.setText(task.description)
        actvCategory.setText(task.category, false)

        val priorityButtonId = when (task.priority) {
            Priority.Rendah -> R.id.rbLow
            Priority.Sedang -> R.id.rbMedium
            Priority.Tinggi -> R.id.rbHigh
            // else tidak perlu jika enum hanya 3 itu
        }
        rgPriority.check(priorityButtonId)

        if (task.dueDateTimeMillis != null) {
            try {
                val instant = Instant.ofEpochMilli(task.dueDateTimeMillis!!)
                val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                selectedDate = zonedDateTime.toLocalDate()
                selectedTime = zonedDateTime.toLocalTime()
            } catch (e: Exception) {
            }
        } else {
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
                checkAndRequestNotificationPermission()
            }
        }
    }

    private fun updateDateTimeButtons() {
        if (selectedDate != null) {
            try {
                btnSelectDate.text = selectedDate!!.format(dateFormatter)
            } catch (e: Exception) {
                Log.e("DateTimeFormat", "Error formatting selectedDate", e); btnSelectDate.text =
                    "Tangggal Error"
            }
        } else {
            btnSelectDate.text = getString(R.string.pilih_tanggal)
        } // Gunakan string resource

        // Update teks tombol waktu berdasarkan variabel selectedTime
        if (selectedTime != null) {
            try {
                btnSelectTime.text = selectedTime!!.format(timeFormatter)
            } catch (e: Exception) {
                Log.e("DateTimeFormat", "Error formatting selectedTime", e); btnSelectTime.text =
                    "Waktu Error"
            }
        } else {
            btnSelectTime.text = getString(R.string.pilih_waktu)
        }
    }


    private fun showDatePicker() {
        val initialDate = selectedDate ?: LocalDate.now()
        val datePickerDialog = DatePickerDialog(
            this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                Log.d("DatePicker", "Tanggal dipilih: $selectedDate")
                updateDateTimeButtons()
            },
            initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        if (selectedDate == null) {
            Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val initialTime = selectedTime ?: LocalTime.now()
        val timePickerDialog = TimePickerDialog(
            this, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                Log.d("TimePicker", "Waktu dipilih: $selectedTime")
                updateDateTimeButtons()
            },
            initialTime.hour, initialTime.minute, true
        )
        timePickerDialog.show()
    }


    private fun saveChanges() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val selectedCategory = actvCategory.text.toString()
        val selectedPriorityId = rgPriority.checkedRadioButtonId
        val selectedPriority = when (selectedPriorityId) {
            R.id.rbLow -> Priority.Rendah
            R.id.rbHigh -> Priority.Tinggi
            R.id.rbMedium -> Priority.Sedang
            else -> {
                Toast.makeText(this, "Pilih prioritas", Toast.LENGTH_SHORT).show(); return
            }
        }
        val reminderEnabled = switchReminder.isChecked

        // 2. Validasi Input
        if (title.isEmpty()) {
            etTitle.error = "Judul tidak boleh kosong"; return
        }
        if (selectedCategory.isBlank() || !resources.getStringArray(R.array.category_array)
                .contains(selectedCategory)
        ) {
            Toast.makeText(this, "Pilih kategori yang valid", Toast.LENGTH_SHORT).show(); return
        }
        etTitle.error = null

        var finalDueDateMillis: Long? = null
        if (selectedDate != null) {
            val timeToUse = selectedTime ?: LocalTime.MIDNIGHT
            val combinedLocalDateTime = LocalDateTime.of(selectedDate, timeToUse)
            try {
                val systemZoneId = ZoneId.systemDefault()
                val zonedDateTime = combinedLocalDateTime.atZone(systemZoneId)
                val instant = zonedDateTime.toInstant()
                finalDueDateMillis = instant.toEpochMilli()
                Log.d(
                    "DateTimeSave",
                    "Saving Edit - Input: $combinedLocalDateTime, Zone: $systemZoneId, Millis: $finalDueDateMillis"
                )
            } catch (e: Exception) {
                Log.e("DateTimeSave", "Error converting date/time to millis in Edit", e)
                Toast.makeText(this, "Error tanggal/waktu.", Toast.LENGTH_SHORT).show()
                return // Hentikan penyimpanan
            }
        }

        val updatedTask = Task(
            id = currentTaskId,
            title = title,
            description = description,
            category = selectedCategory,
            priority = selectedPriority,
            dueDateTimeMillis = finalDueDateMillis,
            isCompleted = currentTask?.isCompleted ?: false,
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.taskDao().updateTask(updatedTask) // Panggil update DAO
                Log.d("EditTaskActivity", "Task ID $currentTaskId updated successfully.")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTask, "Tugas berhasil diperbarui", Toast.LENGTH_SHORT)
                        .show()

                    // --- Logika Reminder ---
                    val currentTaskTitle = updatedTask.title
                    val currentDeadline = updatedTask.dueDateTimeMillis
                    val taskId = updatedTask.id

                    if (reminderEnabled && currentDeadline != null) {
                        // Hitung waktu trigger
                        val oneHourMillis = TimeUnit.HOURS.toMillis(1)
                        val triggerTimeMillis = currentDeadline - oneHourMillis

                        if (triggerTimeMillis <= System.currentTimeMillis()) {
                            Toast.makeText(
                                this@EditTask,
                                "Deadline terlalu cepat untuk pengingat 1 jam sebelumnya.",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.w(
                                "TaskSave",
                                "Cannot schedule reminder for task $taskId: Trigger time is in the past."
                            )
                            cancelReminder(applicationContext, taskId)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this@EditTask,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                Toast.makeText(
                                    this@EditTask,
                                    "Izin notifikasi diperlukan untuk pengingat.",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.w(
                                    "TaskSave",
                                    "Cannot schedule reminder for task $taskId: Notification permission not granted."
                                )
                            } else {
                                scheduleReminder(
                                    applicationContext,
                                    taskId,
                                    currentTaskTitle,
                                    currentDeadline
                                )
                            }
                        }
                    } else {
                        // Jika reminder dimatikan atau deadline null, batalkan reminder lama
                        cancelReminder(applicationContext, taskId)
                    }

                    finish()
                }
            } catch (e: Exception) {
                Log.e("EditTaskActivity", "Error updating task", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditTask,
                        "Gagal memperbarui tugas: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

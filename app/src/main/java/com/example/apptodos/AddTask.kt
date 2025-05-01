package com.example.apptodos

import java.time.ZoneId
import java.time.DateTimeException
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apptodos.room.TaskDB
import android.widget.RadioGroup
import android.widget.Toast
import android.util.Log
import android.widget.Button
import android.content.Context
import android.widget.EditText
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
import java.util.concurrent.TimeUnit

class AddTask : AppCompatActivity() {
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var actvCategory: AutoCompleteTextView
    private lateinit var rgPriority: RadioGroup
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnSaveChanges: Button
    private lateinit var btnCancel: Button
    private lateinit var switchReminder: SwitchCompat


    private lateinit var adapterItems: ArrayAdapter<String>
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null


    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted.")
                Toast.makeText(this, "Izin notifikasi diberikan.", Toast.LENGTH_SHORT).show()
                // Tidak perlu langsung schedule, biarkan logika save yg handle
            } else {
                Log.w("Permission", "Notification permission denied.")
                Toast.makeText(this, "Izin notifikasi ditolak. Pengingat tidak dapat diaktifkan.", Toast.LENGTH_LONG).show()
                switchReminder.isChecked = false // Matikan switch jika izin ditolak
            }
        }

    val db by lazy { TaskDB(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
        etTitle = findViewById(R.id.etTaskTitle)
        etDescription = findViewById(R.id.etTaskDescription)
        actvCategory = findViewById(R.id.actvCategory)
        rgPriority = findViewById(R.id.rgPriority)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        btnSaveChanges = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        switchReminder = findViewById(R.id.switchReminder)
        adapterItems = ArrayAdapter(this, R.layout.dropdown_item, resources.getStringArray(R.array.category_array))
        actvCategory.setAdapter(adapterItems)


        actvCategory.setOnItemClickListener { adapterView, _, position, _ ->
            val selectedCategory = adapterView.getItemAtPosition(position).toString()
            Toast.makeText(this, "Selected Category: $selectedCategory", Toast.LENGTH_SHORT).show()
        }
        setupListener()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
            Log.d("ReminderScheduler", "Reminder scheduled for task ID: $taskId at $triggerTimeMillis")
        } else {
            Log.d("ReminderScheduler", "Reminder not scheduled for task ID: $taskId. Deadline invalid.")
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permission", "Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("Izin Notifikasi Diperlukan")
                        .setMessage("Aplikasi ini memerlukan izin untuk menampilkan notifikasi pengingat tugas.")
                        .setPositiveButton("Mengerti") { _, _ -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        .setNegativeButton("Batal") { dialog, _ ->
                            dialog.dismiss()
                            switchReminder.isChecked = false // Matikan switch jika batal
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

    private fun setupListener(){
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val initialYear = selectedDate?.year ?: calendar.get(Calendar.YEAR)
            val initialMonth = selectedDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
            val initialDay = selectedDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

            switchReminder.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkAndRequestNotificationPermission()
                }
            }

            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                btnSelectDate.text = selectedDate?.format(dateFormatter) ?: getString(R.string.pilih_tanggal)
            }, initialYear, initialMonth, initialDay).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }

        btnSelectTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val initialHour = selectedTime?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
            val initialMinute = selectedTime?.minute ?: calendar.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                btnSelectTime.text = selectedTime?.format(timeFormatter) ?: getString(R.string.pilih_waktu)
            }, initialHour, initialMinute, true // Format 24 jam
            ).show()
        }


        // Tambahkan validasi jika tanggal/waktu wajib
        btnSaveChanges.setOnClickListener {
            // 1. Ambil data dari Input Fields
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val category = actvCategory.text.toString().trim()

            // 2. Baca Prioritas dari RadioGroup
            val selectedPriorityId = rgPriority.checkedRadioButtonId
            val selectedPriority = when (selectedPriorityId) {
                R.id.rbLow -> Priority.Rendah
                R.id.rbHigh -> Priority.Tinggi
                R.id.rbMedium -> Priority.Sedang
                else -> Priority.Sedang
            }

            var finalDueDateMillis: Long? = null
            if (selectedDate != null) {
                val timeToUse = selectedTime ?: LocalTime.MIDNIGHT
                val combinedLocalDateTime = LocalDateTime.of(selectedDate, timeToUse)

                try {
                    val systemZoneId = ZoneId.systemDefault()
                    val zonedDateTime = combinedLocalDateTime.atZone(systemZoneId)
                    val instant = zonedDateTime.toInstant()
                    finalDueDateMillis = instant.toEpochMilli()

                    // Logging untuk verifikasi
                    Log.d("DateTimeSave", "Input: $combinedLocalDateTime | Zone: $systemZoneId | Saved Millis: $finalDueDateMillis")

                } catch (e: DateTimeException) {
                    Log.e("DateTimeSave", "Error konversi waktu lokal ke millis", e)
                    Toast.makeText(this, "Gagal memproses zona waktu.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DateTimeSave", "Error umum saat proses tanggal/waktu", e)
                    Toast.makeText(this, "Terjadi kesalahan tanggal/waktu.", Toast.LENGTH_SHORT).show()
                }
            }

            if (title.isEmpty()) {
                etTitle.error = "Title tidak boleh kosong"; return@setOnClickListener
            }
            if (category.isEmpty()) {
                actvCategory.error = "Kategori tidak boleh kosong"; Toast.makeText(
                    this,
                    "Pilih kategori",
                    Toast.LENGTH_SHORT
                ).show(); return@setOnClickListener
            }
            etTitle.error = null; actvCategory.error = null
            if (selectedDate == null) {
                Toast.makeText(this, "Pilih tanggal terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val taskToSave = Task(
                id = 0,
                title = title,
                description = description,
                category = category,
                priority = selectedPriority,
                dueDateTimeMillis = finalDueDateMillis,
                isCompleted = false
            )
            CoroutineScope(Dispatchers.IO).launch {
                var newTaskId: Long = -1
                try {
                    newTaskId = db.taskDao().addTask(taskToSave)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTask, "Task berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        if (newTaskId > 0) {
                            val reminderEnabled = switchReminder.isChecked
                            val currentTaskTitle = title // Bisa ambil dari taskToSave.title
                            val currentDeadline = finalDueDateMillis

                            if (reminderEnabled && currentDeadline != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@AddTask, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(this@AddTask, "Izin notifikasi diperlukan untuk pengingat.", Toast.LENGTH_LONG).show()
                                    Log.w("TaskSave", "Cannot schedule reminder for task $newTaskId: Notification permission not granted.")
                                    // Tetap finish(), tapi reminder tidak terjadwal
                                } else {
                                    scheduleReminder(applicationContext, newTaskId.toInt(), currentTaskTitle, currentDeadline)
                                }
                            } else {
                                cancelReminder(applicationContext, newTaskId.toInt())
                            }
                        } else {
                            Log.e("TaskSave", "Failed to get valid ID after saving task. Reminder not scheduled.")
                        }

                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTask, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            }
            Log.d("AddTask", "Save button clicked")
            Log.d("AddTask", "Task data: $title, $description, $category, $selectedPriority")
            Log.d("AddTask", "Date/Time: $selectedDate, $selectedTime")
        }


        btnCancel.setOnClickListener {
            finish()
        }

    }
}
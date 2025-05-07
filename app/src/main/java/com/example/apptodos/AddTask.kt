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
import java.util.Locale
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
        .withLocale(Locale("id", "ID"))
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale("id", "ID"))

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted.")
                Toast.makeText(this, "Izin notifikasi diberikan.", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("Permission", "Notification permission denied.")
                Toast.makeText(this, "Izin notifikasi ditolak. Pengingat tidak dapat diaktifkan.", Toast.LENGTH_LONG).show()
                switchReminder.isChecked = false
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

    fun cancelReminders(context: Context, taskId: Int) {
        val tag = "reminder_$taskId"
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
        Log.d("ReminderScheduler", "All reminders cancelled for task ID: $taskId (Tag: $tag)")
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
                Log.d("ReminderScheduler", "Reminder ($leadTimeDescription) scheduled for task ID: $taskId at $triggerTimeMillis. WorkName: $uniqueWorkName")
            } else {
                Log.d("ReminderScheduler", "Reminder ($leadTimeDescription) not scheduled for task ID: $taskId. Deadline invalid or in the past for this lead time.")
            }
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

    private fun setupListener(){
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
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val initialYear = selectedDate?.year ?: calendar.get(Calendar.YEAR)
            val initialMonth = selectedDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
            val initialDay = selectedDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                btnSelectDate.text = selectedDate?.format(dateFormatter) ?: getString(R.string.pilih_tanggal)
                if (switchReminder.isChecked && selectedTime == null) {
                    Toast.makeText(this, "Jangan lupa pilih waktu untuk pengingat.", Toast.LENGTH_SHORT).show()
                }
            }, initialYear, initialMonth, initialDay).apply {
                datePicker.minDate = Calendar.getInstance().timeInMillis
            }.show()
        }

        btnSelectTime.setOnClickListener {
            if (selectedDate == null) {
                Toast.makeText(this, "Pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val calendar = Calendar.getInstance()
            val initialHour = selectedTime?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
            val initialMinute = selectedTime?.minute ?: calendar.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                btnSelectTime.text = selectedTime?.format(timeFormatter) ?: getString(R.string.pilih_waktu)
            }, initialHour, initialMinute, true
            ).show()
        }

        btnSaveChanges.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val category = actvCategory.text.toString().trim()

            val selectedPriorityId = rgPriority.checkedRadioButtonId
            val selectedPriority = when (selectedPriorityId) {
                R.id.rbLow -> Priority.Rendah
                R.id.rbHigh -> Priority.Tinggi
                R.id.rbMedium -> Priority.Sedang
                else -> Priority.Sedang
            }

            if (title.isEmpty()) {
                etTitle.error = "Judul tidak boleh kosong"; return@setOnClickListener
            }
            if (category.isEmpty()) {
                actvCategory.error = "Kategori tidak boleh kosong"; Toast.makeText(this, "Pilih kategori", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            etTitle.error = null; actvCategory.error = null

            if (switchReminder.isChecked && (selectedDate == null || selectedTime == null)) {
                Toast.makeText(this, "Tanggal dan waktu harus dipilih untuk pengingat aktif.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            var finalDueDateMillis: Long? = null
            if (selectedDate != null) {
                val timeToUse = selectedTime ?: LocalTime.MIDNIGHT
                val combinedLocalDateTime = LocalDateTime.of(selectedDate!!, timeToUse)
                try {
                    finalDueDateMillis = combinedLocalDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Log.d("DateTimeSave", "Input: $combinedLocalDateTime | Zone: ${ZoneId.systemDefault()} | Saved Millis: $finalDueDateMillis")

                    // --- Validasi Waktu Deadline (Minimal 1 Jam dari Sekarang) ---
                    val oneHourFromNowMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
                    if (finalDueDateMillis < oneHourFromNowMillis) {
                        Toast.makeText(this, "Waktu deadline minimal harus 1 jam dari sekarang.", Toast.LENGTH_LONG).show()
                        Log.w("TaskSave", "Validation failed: Deadline $finalDueDateMillis is not at least 1 hour from now.")
                        return@setOnClickListener
                    }

                } catch (e: DateTimeException) {
                    Log.e("DateTimeSave", "Error konversi waktu lokal ke millis", e)
                    Toast.makeText(this, "Gagal memproses zona waktu.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                } catch (e: Exception) {
                    Log.e("DateTimeSave", "Error umum saat proses tanggal/waktu", e)
                    Toast.makeText(this, "Terjadi kesalahan tanggal/waktu.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else {
                if(switchReminder.isChecked) {
                    Toast.makeText(this, "Tanggal diperlukan untuk pengingat aktif.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
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
                var newTaskId: Long = -1L
                try {
                    newTaskId = db.taskDao().addTask(taskToSave)
                    withContext(Dispatchers.Main) {
                        if (newTaskId > 0) {
                            Toast.makeText(this@AddTask, "Tugas berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            val reminderEnabled = switchReminder.isChecked
                            if (reminderEnabled && finalDueDateMillis != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(this@AddTask, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(this@AddTask, "Izin notifikasi diperlukan untuk pengingat. Pengingat tidak dijadwalkan.", Toast.LENGTH_LONG).show()
                                    Log.w("TaskSave", "Cannot schedule reminders for task $newTaskId: Notification permission not granted.")
                                } else {
                                    scheduleReminders(applicationContext, newTaskId.toInt(), taskToSave.title, finalDueDateMillis)
                                }
                            }
                            finish()
                        } else {
                            Toast.makeText(this@AddTask, "Gagal menyimpan tugas, ID tidak valid.", Toast.LENGTH_LONG).show()
                            Log.e("TaskSave", "Failed to save task or get valid ID. New Task ID: $newTaskId")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTask, "Error saat menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("TaskSave", "Error saving task", e)
                    }
                }
            }
        }
        btnCancel.setOnClickListener {
            finish()
        }
    }
}
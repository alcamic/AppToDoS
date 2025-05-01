package com.example.apptodos // Sesuaikan package

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import com.example.apptodos.room.Priority
import com.example.apptodos.room.Task
import com.example.apptodos.room.TaskDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTask : AppCompatActivity() {

    val db by lazy { TaskDB(this) }
    private var currentTaskId: Int = -1 // Untuk menyimpan ID task yang diedit
    private var currentTask: Task? = null // Untuk menyimpan data task asli
    private var dueDateTimeMillis: Long? = null // Untuk menyimpan tanggal & waktu

    // Deklarasikan View Components (sesuaikan dengan ID di layout Anda)
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var actvCategory: AutoCompleteTextView // Asumsi pakai Spinner
    private lateinit var rgPriority: RadioGroup
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnSaveChanges: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_task) // Gunakan layout edit

        // Inisialisasi View Components
        etTitle = findViewById(R.id.etTaskTitle) // Ganti ID jika beda
        etDescription = findViewById(R.id.etTaskDescription) // Ganti ID jika beda
        actvCategory = findViewById(R.id.actvCategory) // Ganti ID jika beda
        rgPriority = findViewById(R.id.rgPriority) // Ganti ID jika beda
        btnSelectDate = findViewById(R.id.btnSelectDate) // Ganti ID jika beda
        btnSelectTime = findViewById(R.id.btnSelectTime) // Ganti ID jika beda
        btnSaveChanges = findViewById(R.id.btnSave) // Ganti ID jika beda

        // Setup Spinner (sama seperti di AddTaskActivity)
        setupDropdowns()

        // Ambil Task ID dari Intent
        currentTaskId = intent.getIntExtra(MainActivity.EXTRA_TASK_ID, -1)

        if (currentTaskId == -1) {
            // ID tidak valid, tampilkan pesan error dan tutup activity
            Toast.makeText(this, "Error: Task ID tidak valid.", Toast.LENGTH_SHORT).show()
            Log.e("EditTaskActivity", "Invalid Task ID received.")
            finish() // Kembali ke MainActivity
            return
        }

        // Muat data task dari database
        loadTaskData()

        // Setup listener untuk tombol
        setupButtonClickListeners()
    }

    private fun setupDropdowns() {
        // --- Category ---
        val categoryItems = resources.getStringArray(R.array.category_array)
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryItems)
        // Gunakan setAdapter pada AutoCompleteTextView
        actvCategory.setAdapter(categoryAdapter)
    }


    private fun loadTaskData() {
        CoroutineScope(Dispatchers.IO).launch {
            currentTask = db.taskDao().getTaskById(currentTaskId)
            withContext(Dispatchers.Main) {
                if (currentTask != null) {
                    populateUI(currentTask!!) // Isi UI dengan data task
                } else {
                    // Task tidak ditemukan di DB
                    Toast.makeText(this@EditTask, "Error: Task tidak ditemukan.", Toast.LENGTH_SHORT).show()
                    Log.e("EditTaskActivity", "Task with ID $currentTaskId not found in database.")
                    finish()
                }
            }
        }
    }

    private fun populateUI(task: Task) {
        etTitle.setText(task.title)
        etDescription.setText(task.description)
        actvCategory.setText(task.category, false) // Set kategori (jika masih dropdown)

        // --- Set RadioButton yang sesuai untuk Prioritas ---
        val priorityButtonId = when (task.priority) {
            Priority.Rendah -> R.id.rbLow
            Priority.Sedang -> R.id.rbMedium
            Priority.Tinggi -> R.id.rbHigh
            // else tidak perlu jika enum hanya 3 itu
        }
        // Gunakan check() untuk memilih RadioButton di dalam RadioGroup
        rgPriority.check(priorityButtonId)

        // ... (Set tanggal dan waktu tetap sama) ...
        dueDateTimeMillis = task.dueDateTimeMillis
        updateDateTimeButtons()
    }

    private fun setupButtonClickListeners() {
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectTime.setOnClickListener { showTimePicker() }
        btnSaveChanges.setOnClickListener { saveChanges() }
    }

    // --- Logika Date/Time Picker (Sama seperti AddTaskActivity) ---
    private val calendar = Calendar.getInstance()

    private fun updateDateTimeButtons() {
        if (dueDateTimeMillis != null) {
            val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            calendar.timeInMillis = dueDateTimeMillis!! // Atur calendar ke waktu yg disimpan
            btnSelectDate.text = sdfDate.format(calendar.time)
            btnSelectTime.text = sdfTime.format(calendar.time)
        } else {
            btnSelectDate.text = "Pilih Tanggal"
            btnSelectTime.text = "Pilih Waktu"
        }
    }


    private fun showDatePicker() {
        // Jika sudah ada tanggal, gunakan itu sebagai default
        if (dueDateTimeMillis != null) {
            calendar.timeInMillis = dueDateTimeMillis!!
        } else {
            calendar.timeInMillis = System.currentTimeMillis() // Default ke hari ini
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                // Jika belum ada waktu, set ke 00:00 agar timestamp valid
                if (dueDateTimeMillis == null) {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                }
                dueDateTimeMillis = calendar.timeInMillis
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        // Jika belum ada tanggal, minta pilih tanggal dulu
        if (dueDateTimeMillis == null) {
            Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        calendar.timeInMillis = dueDateTimeMillis!! // Gunakan waktu yg sudah ada

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                dueDateTimeMillis = calendar.timeInMillis
                updateDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24 hour format
        )
        timePickerDialog.show()
    }
    // --- Akhir Logika Date/Time Picker ---


    private fun saveChanges() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val selectedCategory = actvCategory.text.toString()

        // Validasi (minimal judul tidak boleh kosong)
        if (title.isEmpty()) {
            etTitle.error = "Judul tidak boleh kosong"
            Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        etTitle.error = null // Hapus error jika sudah diisi

        val selectedPriorityId = rgPriority.checkedRadioButtonId
        val selectedPriority = when (selectedPriorityId) {
            R.id.rbLow -> Priority.Rendah
            R.id.rbHigh -> Priority.Tinggi
            R.id.rbMedium -> Priority.Sedang
            else -> {
                // Kasus jika tidak ada yang terpilih (seharusnya tidak terjadi jika salah satu di-check di awal)
                // Beri default atau tampilkan pesan error
                Toast.makeText(this, "Silakan pilih prioritas", Toast.LENGTH_SHORT).show()
                Log.w("EditTaskActivity", "No priority RadioButton checked!")
                return // Hentikan proses simpan
                // Atau beri default: Priority.Sedang
            }
        }
        // Buat objek Task yang diperbarui (gunakan ID asli)
        val updatedTask = Task(
            id = currentTaskId, // <-- Gunakan ID asli!
            title = title,
            description = description,
            category = selectedCategory,
            priority = selectedPriority,
            dueDateTimeMillis = dueDateTimeMillis, // Ambil dari variabel
            isCompleted = currentTask?.isCompleted ?: false, // <-- Pertahankan status isCompleted asli!
        )

        // Simpan ke database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.taskDao().updateTask(updatedTask) // Gunakan fungsi update yang sudah ada
                Log.d("EditTaskActivity", "Task ID $currentTaskId updated successfully.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTask, "Tugas berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    finish() // Kembali ke MainActivity
                }
            } catch (e: Exception) {
                Log.e("EditTaskActivity", "Error updating task", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTask, "Gagal memperbarui tugas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
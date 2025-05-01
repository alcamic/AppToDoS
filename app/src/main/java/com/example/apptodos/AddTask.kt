package com.example.apptodos

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apptodos.room.TaskDB
import com.google.android.material.button.MaterialButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import android.util.Log
import com.example.apptodos.room.Priority
import com.example.apptodos.room.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

class AddTask : AppCompatActivity() {
    val btnSave: MaterialButton by lazy { findViewById<MaterialButton>(R.id.btnSave) }
    val btnCancel: MaterialButton by lazy { findViewById<MaterialButton>(R.id.btnCancel) }
    val actvCategory: AutoCompleteTextView by lazy { findViewById<AutoCompleteTextView>(R.id.actvCategory) }
    val etTaskTitle by lazy { findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTaskTitle) }
    val etTaskDescription by lazy { findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTaskDescription) }
    val rgPriority: RadioGroup by lazy { findViewById<RadioGroup>(R.id.rgPriority) }
    val btnSelectDate: MaterialButton by lazy { findViewById<MaterialButton>(R.id.btnSelectDate) }
    val btnSelectTime: MaterialButton by lazy { findViewById<MaterialButton>(R.id.btnSelectTime) }
//    val switchReminder by lazy { findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchReminder) }


    private lateinit var adapterItems: ArrayAdapter<String>
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    @RequiresApi(Build.VERSION_CODES.O)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)


    val db by lazy { TaskDB(this) }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
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


    @RequiresApi(Build.VERSION_CODES.O)
    fun setupListener(){
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val initialYear = selectedDate?.year ?: calendar.get(Calendar.YEAR)
            val initialMonth = selectedDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
            val initialDay = selectedDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                btnSelectDate.text = selectedDate?.format(dateFormatter) ?: getString(R.string.pilih_tanggal)
            }, initialYear, initialMonth, initialDay).apply {
                // Batasi tanggal minimum ke hari ini
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
        btnSave.setOnClickListener {
            // 1. Ambil data dari Input Fields
            val title = etTaskTitle.text.toString().trim()
            val description = etTaskDescription.text.toString().trim()
            val category = actvCategory.text.toString().trim()

            // 2. Baca Prioritas dari RadioGroup
            val selectedPriorityId = rgPriority.checkedRadioButtonId
            val selectedPriority = when (selectedPriorityId) {
                R.id.rbLow -> Priority.Rendah // Sesuaikan nama enum jika diubah
                R.id.rbHigh -> Priority.Tinggi // Sesuaikan nama enum jika diubah
                R.id.rbMedium -> Priority.Sedang // Sesuaikan nama enum jika diubah
                else -> Priority.Sedang // Default jika tidak ada yg terpilih
            }

            var finalDueDateMillis: Long? = null
            if (selectedDate != null && selectedTime != null) {
                val combinedDateTime = LocalDateTime.of(selectedDate, selectedTime)
                finalDueDateMillis = combinedDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
            } else if (selectedDate != null) {
                val combinedDateTime = LocalDateTime.of(selectedDate, LocalTime.MIDNIGHT)
                finalDueDateMillis = combinedDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
            }

            // 4. Validasi Input Lain
            if (title.isEmpty()) {
                etTaskTitle.error = "Title tidak boleh kosong"; return@setOnClickListener
            }
            if (category.isEmpty()) {
                actvCategory.error = "Kategori tidak boleh kosong"; Toast.makeText(
                    this,
                    "Pilih kategori",
                    Toast.LENGTH_SHORT
                ).show(); return@setOnClickListener
            }
            etTaskTitle.error = null; actvCategory.error = null
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
                try {
                    db.taskDao().addTask(taskToSave)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTask, "Task berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTask, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace() // Print the stack trace for debugging
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
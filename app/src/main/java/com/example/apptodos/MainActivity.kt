package com.example.apptodos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.apptodos.room.Task
import com.example.apptodos.room.TaskDB
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    val db by lazy { TaskDB(this) }
    lateinit var taskAdapter: TaskAdapter
    private lateinit var tvTaskSummary: TextView

    companion object {
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
    }

    private enum class FilterType {
        ALL, UNCOMPLETED, PRIORITY, COMPLETED
    }
    private var currentFilter: FilterType = FilterType.ALL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        tvTaskSummary = findViewById(R.id.tvTaskSummary)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupChipListeners()
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(calendar.time)
        tvDate.text = formattedDate

        createNotificationChannel()
        tvDate.text = formattedDate
        setupRecyclerView()
        loadTasks()
    }

    private fun setupChipListeners() {
        val chipAll = findViewById<Chip>(R.id.chipAll)
        val chipUncompleted = findViewById<Chip>(R.id.chipUncompleted)
        val chipPriority = findViewById<Chip>(R.id.chipPriority)
        val chipCompleted = findViewById<Chip>(R.id.chipCompleted)

        chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("ChipAction", "Filter: Semua")
                currentFilter = FilterType.ALL
                loadTasks()
            }
        }

        chipUncompleted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("ChipAction", "Filter: Belum Selesai")
                currentFilter = FilterType.UNCOMPLETED
                loadUncompletedTasks()
            }
        }

        chipPriority.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("ChipAction", "Filter: Prioritas")
                currentFilter = FilterType.PRIORITY
                loadPriorityTask()
            }
        }

        chipCompleted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.i("ChipAction", "Filter: Selesai")
                currentFilter = FilterType.COMPLETED // <-- Set Filter
                loadCompletedTask()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        reloadDataBasedOnFilter()
    }
    private fun reloadDataBasedOnFilter() {
        Log.d("MainActivity", "Reloading data for filter: $currentFilter")
        when (currentFilter) {
            FilterType.ALL -> loadTasks()
            FilterType.UNCOMPLETED -> loadUncompletedTasks()
            FilterType.PRIORITY ->  loadPriorityTask()
            FilterType.COMPLETED -> loadCompletedTask()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "task_reminder_channel"
            val name = "Pengingat Tugas"
            val descriptionText = "Notifikasi pengingat untuk tugas yang mendekati deadline"
            val importance = NotificationManager.IMPORTANCE_HIGH // Prioritas notifikasi
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("MainActivity", "Notification channel created.")
        }
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
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                reminderWorkRequest
            )

            Log.d("ReminderScheduler", "Reminder scheduled for task ID: $taskId at $triggerTimeMillis (Delay: $initialDelay ms)")

        } else {
            Log.d("ReminderScheduler", "Reminder not scheduled for task ID: $taskId. Deadline minus 1 hour is in the past.")
        }
    }

    fun cancelReminder(context: Context, taskId: Int) {
        val uniqueWorkName = "reminder_work_$taskId"
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        Log.d("ReminderScheduler", "Reminder cancelled for task ID: $taskId (WorkName: $uniqueWorkName)")
    }

    private fun handleTaskUpdateAndRefresh(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Update Database
            db.taskDao().updateTask(task)
            Log.d("MainActivity", "Task ${task.id} updated in DB: isCompleted=${task.isCompleted}")
            if (task.isCompleted) {
                cancelReminder(applicationContext, task.id)
            }

            // 2. Muat Ulang Data Sesuai Filter Aktif
            Log.d("MainActivity", "Refreshing list for filter: $currentFilter")
            when (currentFilter) {
                FilterType.ALL -> loadTasks()
                FilterType.UNCOMPLETED -> loadUncompletedTasks()
                FilterType.PRIORITY -> loadPriorityTask() // Sama dg ALL jika asumsi benar
                FilterType.COMPLETED -> loadCompletedTask()
            }
        }
    }


        private fun loadCompletedTask() {
            CoroutineScope(Dispatchers.IO).launch {
                val tasks = db.taskDao().getCompletedTasks()
                val uncompletedCount = db.taskDao().getUncompletedTaskCount() // <-- Ambil Count
                Log.d("MainActivity", "Loading completed tasks. Uncompleted count: $uncompletedCount")
                withContext(Dispatchers.Main) {
                    taskAdapter.setData(tasks)
                    updateTaskSummaryText(uncompletedCount) // <-- Update Teks Summary
                }
            }
    }

    private fun loadPriorityTask() { // Asumsi ini load semua, urut prioritas
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getPrioritySortedTasks() // Asumsi DAO method ada
            val uncompletedCount = db.taskDao().getUncompletedTaskCount() // <-- Ambil Count
            Log.d("MainActivity", "Loading priority tasks. Uncompleted count: $uncompletedCount")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
                updateTaskSummaryText(uncompletedCount) // <-- Update Teks Summary
            }
        }
    }


    private fun loadTasks() { // Asumsi ini load semua, urut prioritas
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getAllTasks() // Asumsi DAO method ada dan sesuai
            val uncompletedCount = db.taskDao().getUncompletedTaskCount() // <-- Ambil Count
            Log.d("MainActivity", "Loading all tasks. Uncompleted count: $uncompletedCount")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
                updateTaskSummaryText(uncompletedCount) // <-- Update Teks Summary
            }
        }
    }

    fun addTask (view: View){
        val intent = Intent(this, AddTask::class.java)
        startActivity(intent)
    }

    private fun setupRecyclerView(){
        // Buat adapter dengan menyertakan callback untuk update task
        taskAdapter = TaskAdapter(
            onTaskCheckedChange = { task -> handleTaskUpdateAndRefresh(task) },
            onTaskAction = { task, action -> handleTaskItemAction(task, action) }
        )

        val rvTodoItems = findViewById<RecyclerView>(R.id.rvTodoItems)
        rvTodoItems.apply {
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = taskAdapter
        }
    }

    private fun handleTaskItemAction(task: Task, action: TaskAction) {
        Log.d("MainActivity", "Aksi diterima: $action untuk task '${task.title}' (ID: ${task.id})")
        when (action) {
            TaskAction.EDIT -> {
                // TODO: Navigasi ke layar Edit Task
                Log.i("MainActivity", "TODO: Buka layar Edit untuk task ID ${task.id}")
                val intent = Intent(this, EditTask::class.java)
                intent.putExtra(EXTRA_TASK_ID, task.id)
                 startActivity(intent)
            }
            TaskAction.DELETE -> {
                // TODO: Tampilkan dialog konfirmasi sebelum menghapus
                Log.i("MainActivity", "TODO: Tampilkan konfirmasi hapus untuk task ID ${task.id}")
                showDeleteConfirmationDialog(task) // Panggil fungsi dialog
            }
        }
    }

    private fun showDeleteConfirmationDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Tugas")
            .setMessage("Apakah Anda yakin ingin menghapus tugas '${task.title}'?")
            .setIcon(android.R.drawable.ic_dialog_alert) // Icon standar
            .setPositiveButton("Hapus") { _, _ ->
                // Panggil fungsi untuk hapus dari database
                deleteTaskFromDatabase(task)
            }
            .setNegativeButton("Batal", null) // Tidak melakukan apa-apa jika batal
            .show()
    }

    private fun deleteTaskFromDatabase(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            db.taskDao().deleteTask(task)
            Log.d("MainActivity", "Task ID ${task.id} dihapus dari DB")
            // Batalkan pengingat setelah task dihapus
            cancelReminder(applicationContext, task.id)
            // Muat ulang data
            reloadDataBasedOnFilter()
        }
    }


    private fun loadUncompletedTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getUncompletedTasks() // Asumsi DAO method ada
            // OPTIMASI: Jika kita sudah ambil HANYA yg belum selesai, jumlahnya adalah ukuran list itu sendiri
            val uncompletedCount = tasks.size
            // Alternatif (kurang efisien): val uncompletedCount = db.taskDao().getUncompletedTaskCount()
            Log.d("MainActivity", "Loading uncompleted tasks. Count: $uncompletedCount")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
                updateTaskSummaryText(uncompletedCount) // <-- Update Teks Summary
            }
        }
    }
    private fun updateTaskSummaryText(count: Int) {
        Log.d("MainActivity", "Updating summary text with count: $count")
        if (count > 0) {
            // Format teks secara dinamis
            tvTaskSummary.text = "Kamu memiliki $count tugas yang harus diselesaikan"
            tvTaskSummary.visibility = View.VISIBLE // Pastikan terlihat
        } else {
            tvTaskSummary.text = "Yay! Tidak ada tugas yang harus diselesaikan." // Teks jika 0
            // tvTaskSummary.visibility = View.GONE // Opsional: sembunyikan jika 0
        }
    }

}
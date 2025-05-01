package com.example.apptodos

import android.content.Intent
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

class MainActivity : AppCompatActivity() {
    val db by lazy { TaskDB(this) }
    lateinit var taskAdapter: TaskAdapter

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

    private fun handleTaskUpdateAndRefresh(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Update Database
            db.taskDao().updateTask(task)
            Log.d("MainActivity", "Task ${task.id} updated in DB: isCompleted=${task.isCompleted}")

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
            Log.d("MainActivity", "Loading all tasks ")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
            }
        }
    }

    private fun loadPriorityTask() {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getPrioritySortedTasks()
            Log.d("MainActivity", "Loading all tasks ")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
            }
        }
    }


    private fun loadTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getAllTasks()
            Log.d("MainActivity", "Loading all tasks ")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
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
            db.taskDao().deleteTask(task) // Asumsi ada deleteTask(task: Task) di DAO
            Log.d("MainActivity", "Task ID ${task.id} dihapus dari DB")
            // Muat ulang data sesuai filter aktif setelah hapus
            reloadDataBasedOnFilter()
        }
    }


    private fun loadUncompletedTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = db.taskDao().getUncompletedTasks()
            Log.d("MainActivity", "Loading all tasks ")
            withContext(Dispatchers.Main) {
                taskAdapter.setData(tasks)
            }
        }
    }

}
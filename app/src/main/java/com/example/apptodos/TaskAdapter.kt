package com.example.apptodos

import android.content.res.ColorStateList
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.apptodos.databinding.ActivityLayoutTodoBinding // <-- Import the generated binding class
import com.example.apptodos.room.Task
import java.text.SimpleDateFormat // <-- Import for date formatting (example)
import java.util.Locale // <-- Import for date formatting (example)
import android.view.View // <-- Import View for visibility control
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.apptodos.room.Priority

enum class TaskAction {
    EDIT, DELETE
}

class TaskAdapter (
    private val onTaskCheckedChange: (task: Task) -> Unit, // Callback checkbox (sudah ada)
    private val onTaskAction: (task: Task, action: TaskAction) -> Unit // Callback BARU untuk aksi menu
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(){

    // Use plural 'tasks' for the list
    private var tasks: MutableList<Task> = mutableListOf()

    // ViewHolder holds the binding object
    inner class TaskViewHolder(val binding: ActivityLayoutTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // Inflate using the generated binding class
        val binding = ActivityLayoutTodoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    // Implement getItemCount correctly
    override fun getItemCount(): Int {
        return tasks.size
    }


    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currentTask = tasks[position]

        holder.binding.tvTitle.text = currentTask.title       // OK (title exists)
        holder.binding.tvDescription.text = currentTask.description // OK (description exists)

        // --- Use the correct property name 'dueDateTimeMillis' ---
        if (currentTask.dueDateTimeMillis != null) { // <-- Use the correct name
            // Using yyyy for year, adjust format as needed
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            holder.binding.tvDueDate.text = sdf.format(currentTask.dueDateTimeMillis) // <-- Use the correct name
            holder.binding.tvDueDate.visibility = View.VISIBLE
        } else {
            holder.binding.tvDueDate.visibility = View.GONE // Hide if no due date
        }
        // --- End of due date handling ---

        // Bind category
        if (currentTask.category.isNullOrEmpty()) { // Check needed only if category could be empty, but it's non-nullable now.
            holder.binding.tvCategory.visibility = View.GONE // Should not happen if category is never empty
        } else {
            holder.binding.tvCategory.text = currentTask.category // OK (category exists)
            holder.binding.tvCategory.visibility = View.VISIBLE
        }


        // --- Handling Priority (Example) ---
        // You need logic here to potentially change the priority indicator color based on currentTask.priority
         val priorityColor = when (currentTask.priority) {
            Priority.Tinggi -> ContextCompat.getColor(holder.itemView.context, R.color.priority_high) // Define these colors
            Priority.Sedang -> ContextCompat.getColor(holder.itemView.context, R.color.priority_medium)
            Priority.Rendah -> ContextCompat.getColor(holder.itemView.context, R.color.priority_low)
         }
         holder.binding.viewPriorityIndicator.backgroundTintList = ColorStateList.valueOf(priorityColor)

        holder.binding.cbDone.setOnCheckedChangeListener(null)
        holder.binding.cbDone.isChecked = currentTask.isCompleted

        updateTaskAppearance(holder.binding, currentTask.isCompleted)
        holder.itemView.isEnabled = true
        if (currentTask.isCompleted) {
            holder.binding.cbDone.isEnabled = false
            holder.itemView.setOnClickListener(null)
        }else{
            holder.binding.cbDone.isEnabled = true
            holder.binding.cbDone.setOnCheckedChangeListener { _, isChecked ->
                    val updatedTask = currentTask.copy(isCompleted = isChecked)
                    tasks[position] = updatedTask // Update list lokal
                    onTaskCheckedChange(updatedTask) // Beri tahu Activity/VM -> DB

                    updateTaskAppearance(holder.binding, isChecked) // Update visual

                    // Langsung nonaktifkan setelah dicentang
                    if (isChecked) {
                        holder.binding.cbDone.isEnabled = false
                        holder.itemView.setOnClickListener(null)
                    }
                }
            }
        holder.binding.ivMore.isEnabled = true
        holder.binding.ivMore.setOnClickListener { view ->
            Log.d("AdapterAction", "Tombol More diklik untuk task: ${currentTask.title}")
            showPopupMenu(view, currentTask)
        }
        // TODO: Add click listeners if needed
    }

    private fun showPopupMenu(anchorView: View, task: Task) {
        val context = anchorView.context
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.task_item_menu, popup.menu) // Inflate menu yg sudah tanpa toggle

        popup.setOnMenuItemClickListener { menuItem ->
            // 3. Hapus case untuk toggle complete
            val action: TaskAction? = when (menuItem.itemId) {
                R.id.action_edit_task -> TaskAction.EDIT
                R.id.action_delete_task -> TaskAction.DELETE
                else -> null
            }

            action?.let {
                onTaskAction(task, it)
            }
            true
        }
        popup.show()
    }

    private fun updateTaskAppearance(binding: ActivityLayoutTodoBinding, isCompleted: Boolean) {
        if (isCompleted) {
            binding.tvTitle.paintFlags = binding.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.tvDescription.paintFlags = binding.tvDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.root.alpha = 0.6f
        } else {
            binding.tvTitle.paintFlags = binding.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.tvDescription.paintFlags = binding.tvDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.root.alpha = 1.0f
        }
    }

    // setData remains the same
    fun setData(newData: List<Task>) {
        this.tasks.clear()
        this.tasks.addAll(newData)
        notifyDataSetChanged() // Consider DiffUtil later for better performance
    }
}
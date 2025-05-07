package com.example.apptodos

import android.content.res.ColorStateList
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.apptodos.databinding.ActivityLayoutTodoBinding
import com.example.apptodos.room.Task
import java.text.SimpleDateFormat
import java.util.Locale
import android.view.View
import android.widget.PopupMenu // Pastikan PopupMenu diimpor dengan benar
import androidx.core.content.ContextCompat
import com.example.apptodos.room.Priority

enum class TaskAction {
    EDIT, DELETE
}

class TaskAdapter (
    private val onTaskCheckedChange: (task: Task) -> Unit,
    private val onTaskAction: (task: Task, action: TaskAction) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(){

    private var tasks: MutableList<Task> = mutableListOf()

    inner class TaskViewHolder(val binding: ActivityLayoutTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ActivityLayoutTodoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return tasks.size
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currentTask = tasks[position]

        holder.binding.tvTitle.text = currentTask.title
        holder.binding.tvDescription.text = currentTask.description

        if (currentTask.dueDateTimeMillis != null) {
            val sdf = SimpleDateFormat("dd MMM yy", Locale.getDefault())
            holder.binding.tvDueDate.text = sdf.format(currentTask.dueDateTimeMillis)
            holder.binding.tvDueDate.visibility = View.VISIBLE
        } else {
            holder.binding.tvDueDate.visibility = View.GONE
        }

        if (currentTask.category.isNullOrEmpty()) {
            holder.binding.tvCategory.visibility = View.GONE
        } else {
            holder.binding.tvCategory.text = currentTask.category
            holder.binding.tvCategory.visibility = View.VISIBLE
        }

        val priorityColor = when (currentTask.priority) {
            Priority.Tinggi -> ContextCompat.getColor(holder.itemView.context, R.color.priority_high)
            Priority.Sedang -> ContextCompat.getColor(holder.itemView.context, R.color.priority_medium)
            Priority.Rendah -> ContextCompat.getColor(holder.itemView.context, R.color.priority_low)
        }
        holder.binding.viewPriorityIndicator.backgroundTintList = ColorStateList.valueOf(priorityColor)

        holder.binding.cbDone.setOnCheckedChangeListener(null)
        holder.binding.cbDone.isChecked = currentTask.isCompleted

        updateTaskAppearance(holder.binding, currentTask.isCompleted)

        if (currentTask.isCompleted) {
            holder.binding.cbDone.isEnabled = false
            holder.itemView.setOnClickListener(null)
        } else {
            holder.binding.cbDone.isEnabled = true
            holder.binding.cbDone.setOnCheckedChangeListener { _, isChecked ->
                val updatedTask = currentTask.copy(isCompleted = isChecked)
                if (position != RecyclerView.NO_POSITION && position < tasks.size) {
                    tasks[position] = updatedTask
                }
                onTaskCheckedChange(updatedTask)
                updateTaskAppearance(holder.binding, isChecked)


                if (isChecked) {
                    holder.binding.cbDone.isEnabled = false
                    holder.itemView.setOnClickListener(null)
                }
            }

        }

        holder.binding.ivMore.isEnabled = true
        holder.binding.ivMore.setOnClickListener { view ->
            Log.d("AdapterAction", "Tombol More diklik untuk task: ${currentTask.title}, isCompleted: ${currentTask.isCompleted}")
            showPopupMenu(view, currentTask)
        }
    }

    private fun showPopupMenu(anchorView: View, task: Task) {
        val context = anchorView.context
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.task_item_menu, popup.menu)

        if (task.isCompleted) {
            val editMenuItem = popup.menu.findItem(R.id.action_edit_task)
            editMenuItem?.isVisible = false
            Log.d("PopupMenu", "Task '${task.title}' is completed. Edit option hidden.")
        } else {
            val editMenuItem = popup.menu.findItem(R.id.action_edit_task)
            editMenuItem?.isVisible = true // Pastikan terlihat jika tugas belum selesai
            Log.d("PopupMenu", "Task '${task.title}' is not completed. Edit option visible.")
        }


        popup.setOnMenuItemClickListener { menuItem ->
            val action: TaskAction? = when (menuItem.itemId) {
                R.id.action_edit_task -> if (!task.isCompleted) TaskAction.EDIT else null // Double check
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

    fun setData(newData: List<Task>) {
        this.tasks.clear()
        this.tasks.addAll(newData)
        notifyDataSetChanged()
    }
}
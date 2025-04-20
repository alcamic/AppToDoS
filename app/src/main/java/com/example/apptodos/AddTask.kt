package com.example.apptodos

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apptodos.room.TaskDB
import com.google.android.material.button.MaterialButton
import android.view.View
import android.widget.Toast

class AddTask : AppCompatActivity() {
    val btnSave by lazy { findViewById<MaterialButton>(R.id.btnSave) }
    val btnCancel by lazy { findViewById<MaterialButton>(R.id.btnCancel) }
    val actvCategory by lazy { findViewById<AutoCompleteTextView>(R.id.actvCategory) }


    private lateinit var adapterItems: ArrayAdapter<String>

    val db by lazy { TaskDB(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
        adapterItems = ArrayAdapter(this, R.layout.dropdown_item, resources.getStringArray(R.array.category_array))
        actvCategory.setAdapter(adapterItems)

        actvCategory.setOnItemClickListener { adapterView, view, position, id ->
            val selectedCategory = adapterView.getItemAtPosition(position).toString()
            Toast.makeText(this, "Selected Category: $selectedCategory", Toast.LENGTH_SHORT).show()
        }
//      SetupListener()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }



//    fun SetupListener(){
//        btnSave.setOnClickListener{
//            CoroutineScope(Dispatchers.IO).launch {
//                db.taskDao().addTask(
//                    Task(
//                        0, etTaskTitle.text.toString(), etTaskDescription.text.toString(),
//                        category = TODO(),
//                        priority = TODO(),
//                        dueDate = TODO(),
//                    )
//            }
//        }
//    }
}
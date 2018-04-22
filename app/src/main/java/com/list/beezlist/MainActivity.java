package com.list.beezlist;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.list.beezlist.adapter.Task;
import com.list.beezlist.adapter.TaskViewHolder;

import java.text.DateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements RecyclerItemTouchHelperListener, AdapterView.OnItemSelectedListener {
    private BottomNavigationView.OnNavigationItemSelectedListener onNavigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
           switch (item.getItemId()){
               case R.id.navigation_tasks:
                   mTextMessage.setText("Home");
                   break;
               case R.id.navigation_calander:
                   Intent intent = new Intent(MainActivity.this,CalendarActivity.class);
                   startActivity(intent);
                   mTextMessage.setText("Calendar");
                   break;
           }
            return false;
        }
    };
    private RecyclerItemTouchHelperListener listener;
    private TextView mTextMessage;
    private RecyclerView recyclerView;
    private DatabaseReference databaseReference, dabRef;
    private FirebaseDatabase database;
    EditText editTitle, editDesc;
    LinearLayout dynamicContent,bottonNavBar;
    RecyclerItemTouchHelper adapter;

    // The interface to transfer the data


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Spinner spinner = findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(this,R.array.calander, android.R.layout.simple_spinner_item);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter1);
        spinner.setOnItemSelectedListener(this);
        database = FirebaseDatabase.getInstance();

        final TextView time = findViewById(R.id.timeHeader);

        Calendar calendar = Calendar.getInstance();
        String currentDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.getTime());
        time.setText(currentDate);

        recyclerView = findViewById(R.id.task_list);
        registerForContextMenu(recyclerView);
        recyclerView.setHasFixedSize(true);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        databaseReference = FirebaseDatabase.getInstance().getReference().child("Tasks");
        dabRef = FirebaseDatabase.getInstance().getReference();

        ItemTouchHelper.SimpleCallback itemTouchHelperCallBack = new RecyclerItemTouchHelper(0, ItemTouchHelper.LEFT,this);
        new ItemTouchHelper(itemTouchHelperCallBack).attachToRecyclerView(recyclerView);

    }


    public void addButtonClicked(View view) {

        editTitle = findViewById(R.id.editTitle);
        editDesc = findViewById(R.id.editDesc);

        String title = editTitle.getText().toString();
        String description = editDesc.getText().toString();
        if (title.matches("")) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Empty Field")
                    .setMessage("Please Write something").show();
            return;
        }
        long date = System.currentTimeMillis();
        final TextView time = findViewById(R.id.timeHeader);
        Calendar calendar = Calendar.getInstance();
        String currentDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.getTime());
        time.setText(currentDate);



        DatabaseReference newTask = databaseReference.push();
        newTask.child("time").setValue(currentDate);
        newTask.child("description").setValue(description);
        newTask.child("title").setValue(title);


    }


    @Override
    protected void onStart() {
        super.onStart();


        FirebaseRecyclerAdapter<Task, TaskViewHolder> FBRA = new FirebaseRecyclerAdapter<Task, TaskViewHolder>(
                Task.class,
                R.layout.task_row,
                TaskViewHolder.class,
                databaseReference
        ) {


            @Override
            protected void populateViewHolder(TaskViewHolder viewHolder, Task model, final int position) {

                viewHolder.setTitle(model.getTitle());
                viewHolder.setTime(model.getTime());
                viewHolder.setDescription(model.getDescription());

                viewHolder.databaseReference = getRef(position);

            }


        };
        recyclerView.setAdapter(FBRA);

    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        databaseReference.child("title").removeValue();
        databaseReference.child("description").removeValue();
        databaseReference.child("time").removeValue();
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String text = parent.getItemAtPosition(position).toString();
        Toast.makeText(parent.getContext(),text,Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}



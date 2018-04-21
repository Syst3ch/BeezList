package com.list.beezlist;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.list.beezlist.adapter.RecyclerViewClickListener;
import com.list.beezlist.adapter.TaskViewHolder;

import org.w3c.dom.Text;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static android.support.v4.os.LocaleListCompat.create;

public class MainActivity extends AppCompatActivity  {

    private RecyclerView recyclerView;
    private DatabaseReference databaseReference,dabRef;
    private FirebaseDatabase database;
    EditText editTitle,editDesc;
    Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = FirebaseDatabase.getInstance();

        final TextView time = findViewById(R.id.timeHeader);

        Calendar calendar = Calendar.getInstance();
        String currentDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.getTime());
        time.setText(currentDate);

        recyclerView =  findViewById(R.id.task_list);
        registerForContextMenu(recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        databaseReference = FirebaseDatabase.getInstance().getReference().child("Tasks");
        dabRef = FirebaseDatabase.getInstance().getReference();

    }


    public void addButtonClicked(View view) {

        editTitle =  findViewById(R.id.editTitle);
        editDesc =  findViewById(R.id.editDesc);

        String title = editTitle.getText().toString();
        String description = editDesc.getText().toString();
        if(title.matches("")){
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

        databaseReference = database.getReference().child("Tasks");

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
            protected void populateViewHolder(TaskViewHolder viewHolder, Task model, int position) {
                DatabaseReference databaseReference;

                viewHolder.setTitle(model.getTitle());
                viewHolder.setTime(model.getTime());
                viewHolder.setDescription(model.getDescription());

                viewHolder.databaseReference = getRef(position);

            }

        };

        recyclerView.setAdapter(FBRA);


    }

}

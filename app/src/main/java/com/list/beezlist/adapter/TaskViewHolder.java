package com.list.beezlist.adapter;

import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.list.beezlist.R;
import com.list.beezlist.Task;


public class TaskViewHolder extends RecyclerView.ViewHolder {

    AlertDialog alertDialog;
    View view;
    public DatabaseReference databaseReference;

    public TaskViewHolder(View itemView) {
        super(itemView);

        databaseReference = FirebaseDatabase.getInstance().getReference("Tasks");

        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override

            public boolean onLongClick(View delete) {

                databaseReference.child("title").removeValue();
                databaseReference.child("description").removeValue();
                databaseReference.child("time").removeValue();


                return false;
            }
        });
    }

    public void setTitle(String title) {

        TextView task_title =  itemView.findViewById(R.id.taskTitle);
        task_title.setText(title);
    }

    public void setDescription(String description) {
        TextView task_desc =  itemView.findViewById(R.id.taskDesc);
        task_desc.setText(description);
    }

    public void setTime(String time) {
        TextView task_time = itemView.findViewById(R.id.taskTime);
        task_time.setText(time);
    }


}
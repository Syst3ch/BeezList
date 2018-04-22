package com.list.beezlist.adapter;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.list.beezlist.R;


public class TaskViewHolder extends RecyclerView.ViewHolder{

    View view;
    public RelativeLayout view_background;
    public LinearLayout view_foreground;
    public DatabaseReference databaseReference;
    public RelativeLayout viewBackground, viewForeground;

    public TaskViewHolder(View itemView) {
        super(itemView);

    view_background = (RelativeLayout)itemView.findViewById(R.id.view_background);
    view_foreground = (LinearLayout)itemView.findViewById(R.id.view_foreground);

        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                // -- Start of Dialog --
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                builder.setMessage("Do you want to delete this item?").setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            // This work once the User click positive
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                databaseReference.child("title").removeValue();
                                databaseReference.child("description").removeValue();
                                databaseReference.child("time").removeValue();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            // This work once the User click negative
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.setTitle("Delete Your Task?");
                dialog.show();
                // -- End of Dialog --

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
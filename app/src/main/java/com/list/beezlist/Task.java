package com.list.beezlist;

import android.widget.Button;

/**
 * Created by SHKEFATI on 19/04/2018.
 */

public class Task {

    private String title,description,time;
    private Button addButton;

    public Task(){

    }

    public Task(String title, String description, String time, Button addButton){
        this.title = title;
        this.description = description;
        this.addButton = addButton;
        this.time = time;

    }

    public Button getAddButton(){
        return addButton;
    }
    public void setAddButton(){
        this.addButton = addButton;
    }
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }



}

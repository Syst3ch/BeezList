package com.list.beezlist.splash;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.ActionMode;
import android.widget.ProgressBar;

import com.list.beezlist.MainActivity;
import com.list.beezlist.R;

public class SplashScreen extends AppCompatActivity {
    private ProgressBar progressBar;
    private int progressStatus = 0;
    private MyProgressBar pb;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        pb = new MyProgressBar(this);



        //Long operation by thread
        new Thread(new Runnable() {
            public void run() {
                while (progressStatus < 100) {
                    progressStatus += 4;
                    //Update progress bar with completion of operation
                    handler.post(new Runnable() {
                        public void run() {
                            progressBar.setProgress(progressStatus);
                            pb.setProgress(progressStatus);
                        }
                    });
                    try {
                        // Sleep for 300 milliseconds.
                        //Just to display the progress slowly
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    class MyProgressBar extends ProgressBar
    {
        public MyProgressBar(Context context) {
            super(context);
        }

        @Override
        public void setProgress(int progress)
        {
            super.setProgress(progress);
            if(progress == this.getMax())
            {
                //Do stuff when progress is max
                Intent intent  = new Intent(SplashScreen.this, MainActivity.class);
                startActivity(intent);
            }else {

            }
        }
    }

    }
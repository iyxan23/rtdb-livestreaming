package com.iyxan23.rtdb.livestream.test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void chooseStreamer(View view) {
        startActivity(new Intent(this, StreamerActivity.class));
    }

    public void chooseViewer(View view) {
        startActivity(new Intent(this, ViewerActivity.class));
    }
}
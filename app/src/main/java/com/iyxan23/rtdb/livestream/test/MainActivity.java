package com.iyxan23.rtdb.livestream.test;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void chooseStreamer(View view) {
        Toast.makeText(this, "Generating a new room id", Toast.LENGTH_SHORT).show();

        String room_id = UUID.randomUUID().toString();

        startActivity(new Intent(this, StreamerActivity.class).putExtra("room_id", room_id));
    }

    public void chooseViewer(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Insert room id");

        EditText editText = new EditText(this);

        builder.setView(editText);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            startActivity(
                    new Intent(
                            this,
                            ViewerActivity.class

                    ).putExtra("room_id", editText.getText().toString())
            );
        });

        builder.create().show();
    }
}
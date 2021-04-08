package com.iyxan23.rtdb.livestream.test;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telecom.Call;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("data", MODE_PRIVATE);

        if (!sp.contains("name")) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Set your name");
            builder.setCancelable(false);

            EditText editText = new EditText(this);

            builder.setView(editText);
            builder.setPositiveButton("Ok", (dialog, which) -> {
                String name = editText.getText().toString();

                if (name.trim().equals("")) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                sp.edit().putString("name", name).apply();
            });

            builder.create().show();
        } else {
            setUsername();
        }
    }

    private void setUsername() {
        String username = sp.getString("name", "Unknown");
        TextView username_text = findViewById(R.id.username);

        username_text.append(username);

        database.getReference("calls")
                .child(username)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        String call_id = snapshot.getKey();
                        String caller = snapshot.child("caller").getValue(String.class);

                        Intent intent = new Intent(MainActivity.this, CallActivity.class);
                        intent.putExtra("call_id", call_id);
                        intent.putExtra("caller", caller);

                        startActivity(intent);
                    }

                    @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }
                    @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) { }
                    @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Error while retrieving calls: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // https://stackoverflow.com/a/157202

    final String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    Random random = new Random();

    String randomString(int len){
        StringBuilder sb = new StringBuilder(len);

        for(int i = 0; i < len; i++)
            sb.append(characters.charAt(random.nextInt(characters.length())));

        return sb.toString();
    }

    public void chooseStreamer(View view) {
        Toast.makeText(this, "Generating a new room id", Toast.LENGTH_SHORT).show();

        String room_id = randomString(5);

        startActivity(new Intent(this, StreamerActivity.class).putExtra("room_id", room_id));
    }

    public void chooseViewer(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Insert room id");

        EditText editText = new EditText(this);

        builder.setView(editText);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            String room_id = editText.getText().toString();

            // Check if room id exists

            DatabaseReference room_reference = database.getReference("stream").child(room_id);

            ValueEventListener check_exists = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        startActivity(
                                new Intent(
                                        MainActivity.this,
                                        ViewerActivity.class

                                ).putExtra("room_id", room_id)
                        );

                        room_reference.removeEventListener(this);
                    } else {
                        Toast.makeText(MainActivity.this, "Room with ID " + room_id + " doesn't exists", Toast.LENGTH_SHORT).show();
                    }

                    dialog.dismiss();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };

            room_reference.addListenerForSingleValueEvent(check_exists);
        });

        builder.create().show();
    }

    public void chooseTwoWaySpeaking(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Insert a username to call");

        EditText editText = new EditText(this);
        editText.setHint("Username to call");

        builder.setView(editText);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            Intent intent = new Intent(MainActivity.this, CallActivity.class);
            intent.putExtra("call_type", CallActivity.CallType.CALLER);
            intent.putExtra("receiver", editText.getText().toString());

            startActivity(intent);
        });

        builder.create().show();
    }
}
package com.iyxan23.rtdb.livestream.test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Base64;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ViewerActivity extends AppCompatActivity {

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference audio_reference;

    final int sampleRate = 16000 ; // 44100 for music
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

    AudioTrack audioTrack;

    ValueEventListener audio_listener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) {
                Toast.makeText(ViewerActivity.this, "Streamer has stopped streaming", Toast.LENGTH_SHORT).show();
                finish();
            }
            
            byte[] data = Base64.decode(snapshot.getValue(String.class), Base64.URL_SAFE);
            new Thread(() -> toSpeaker(data)).start();
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(ViewerActivity.this, "Error while receiving audio: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    };

    List<String> viewers;

    ValueEventListener viewers_listener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (viewers == null) {
                viewers = new ArrayList<>();
            }

            viewers.clear();

            for (DataSnapshot child : snapshot.getChildren()) {
                viewers.add(child.getValue(String.class));
            }

            TextView viewers_text = findViewById(R.id.viewers);
            viewers_text.setText("Viewers: ");

            boolean is_first = true;
            for (String viewer : viewers) {
                if (!is_first) viewers_text.append(",");

                viewers_text.append(viewer);

                is_first = false;
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(ViewerActivity.this, "Error while checking viewers: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    };

    String push_key_user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);

        Intent intent = getIntent();
        String room_id = intent.getStringExtra("room_id");

        getSupportActionBar().setSubtitle("Room ID: " + room_id);

        audio_reference = database.getReference("stream").child(room_id);

        push_key_user = audio_reference.child("viewers").push().getKey();

        if (push_key_user == null) {
            Toast.makeText(this, "Error: Push key is null", Toast.LENGTH_SHORT).show();
            return;
        }

        audio_reference
                .child("viewers")
                .child(push_key_user)
                .setValue(
                        getSharedPreferences("data", MODE_PRIVATE)
                                .getString("name", "Unknown user")
                );

        audio_reference
                .child("viewers")
                .addValueEventListener(viewers_listener);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                audioFormat,
                minBufSize,
                AudioTrack.MODE_STREAM
        );

        audioTrack.play();
        audio_reference.child("audio").addValueEventListener(audio_listener);
    }

    @Override
    protected void onDestroy() {
        audio_reference.child("audio").removeEventListener(audio_listener);
        audio_reference.child("viewers").child(push_key_user).removeValue();

        audioTrack.stop();

        super.onDestroy();
    }

    public void toSpeaker(byte[] soundBytes) {
        audioTrack.write(soundBytes, 0, soundBytes.length);
    }
}
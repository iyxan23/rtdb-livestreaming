package com.iyxan23.rtdb.livestream.test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StreamerActivity extends AppCompatActivity {

    AudioRecord recorder;

    final int sampleRate = 16000 ; // 44100 for music
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    boolean muted = true;

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference stream_reference;

    TextView stream_info;
    Button stream_button;

    List<String> listeners;

    ValueEventListener listeners_listener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            TextView listeners_text = findViewById(R.id.listeners);

            if (listeners == null) {
                listeners = new ArrayList<>();
            }

            listeners.clear();

            for (DataSnapshot child : snapshot.getChildren()) {
                listeners.add(child.getValue(String.class));
            }

            TextView viewers_text = findViewById(R.id.viewers);
            viewers_text.setText("Listeners: ");

            boolean is_first = true;
            for (String listener : listeners) {
                if (!is_first) viewers_text.append(",");

                viewers_text.append(listener);

                is_first = false;
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(StreamerActivity.this, "Error while retreiving viewers: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streamer);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "We need audio permission to stream your mic", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, 1);
        }

        stream_info = findViewById(R.id.stream_info);
        stream_button = findViewById(R.id.stream_button);

        Intent intent = getIntent();
        String room_id = intent.getStringExtra("room_id");

        stream_reference = database.getReference("stream").child(room_id);

        getSupportActionBar().setSubtitle("Room ID: " + room_id);

        stream_button.setOnClickListener(v -> {
            muted = !muted;

            stream_button.setText(muted ? "Start Streaming" : "Stop Streaming");
            stream_info.setText(muted ? "Not streaming" : "Streaming audio");

            if (!muted) {
                startStreaming();
            }
        });

        stream_reference
                .child("viewers")
                .addValueEventListener(listeners_listener);
    }

    Thread stream_thread;

    @Override
    protected void onDestroy() {
        if (stream_thread != null) {
            stream_thread.interrupt();
        }

        stream_reference.child("audio").removeValue();

        super.onDestroy();
    }

    private void startStreaming() {
        stream_thread = new Thread(() -> {
            byte[] buffer = new byte[minBufSize];

            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufSize * 10
            );

            recorder.startRecording();

            while (!muted) {
                minBufSize = recorder.read(buffer, 0, buffer.length);

                String data = new String(
                        Base64.encode(
                                buffer,
                                Base64.URL_SAFE
                        ),
                        StandardCharsets.UTF_8
                );

                stream_reference.child("audio").setValue(data);
            }

        });

        stream_thread.start();
    }
}
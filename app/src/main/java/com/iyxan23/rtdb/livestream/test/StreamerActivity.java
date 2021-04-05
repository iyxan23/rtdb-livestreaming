package com.iyxan23.rtdb.livestream.test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.nio.charset.StandardCharsets;

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
    }

    private void startStreaming() {
        new Thread(() -> {
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

        }).start();
    }
}
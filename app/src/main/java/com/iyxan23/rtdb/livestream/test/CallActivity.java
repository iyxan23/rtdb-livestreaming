package com.iyxan23.rtdb.livestream.test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.nio.charset.StandardCharsets;

public class CallActivity extends AppCompatActivity {

    public enum CallType {
        RECEIVER,
        CALLER
    }

    boolean IN_CALL = false;

    String call_id;
    String other_side;
    String self_username;

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference call_ref;
    DatabaseReference stream_ref;
    DatabaseReference listen_ref;

    final int sampleRate = 16000 ; // 44100 for music
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    boolean muted = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        self_username = getSharedPreferences("data", MODE_PRIVATE).getString("name", "Unknown");

        Intent intent = getIntent();

        CallType call_type = CallType.valueOf(intent.getStringExtra("call_type"));

        if (call_type == CallType.RECEIVER) {
            call_id = intent.getStringExtra("call_id");
            other_side = intent.getStringExtra("caller");

            findViewById(R.id.mute_call).setVisibility(View.GONE);

            FloatingActionButton end_call = findViewById(R.id.end_call);
            end_call.setBackgroundColor(0xFF0AAF12);
            end_call.setImageResource(R.drawable.ic_call);

            call_ref = database
                            .getReference("calls")
                            .child(self_username);

        } else if (call_type == CallType.CALLER) {
            TextView calling = findViewById(R.id.calling);

            other_side = intent.getStringExtra("receiver");

            call_ref = database
                            .getReference("calls")
                            .child(other_side);

            call_ref.child("caller").setValue(self_username);

            calling.append(other_side);

            stream_ref = call_ref.child(self_username);
            listen_ref = call_ref.child(other_side);

            listen_ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        startStreaming();
                        startListening();

                        listen_ref.removeEventListener(this);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(CallActivity.this, "Error while checking for pickup: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        listen_ref.removeEventListener(audio_listener);
        audioTrack.stop();
        if (stream_thread != null) stream_thread.interrupt();
        muted = true;

        super.onDestroy();
    }

    public void endCall(View view) {
        if (!IN_CALL) {
            // This user picked up the call, start streaming
            IN_CALL = true;

            stream_ref = call_ref.child(self_username);
            listen_ref = call_ref.child(other_side);

            startStreaming();
            startListening();

        } else {
            call_ref.removeValue();

            finish();
        }
    }

    Thread stream_thread;

    AudioRecord recorder;

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
                if (Thread.interrupted()) {
                    break;
                }

                minBufSize = recorder.read(buffer, 0, buffer.length);

                String data = new String(
                        Base64.encode(
                                buffer,
                                Base64.URL_SAFE
                        ),
                        StandardCharsets.UTF_8
                );

                stream_ref.setValue(data);
            }

            recorder.stop();

        });

        stream_thread.start();
    }

    AudioTrack audioTrack;

    ValueEventListener audio_listener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) {
                Toast.makeText(CallActivity.this, "Streamer has stopped streaming", Toast.LENGTH_SHORT).show();
                finish();
            }

            byte[] data = Base64.decode(snapshot.getValue(String.class), Base64.URL_SAFE);
            new Thread(() -> toSpeaker(data)).start();
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(CallActivity.this, "Error while receiving audio: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    };

    private void startListening() {
        listen_ref.addValueEventListener(audio_listener);
    }

    public void toSpeaker(byte[] soundBytes) {
        audioTrack.write(soundBytes, 0, soundBytes.length);
    }

    public void muteCall(View view) {
        muted = !muted;

        if (!muted) {
            startStreaming();
        }
    }
}
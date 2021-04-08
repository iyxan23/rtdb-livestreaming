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

    /**
     * Used to identify who are we? the caller or the receiver?
     */
    public enum CallType {
        RECEIVER,
        CALLER
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Stuff important for the call                                                               //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    String call_id;         // The call id
    String other_side;      // The other side's username
    String self_username;   // Our username

    FirebaseDatabase database = FirebaseDatabase.getInstance();

    DatabaseReference call_ref;     // Reference to the call (/calls/USERNAME/CALL_ID)
    DatabaseReference stream_ref;   // Reference to a child that we should stream to (/calls/USERNAME/CALL_ID/OUR_USERNAME)
    DatabaseReference listen_ref;   // Reference to a child that we should listen to (/calls/USERNAME/CALL_ID/THEIR_USERNAME)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Audio configuration                                                                        //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final int sampleRate = 16000 ; // 44100 for music
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Call states                                                                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    boolean muted = true;    // Are we muted?
    boolean in_call = false; // Are we in call?

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Get our username
        self_username = getSharedPreferences("data", MODE_PRIVATE).getString("name", "Unknown");

        // Get the intent's extras
        Intent intent = getIntent();

        // Get the call type, are we a caller or the receiver?
        CallType call_type = CallType.valueOf(intent.getStringExtra("call_type"));

        TextView calling = findViewById(R.id.calling);

        // Check are we the caller or the receiver?
        if (call_type == CallType.RECEIVER) {

            // Get the call id and the caller
            call_id = intent.getStringExtra("call_id");
            other_side = intent.getStringExtra("caller");

            // Set the UI state to "waiting to pick up"
            findViewById(R.id.mute_call).setVisibility(View.GONE);

            FloatingActionButton end_call = findViewById(R.id.end_call);
            end_call.setBackgroundColor(0xFF0AAF12);
            end_call.setImageResource(R.drawable.ic_call);

            // Set the call reference
            call_ref = database
                            .getReference("calls")
                            .child(self_username);

            // Set the calling text
            calling.setText(other_side);
            calling.append(getString(R.string.is_calling_you));

        } else if (call_type == CallType.CALLER) {

            // Get the receiver
            other_side = intent.getStringExtra("receiver");

            // Get the call reference
            call_ref = database
                            .getReference("calls")
                            .child(other_side);

            // Push the call ID, and set the call reference
            call_id = call_ref.push().getKey();

            if (call_id == null) {
                Toast.makeText(this, "Error while pushing", Toast.LENGTH_LONG).show();
                finish();
            }

            call_ref = call_ref.child(call_id);

            // Set the child "caller" in the call reference as our username
            call_ref.child("caller").setValue(self_username);

            // Set the stream and listen references
            stream_ref = call_ref.child(self_username);
            listen_ref = call_ref.child(other_side);

            // Check if the receiver picked up the call
            listen_ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Check if this snapshot exists / user picked up the call
                    if (snapshot.exists()) {

                        // Change UI state
                        Toast.makeText(CallActivity.this, other_side + " picked up the call", Toast.LENGTH_SHORT).show();
                        calling.setText(getString(R.string.talking_to));
                        calling.append(other_side);

                        // Start the call
                        in_call = true;

                        startStreaming();
                        startListening();

                        // Don't forget to remove this listener
                        listen_ref.removeEventListener(this);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(CallActivity.this, "Error while checking for pickup: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        // Set the calling TextView to be "Calling " + other_side
        calling.append(other_side);
    }

    @Override
    protected void onDestroy() {
        // Exit gracefully
        if (in_call) {
            listen_ref.removeEventListener(audio_listener);
            audioTrack.stop();
            muted = true;

            if (stream_thread != null) stream_thread.interrupt();
        }

        super.onDestroy();
    }

    public void endCall(View view) {
        if (!in_call) {
            // This user picked up the call, start streaming
            in_call = true;

            stream_ref = call_ref.child(self_username);
            listen_ref = call_ref.child(other_side);

            startStreaming();
            startListening();

            // Restore the UI to the talking state

            findViewById(R.id.mute_call).setVisibility(View.VISIBLE);

            FloatingActionButton end_call = findViewById(R.id.end_call);
            end_call.setBackgroundColor(0xFFCD1A14);
            end_call.setImageResource(R.drawable.ic_call_end);

        } else {
            // User ended the call, remove everything
            call_ref.removeValue();

            finish();
        }
    }

    Thread stream_thread;

    AudioRecord recorder;

    private void startStreaming() {
        // Set the stream thread
        stream_thread = new Thread(() -> {

            // Allocate a new buffer for our audio
            byte[] buffer = new byte[minBufSize];

            // Initialize the recorder
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufSize * 10
            );

            // Start the recorder
            recorder.startRecording();

            // Are we not muted?
            while (!muted) {
                if (Thread.interrupted()) {
                    break;
                }

                // Read an audio frame
                recorder.read(buffer, 0, buffer.length);

                // Encode it as base64
                String data = new String(
                        Base64.encode(
                                buffer,
                                Base64.URL_SAFE
                        ),
                        StandardCharsets.UTF_8
                );

                // Set the stream reference to the audio frame, and let the other side receive it
                stream_ref.setValue(data);
            }

            // We're muted, stop the recorder
            recorder.stop();

        });

        stream_thread.start();
    }

    AudioTrack audioTrack;

    ValueEventListener audio_listener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) {
                Toast.makeText(CallActivity.this, "Other side has stopped streaming, end our call", Toast.LENGTH_SHORT).show();
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
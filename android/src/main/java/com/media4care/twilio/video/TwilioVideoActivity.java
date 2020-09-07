package com.media4care.twilio.video;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.media4care.twilio.video.capacitortwiliovideo.R;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.video.AudioCodec;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.EncodingParameters;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoCodec;
import com.twilio.video.VideoRenderer;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;
import java.util.Collections;
import kotlin.Unit;

public class TwilioVideoActivity extends AppCompatActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;

    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";
    private static final String TAG = "TwilioVideoActivity";

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;
    private LocalParticipant localParticipant;

    /*
     * AudioCodec and VideoCodec represent the preferred codec for encoding and decoding audio and
     * video.
     */
    private AudioCodec audioCodec;
    private VideoCodec videoCodec;

    /*
     * Encoding parameters represent the sender side bandwidth constraints.
     */
    private EncodingParameters encodingParameters;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    /*
     * Android application UI elements
     */
    private CameraCapturerCompat cameraCapturerCompat;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton muteActionFab;

    /*
     * Audio management
     */
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;

    private VideoRenderer localVideoView;
    private boolean disconnectedFromOnDestroy;
    private String remoteParticipantIdentity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_twilio_video);

        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        Bundle arguments = getIntent().getExtras();
        if (arguments == null) Log.d(TAG, "roomName and accessToken are missing");

        primaryVideoView = findViewById(R.id.primary_video_view);
        thumbnailVideoView = findViewById(R.id.thumbnail_video_view);
        switchCameraActionFab = findViewById(R.id.switch_camera_action_fab);
        muteActionFab = findViewById(R.id.mute_action_fab);

        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            connectToRoom(arguments.getString("roomName"), arguments.getString("accessToken"));
        }

        initializeUI();
    }

    @Override
    protected void onDestroy() {
        audioSwitch.stop();
        setVolumeControlStream(savedVolumeControlStream);

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }

        super.onDestroy();
    }

    private void initializeUI() {
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        muteActionFab.setOnClickListener(muteClickListener());
    }

    private boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED && resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone() {
        if (
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
        ) {
            // TODO notify that permission is required
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO },
                CAMERA_MIC_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {
                createAudioAndVideoTracks();
            } else {
                // TODO notify that permission is required
            }
        }
    }

    private void createAudioAndVideoTracks() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);

        // Share your camera
        cameraCapturerCompat = new CameraCapturerCompat(this, getAvailableCameraSource());
        localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturerCompat.getVideoCapturer(), LOCAL_VIDEO_TRACK_NAME);
        primaryVideoView.setMirror(true);
        localVideoTrack.addRenderer(primaryVideoView);
        localVideoView = primaryVideoView;

        audioSwitch.start(
            (audioDevices, audioDevice) -> {
                // TODO Enable user select audio device
                return Unit.INSTANCE;
            }
        );
    }

    private CameraCapturer.CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA))
            ? (CameraCapturer.CameraSource.FRONT_CAMERA)
            : (CameraCapturer.CameraSource.BACK_CAMERA);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.removeRenderer(primaryVideoView);
            localVideoTrack.addRenderer(thumbnailVideoView);
            localVideoView = thumbnailVideoView;
            thumbnailVideoView.setMirror(cameraCapturerCompat.getCameraSource() == CameraCapturer.CameraSource.FRONT_CAMERA);
        }
    }

    private void moveLocalVideoToPrimaryView() {
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            thumbnailVideoView.setVisibility(View.GONE);
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(thumbnailVideoView);
                localVideoTrack.addRenderer(primaryVideoView);
            }
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(cameraCapturerCompat.getCameraSource() == CameraCapturer.CameraSource.FRONT_CAMERA);
        }
    }

    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            // TODO Support group calls
            return;
        }

        remoteParticipantIdentity = remoteParticipant.getIdentity();

        /*
         * Add remote participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication = remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant events
         */
        remoteParticipant.setListener(remoteParticipantListener());
    }

    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        moveLocalVideoToThumbnailView();
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);
    }

    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            // TODO Support group calls
            return;
        }

        /*
         * Remove remote participant renderer
         */
        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            RemoteVideoTrackPublication remoteVideoTrackPublication = remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }
        moveLocalVideoToPrimaryView();
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeRenderer(primaryVideoView);
    }

    private void connectToRoom(String roomName, String accessToken) {
        audioSwitch = new AudioSwitch(getApplicationContext());

        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        createAudioAndVideoTracks();

        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken).roomName(roomName);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        /*
         * Toggles automatic track subscription. If set to false, the LocalParticipant will receive
         * notifications of track publish events, but will not automatically subscribe to them. If
         * set to true, the LocalParticipant will automatically subscribe to tracks as they are
         * published. If unset, the default is true. Note: This feature is only available for Group
         * Rooms. Toggling the flag in a P2P room does not modify subscription behavior.
         */
        connectOptionsBuilder.enableAutomaticSubscription(true);

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {

            @Override
            public void onConnected(Room room) {
                localParticipant = room.getLocalParticipant();

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {}

            @Override
            public void onReconnected(@NonNull Room room) {}

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                audioSwitch.deactivate();
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                localParticipant = null;

                TwilioVideoActivity.this.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    audioSwitch.deactivate();
                    moveLocalVideoToPrimaryView();
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                addRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                removeRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onRecordingStarted(Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {

            @Override
            public void onAudioTrackPublished(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onAudioTrackPublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onAudioTrackUnpublished(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onAudioTrackUnpublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onDataTrackPublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(
                    TAG,
                    String.format(
                        "onDataTrackPublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.i(
                    TAG,
                    String.format(
                        "onDataTrackUnpublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onVideoTrackPublished(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onVideoTrackPublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onVideoTrackUnpublished(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onVideoTrackUnpublished: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                        "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()
                    )
                );
            }

            @Override
            public void onAudioTrackSubscribed(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication,
                RemoteAudioTrack remoteAudioTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onAudioTrackSubscribed: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()
                    )
                );
            }

            @Override
            public void onAudioTrackUnsubscribed(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication,
                RemoteAudioTrack remoteAudioTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onAudioTrackUnsubscribed: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()
                    )
                );
            }

            @Override
            public void onAudioTrackSubscriptionFailed(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication,
                TwilioException twilioException
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onAudioTrackSubscriptionFailed: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteAudioTrackPublication: sid=%b, name=%s]" +
                        "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()
                    )
                );
            }

            @Override
            public void onDataTrackSubscribed(
                RemoteParticipant remoteParticipant,
                RemoteDataTrackPublication remoteDataTrackPublication,
                RemoteDataTrack remoteDataTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onDataTrackSubscribed: " + "[RemoteParticipant: identity=%s], " + "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()
                    )
                );
            }

            @Override
            public void onDataTrackUnsubscribed(
                RemoteParticipant remoteParticipant,
                RemoteDataTrackPublication remoteDataTrackPublication,
                RemoteDataTrack remoteDataTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onDataTrackUnsubscribed: " + "[RemoteParticipant: identity=%s], " + "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()
                    )
                );
            }

            @Override
            public void onDataTrackSubscriptionFailed(
                RemoteParticipant remoteParticipant,
                RemoteDataTrackPublication remoteDataTrackPublication,
                TwilioException twilioException
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onDataTrackSubscriptionFailed: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteDataTrackPublication: sid=%b, name=%s]" +
                        "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()
                    )
                );
            }

            @Override
            public void onVideoTrackSubscribed(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication,
                RemoteVideoTrack remoteVideoTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onVideoTrackSubscribed: " + "[RemoteParticipant: identity=%s], " + "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()
                    )
                );
                addRemoteParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackUnsubscribed(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication,
                RemoteVideoTrack remoteVideoTrack
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onVideoTrackUnsubscribed: " + "[RemoteParticipant: identity=%s], " + "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()
                    )
                );
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication,
                TwilioException twilioException
            ) {
                Log.i(
                    TAG,
                    String.format(
                        "onVideoTrackSubscriptionFailed: " +
                        "[RemoteParticipant: identity=%s], " +
                        "[RemoteVideoTrackPublication: sid=%b, name=%s]" +
                        "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()
                    )
                );
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {}

            @Override
            public void onAudioTrackDisabled(
                RemoteParticipant remoteParticipant,
                RemoteAudioTrackPublication remoteAudioTrackPublication
            ) {}

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {}

            @Override
            public void onVideoTrackDisabled(
                RemoteParticipant remoteParticipant,
                RemoteVideoTrackPublication remoteVideoTrackPublication
            ) {}
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return v -> {
            if (cameraCapturerCompat != null) {
                CameraCapturer.CameraSource cameraSource = cameraCapturerCompat.getCameraSource();
                cameraCapturerCompat.switchCamera();
                if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                    thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                } else {
                    primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return v -> {
            /*
             * Enable/disable the local audio track. The results of this operation are
             * signaled to other Participants in the same Room. When an audio track is
             * disabled, the audio is muted.
             */
            if (localAudioTrack != null) {
                boolean enable = !localAudioTrack.isEnabled();
                localAudioTrack.enable(enable);
                int icon = enable ? R.drawable.ic_mic_white_24dp : R.drawable.ic_mic_off_black_24dp;
                muteActionFab.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
            }
        };
    }
}

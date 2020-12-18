package com.media4care.twilio.video;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.getcapacitor.JSObject;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.media4care.twilio.video.capacitortwiliovideo.R;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.audioswitch.AudioDevice;
import com.twilio.video.AudioCodec;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.EncodingParameters;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalAudioTrackPublication;
import com.twilio.video.LocalDataTrack;
import com.twilio.video.LocalDataTrackPublication;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.LocalVideoTrackPublication;
import com.twilio.video.NetworkQualityConfiguration;
import com.twilio.video.NetworkQualityLevel;
import com.twilio.video.NetworkQualityVerbosity;
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
import org.json.JSONException;

import java.util.List;


public class TwilioVideoActivity extends AppCompatActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;

    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";
    private static final String TAG = "TwilioVideoActivity";
    public static final String CLOSE_EVENT = "close-twilio-activity";
    public static final String SEND_EVENT = "send-twilio-event";

    private final int CONTROLS_ANIMATION_DELAY = 6000;
    private final int CONTROLS_ANIMATION_DURATION = 300;

    private JSObject pluginOptions;
    private final String AUTO_HIDE_CONTROLS_OPTION = "autoHideControls";
    private final String SHOW_SWITCH_CAMERA_OPTION = "showSwitchCamera";
    private final String SHOW_MUTE_OPTION = "showMute";
    private final String SHOW_DISABLE_VIDEO = "showDisableVideo";
    private final String CONTROLS_POSITION_OPTION = "controlsPosition";
    private final String BUTTON_SIZE_OPTION = "buttonSize";
    private final String STATUS_TEXT_CONNECTING = "connectingText";
    private final String STATUS_TEXT_RECONNECTING = "reconnectingText";
    private final String UNSTABLE_CONNECTION_TEXT = "unstableConnectionText";
    private final String BAD_CONNECTION_TEXT = "badConnectionText";
    private final String SHOW_AUDIO_CONTROLS = "showAudioControls";

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
    private FloatingActionButton bluetoothActionFab;
    private FloatingActionButton speakerActionFab;
    private FloatingActionButton headsetActionFab;
    private FloatingActionButton audioOptionsFab;

    private FloatingActionButton toggleCameraActionFab;
    private TextView statusText;
    private LinearLayout controls;
    private LinearLayout audioControls;
    private FloatingActionButton hangupActionFab;
    private FloatingActionButton bigHangupActionFab;

    private LinearLayout networkQuality;
    private TextView localParticipantNQStatus;
    private ImageView signalIcon;
    /*
     * Audio management
     */
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;

    private VideoRenderer localVideoView;
    private boolean disconnectedFromOnDestroy;
    private String remoteParticipantIdentity;

    private AudioManager audioManager;

    private boolean isBluetoothConnected;
    private boolean isHeadsetConnected;
    private boolean areAudioOptionsVisible = false;

    private boolean isExternalDeviceConnected = false;
    private boolean showAudioControls = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_twilio_video);

        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        Bundle arguments = getIntent().getExtras();
        if (arguments == null) Log.d(TAG, "roomName and accessToken are missing");

        primaryVideoView = findViewById(R.id.primary_video_view);
        thumbnailVideoView = findViewById(R.id.thumbnail_video_view);
        hangupActionFab = findViewById(R.id.hangup_action_fab);
        bigHangupActionFab = findViewById(R.id.big_hangup_action_fab);
        switchCameraActionFab = findViewById(R.id.switch_camera_action_fab);
        muteActionFab = findViewById(R.id.mute_action_fab);

        speakerActionFab = findViewById(R.id.speaker_action_fab);
        bluetoothActionFab = findViewById(R.id.bluetooth_action_fab);
        headsetActionFab = findViewById(R.id.headset_action_fab);
        audioOptionsFab = findViewById(R.id.audio_options_fab);

        toggleCameraActionFab = findViewById(R.id.toggle_cam_action_fab);
        controls = findViewById(R.id.controls);
        audioControls = findViewById(R.id.audio_controls);
        statusText = findViewById(R.id.statusText);

        networkQuality = findViewById(R.id.networkQualityToolbar);
        localParticipantNQStatus = findViewById(R.id.localParticipantQuality);
        signalIcon = findViewById(R.id.signalIcon);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            connectToRoom(arguments.getString("roomName"), arguments.getString("accessToken"));
        }

        try {
            pluginOptions = new JSObject(arguments.getString("options"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        initializeUI();

        showForAFewSeconds();

        LocalBroadcastManager.getInstance(this).registerReceiver(MessageReceiver, new IntentFilter(CLOSE_EVENT));
    }

    @Override
    public void onUserInteraction() {
        showForAFewSeconds();
        super.onUserInteraction();
    }

    @Override
    public void onBackPressed() {
        // Not calling super.onBackPressed() disables back button
    }

    @Override
    protected void onStop() {

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

        super.onStop();
    }

    private boolean canCancelAnimation() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    private void refreshControls() {
        if (canCancelAnimation()){
            controls.animate().cancel();
            if (showAudioControls && isExternalDeviceConnected) audioControls.animate().cancel();
        }
        controls.setVisibility(View.GONE);
        audioControls.setVisibility(View.GONE);
        showForAFewSeconds();
    }

    private void showForAFewSeconds() {
        Boolean autoHide = pluginOptions.getBoolean(AUTO_HIDE_CONTROLS_OPTION, true);

        if (controls.getVisibility() == View.VISIBLE || !autoHide) return;

        controls.setVisibility(View.VISIBLE);
        if (showAudioControls && isExternalDeviceConnected) audioControls.setVisibility(View.VISIBLE);

        controls
            .animate()
            .alpha(0f)
            .setStartDelay(CONTROLS_ANIMATION_DELAY)
            .setDuration(CONTROLS_ANIMATION_DURATION)
            .setListener(
                new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        controls.setVisibility(View.GONE);
                        controls.setAlpha(1f);
                    }
                }
            );

        if (showAudioControls && isExternalDeviceConnected) audioControls
            .animate()
            .alpha(0f)
            .setStartDelay(CONTROLS_ANIMATION_DELAY)
            .setDuration(CONTROLS_ANIMATION_DURATION)
            .setListener(
                new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        hideAudioOptions();
                        audioControls.setVisibility(View.GONE);
                        audioControls.setAlpha(1f);
                    }
                }
            );
    }

    private void hideAudioOptions () {
        speakerActionFab.setVisibility(View.GONE);
        bluetoothActionFab.setVisibility(View.GONE);
        headsetActionFab.setVisibility(View.GONE);
        areAudioOptionsVisible = false;
    }

    private void toggleAudioOptions() {
        if (areAudioOptionsVisible) {
            hideAudioOptions();
        } else {
            AudioDevice selectedDevice = audioSwitch.getSelectedAudioDevice();
            if (!(selectedDevice instanceof AudioDevice.Speakerphone)) speakerActionFab.setVisibility(View.VISIBLE);
            if (!(selectedDevice instanceof AudioDevice.BluetoothHeadset) && isBluetoothConnected) bluetoothActionFab.setVisibility(View.VISIBLE);
            if (!(selectedDevice instanceof AudioDevice.WiredHeadset) && isHeadsetConnected) headsetActionFab.setVisibility(View.VISIBLE);
            areAudioOptionsVisible = true;
        }
    }

    private void initializeUI() {
        Boolean autoHide = pluginOptions.getBoolean(AUTO_HIDE_CONTROLS_OPTION, true);
        Boolean showSwitchCamera = pluginOptions.getBoolean(SHOW_SWITCH_CAMERA_OPTION, true);
        Boolean showMute = pluginOptions.getBoolean(SHOW_MUTE_OPTION, true);
        Boolean showToggleCamera = pluginOptions.getBoolean(SHOW_DISABLE_VIDEO, true);
        showAudioControls = pluginOptions.getBoolean(SHOW_AUDIO_CONTROLS, true);
        String position = pluginOptions.getString(CONTROLS_POSITION_OPTION, "bottom_end");
        String buttonSize = pluginOptions.getString(BUTTON_SIZE_OPTION, "normal");
        String connectingText = pluginOptions.getString(STATUS_TEXT_CONNECTING, "Connecting...");

        statusText.setText(connectingText);

        controls.setVisibility(autoHide ? View.GONE : View.VISIBLE);
        audioControls.setVisibility(autoHide || !showAudioControls ? View.GONE : View.VISIBLE);

        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) controls.getLayoutParams();
        params.gravity = getLayoutGravity(position);

        hangupActionFab.setScaleType(ImageView.ScaleType.CENTER);

        switchCameraActionFab.setVisibility(showSwitchCamera ? View.VISIBLE : View.INVISIBLE);
        muteActionFab.setVisibility(showMute ? View.VISIBLE : View.INVISIBLE);
        toggleCameraActionFab.setVisibility(showToggleCamera ? View.VISIBLE : View.INVISIBLE);

        hideAudioOptions();

        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        muteActionFab.setOnClickListener(muteClickListener());
        toggleCameraActionFab.setOnClickListener(toggleCamClickListener());

        audioOptionsFab.setOnClickListener(audioOptionsFabClickListener());
        bluetoothActionFab.setOnClickListener(bluetoothActionFabClickListener());
        speakerActionFab.setOnClickListener(speakerActionFabClickListener());
        headsetActionFab.setOnClickListener(headsetActionFabClickListener());

        if (buttonSize.equals("large")) {
            bigHangupActionFab.setOnClickListener(hangupClickListener());

            bigHangupActionFab.setVisibility(View.VISIBLE);
            hangupActionFab.setVisibility(View.GONE);
        } else if (buttonSize.equals("normal")) {
            hangupActionFab.setOnClickListener(hangupClickListener());

            hangupActionFab.setVisibility(View.VISIBLE);
            bigHangupActionFab.setVisibility(View.GONE);
        } else {
            Log.e(TAG, BUTTON_SIZE_OPTION + " option is not valid");
        }
    }

    private int getLayoutGravity(String position) {
        int result = Gravity.BOTTOM | Gravity.END;

        if (position.equals("bottom_center")) {
            result = Gravity.BOTTOM | Gravity.CENTER;
        } else if (position.equals("bottom_start")) {
            result = Gravity.BOTTOM | Gravity.START;
        } else if (position.equals("bottom_end")) {
            result = Gravity.BOTTOM | Gravity.END;
        } else {
            Log.e(TAG, CONTROLS_POSITION_OPTION + " option is not valid");
        }

        return result;
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

        configureAudioDevice();
    }

    private void configureAudioDevice() {

        AudioDeviceInfo[] nativeDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        isExternalDeviceConnected = false;

        for (AudioDeviceInfo device : nativeDevices) {
            int t = device.getType();
            if (
                t != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE &&
                t != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
                t != AudioDeviceInfo.TYPE_TELEPHONY
            ) {
                isExternalDeviceConnected = true;
                break;
            }
        }

        audioSwitch.start(
            (audioDevices, selectedAudioDevice) -> {

                isBluetoothConnected = false;
                isHeadsetConnected = false;

                // hide/show external audio buttons if present
                for (AudioDevice audioDevice: audioDevices) {
                    if (audioDevice instanceof AudioDevice.BluetoothHeadset) isBluetoothConnected = true;
                    else if (audioDevice instanceof AudioDevice.WiredHeadset) isHeadsetConnected = true;
                }

                boolean isSpeakerphoneSelected = selectedAudioDevice instanceof AudioDevice.Speakerphone;

                // update external audio buttons is selected
                boolean isBluetoothSelected = selectedAudioDevice instanceof AudioDevice.BluetoothHeadset;
                boolean isHeadsetSelected = selectedAudioDevice instanceof AudioDevice.WiredHeadset;

                isExternalDeviceConnected = isBluetoothConnected || isHeadsetConnected;

                // update audio main icon
                if (isExternalDeviceConnected) {
                    int icon = isBluetoothSelected
                        ? R.drawable.ic_bluetooth_white_24dp
                        : isHeadsetSelected
                            ? R.drawable.ic_headset_white_24dp
                            : R.drawable.ic_speaker_white_24dp;
                    audioOptionsFab.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
                }

                if (areAudioOptionsVisible){
                    bluetoothActionFab.setVisibility(isBluetoothConnected && !isBluetoothSelected ? View.VISIBLE: View.GONE);
                    headsetActionFab.setVisibility(isHeadsetConnected && !isHeadsetSelected ? View.VISIBLE: View.GONE);
                    speakerActionFab.setVisibility(!isSpeakerphoneSelected ? View.VISIBLE: View.GONE);
                }

                if (isExternalDeviceConnected && showAudioControls) refreshControls();

                return Unit.INSTANCE;
            }
        );

        if (!isExternalDeviceConnected && audioSwitch.getSelectedAudioDevice() instanceof AudioDevice.Earpiece) selectSpeakerAsAudioOutput();


    }

    private CameraCapturer.CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA))
            ? (CameraCapturer.CameraSource.FRONT_CAMERA)
            : (CameraCapturer.CameraSource.BACK_CAMERA);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(primaryVideoView);
                localVideoTrack.addRenderer(thumbnailVideoView);
            } else {
                Log.e(TAG, "localVideoTrack is null");
            }
            localVideoView = thumbnailVideoView;
            if (cameraCapturerCompat != null) {
                thumbnailVideoView.setMirror(cameraCapturerCompat.getCameraSource() == CameraCapturer.CameraSource.FRONT_CAMERA);
            } else {
                Log.e(TAG, "cameraCapturerCompat is null");
            }

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
            if (cameraCapturerCompat != null) {
                primaryVideoView.setMirror(cameraCapturerCompat.getCameraSource() == CameraCapturer.CameraSource.FRONT_CAMERA);
            } else {
                Log.e(TAG, "cameraCapturerCompat is null");
            }
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
        statusText.setVisibility(View.GONE);
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


    private void selectSpeakerAsAudioOutput() {
        List<AudioDevice> devices = audioSwitch.getAvailableAudioDevices();
        for (AudioDevice audioDevice: devices) {
            if(audioDevice instanceof AudioDevice.Speakerphone) {
                audioSwitch.selectDevice(audioDevice);
                break;
            }
        }
    }

    private void selectBluetoothHeadsetAsAudioOutput() {
        List<AudioDevice> devices = audioSwitch.getAvailableAudioDevices();
        for (AudioDevice audioDevice: devices) {
            if(audioDevice instanceof AudioDevice.BluetoothHeadset) {
                audioSwitch.selectDevice(audioDevice);
                return;
            }
        }
    }

    private void selectWiredHeadsetAsAudioOutput() {
        List<AudioDevice> devices = audioSwitch.getAvailableAudioDevices();
        for (AudioDevice audioDevice: devices) {
            if(audioDevice instanceof AudioDevice.WiredHeadset) {
                audioSwitch.selectDevice(audioDevice);
                return;
            }
        }
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

        connectOptionsBuilder.enableNetworkQuality(true);

        NetworkQualityConfiguration nqConfiguration = new NetworkQualityConfiguration(
                NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL,
                NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL
        );

        connectOptionsBuilder.networkQualityConfiguration(nqConfiguration);

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
    }

    private void sendTwilioEvent(String event) {
        sendTwilioEvent(event, "");
    }

    private void sendTwilioEvent(String event, String detail) {
        Intent intent = new Intent(SEND_EVENT);
        intent.putExtra("event", event);
        intent.putExtra("detail", detail);
        LocalBroadcastManager.getInstance(TwilioVideoActivity.this).sendBroadcast(intent);
    }

    private Room.Listener roomListener() {
        String TWILIO_EVENT_CONNECTED = "connected";
        String TWILIO_EVENT_RECONNECTING = "reconnecting";
        String TWILIO_EVENT_RECONNECTED = "reconnected";
        String TWILIO_EVENT_CONNECTION_FAILED = "connection-failed";
        String TWILIO_EVENT_DISCONNECTED = "disconnected";
        String TWILIO_EVENT_PARTICIPANT_CONNECTED = "participant-connected";
        String TWILIO_EVENT_PARTICIPANT_DISCONNECTED = "participant-disconnected";

        return new Room.Listener() {

            @Override
            public void onConnected(Room room) {
                audioSwitch.activate();
                localParticipant = room.getLocalParticipant();
                localParticipant.setListener(localParticipantListener());

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }

                sendTwilioEvent(TWILIO_EVENT_CONNECTED);
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {
                sendTwilioEvent(TWILIO_EVENT_RECONNECTING);

                String reconnectingText = pluginOptions.getString(STATUS_TEXT_RECONNECTING, "Reconnecting...");

                statusText.setVisibility(View.VISIBLE);
                statusText.setText(reconnectingText);
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                sendTwilioEvent(TWILIO_EVENT_RECONNECTED);

                statusText.setVisibility(View.GONE);
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                audioSwitch.deactivate();
                sendTwilioEvent(TWILIO_EVENT_CONNECTION_FAILED);
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

                sendTwilioEvent(TWILIO_EVENT_DISCONNECTED);
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                addRemoteParticipant(remoteParticipant);
                sendTwilioEvent(TWILIO_EVENT_PARTICIPANT_CONNECTED);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                removeRemoteParticipant(remoteParticipant);
                sendTwilioEvent(TWILIO_EVENT_PARTICIPANT_DISCONNECTED);
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

            @Override
            public void onNetworkQualityLevelChanged(@NonNull RemoteParticipant remoteParticipant, @NonNull NetworkQualityLevel networkQualityLevel) {
                // TODO: Show remote participant connection quality
            }
        };
    }

    private LocalParticipant.Listener localParticipantListener() {
        return new LocalParticipant.Listener() {
            @Override
            public void onNetworkQualityLevelChanged(@NonNull LocalParticipant localParticipant, @NonNull NetworkQualityLevel networkQualityLevel) {
                String unstableConnection = pluginOptions.getString(UNSTABLE_CONNECTION_TEXT, "Unstable connection");
                String badConnection = pluginOptions.getString(UNSTABLE_CONNECTION_TEXT, "Bad connection");

                if (networkQualityLevel == NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO || networkQualityLevel == NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ONE) {
                    int color = Color.parseColor("#F44336");
                    networkQuality.setVisibility(View.VISIBLE);
                    localParticipantNQStatus.setText(badConnection);
                    localParticipantNQStatus.setTextColor(color);
                    signalIcon.setColorFilter(color);
                } else if (networkQualityLevel == NetworkQualityLevel.NETWORK_QUALITY_LEVEL_TWO) {
                    int color = Color.parseColor("#FFEB3B");
                    networkQuality.setVisibility(View.VISIBLE);
                    localParticipantNQStatus.setText(unstableConnection);
                    localParticipantNQStatus.setTextColor(color);
                    signalIcon.setColorFilter(color);
                } else {
                    networkQuality.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAudioTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalAudioTrackPublication localAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalAudioTrack localAudioTrack, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalVideoTrackPublication localVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalVideoTrack localVideoTrack, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onDataTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalDataTrackPublication localDataTrackPublication) {

            }

            @Override
            public void onDataTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalDataTrack localDataTrack, @NonNull TwilioException twilioException) {

            }
        };
    }

    private View.OnClickListener hangupClickListener() {
        return v -> {
            sendTwilioEvent("hangup");
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

    private View.OnClickListener audioOptionsFabClickListener() {
        return v -> {
            toggleAudioOptions();
        };
    }

    private View.OnClickListener bluetoothActionFabClickListener() {
        return v -> {
            selectBluetoothHeadsetAsAudioOutput();
        };
    }

    private View.OnClickListener speakerActionFabClickListener() {
        return v -> {
            selectSpeakerAsAudioOutput();
        };
    }

    private View.OnClickListener headsetActionFabClickListener() {
        return v -> {
            selectWiredHeadsetAsAudioOutput();
        };
    }

    private View.OnClickListener toggleCamClickListener() {
        return v -> {
          if (localVideoTrack != null) {
              boolean enable = !localVideoTrack.isEnabled();
              localVideoTrack.enable(enable);
              int icon = enable ? R.drawable.ic_videocam_white_24dp : R.drawable.ic_videocam_off_black_24dp;
              toggleCameraActionFab.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
          }
        };
    }

    private BroadcastReceiver MessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };
}

package com.openim.tophone.telecom;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.openim.tophone.R;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.utils.PhoneLocationHelper;

import java.util.Locale;

/**
 * Full-screen call UI shown while this app is the system default dialer.
 */
public class InCallActivity extends AppCompatActivity {
    private static final long TIMER_INTERVAL_MS = 1000L;
    private static final long FINISH_DELAY_MS = 800L;
    private static final String LOCATION_REQUEST_TAG = "InCallActivityLocation";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView avatarText;
    private TextView titleText;
    private TextView numberText;
    private TextView locationText;
    private TextView statusText;
    private ImageButton muteButton;
    private ImageButton speakerButton;
    private LinearLayout activeControls;
    private LinearLayout incomingControls;
    private View endCallContainer;
    private boolean receiverRegistered;
    private int callState = Call.STATE_DISCONNECTED;
    private String phoneNumber = "";
    private boolean incoming;
    private long connectedAt;
    private String requestedLocationNumber = "";

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (callState != Call.STATE_ACTIVE) {
                return;
            }
            renderDuration();
            handler.postDelayed(this, TIMER_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readCallState(intent);
            renderCallState();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(R.layout.activity_in_call);
        bindViews();
        bindActions();
        readCallState(getIntent());
        renderCallState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readCallState(intent);
        renderCallState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            registerReceiver(
                    callStateReceiver,
                    new IntentFilter(ToPhoneInCallService.ACTION_CALL_STATE_CHANGED)
            );
            receiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int serviceState = ToPhoneInCallService.getCurrentState();
        if (serviceState != Call.STATE_DISCONNECTED) {
            callState = serviceState;
            phoneNumber = ToPhoneInCallService.getCurrentNumber();
            incoming = ToPhoneInCallService.isCurrentIncoming();
            connectedAt = ToPhoneInCallService.getCurrentConnectedAt();
        }
        renderCallState();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(callStateReceiver);
            receiverRegistered = false;
        }
        handler.removeCallbacks(timerRunnable);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        N.clearDispose(LOCATION_REQUEST_TAG);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (callState == Call.STATE_DISCONNECTED) {
            super.onBackPressed();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.call_screen_background));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
    }

    private void bindViews() {
        avatarText = findViewById(R.id.callAvatarText);
        titleText = findViewById(R.id.callTitleText);
        numberText = findViewById(R.id.callNumberText);
        locationText = findViewById(R.id.callLocationText);
        statusText = findViewById(R.id.callStatusText);
        muteButton = findViewById(R.id.callMuteButton);
        speakerButton = findViewById(R.id.callSpeakerButton);
        activeControls = findViewById(R.id.callActiveControls);
        incomingControls = findViewById(R.id.callIncomingControls);
        endCallContainer = findViewById(R.id.callEndContainer);
    }

    private void bindActions() {
        findViewById(R.id.callAnswerButton).setOnClickListener(
                view -> ToPhoneInCallService.answerCurrentCall()
        );
        findViewById(R.id.callDeclineButton).setOnClickListener(
                view -> ToPhoneInCallService.declineCurrentCall()
        );
        findViewById(R.id.callEndButton).setOnClickListener(
                view -> ToPhoneInCallService.disconnectCurrentCall()
        );
        muteButton.setOnClickListener(view -> {
            ToPhoneInCallService.toggleMute();
            renderAudioControls();
        });
        speakerButton.setOnClickListener(view -> {
            ToPhoneInCallService.toggleSpeaker();
            renderAudioControls();
        });
    }

    private void readCallState(Intent intent) {
        if (intent == null) {
            return;
        }
        callState = intent.getIntExtra(
                ToPhoneInCallService.EXTRA_STATE,
                ToPhoneInCallService.getCurrentState()
        );
        phoneNumber = intent.getStringExtra(ToPhoneInCallService.EXTRA_NUMBER);
        if (phoneNumber == null) {
            phoneNumber = "";
        }
        incoming = intent.getBooleanExtra(
                ToPhoneInCallService.EXTRA_INCOMING,
                ToPhoneInCallService.isCurrentIncoming()
        );
        connectedAt = intent.getLongExtra(
                ToPhoneInCallService.EXTRA_CONNECTED_AT,
                ToPhoneInCallService.getCurrentConnectedAt()
        );
    }

    private void renderCallState() {
        handler.removeCallbacks(timerRunnable);
        renderIdentity();
        renderAudioControls();

        boolean isRinging = callState == Call.STATE_RINGING;
        incomingControls.setVisibility(isRinging ? View.VISIBLE : View.GONE);
        activeControls.setVisibility(isRinging ? View.GONE : View.VISIBLE);
        endCallContainer.setVisibility(isRinging ? View.GONE : View.VISIBLE);

        switch (callState) {
            case Call.STATE_RINGING:
                statusText.setText(R.string.call_ui_incoming);
                break;
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
            case Call.STATE_SELECT_PHONE_ACCOUNT:
                statusText.setText(R.string.call_ui_calling);
                break;
            case Call.STATE_ACTIVE:
                if (connectedAt <= 0L) {
                    connectedAt = System.currentTimeMillis();
                }
                renderDuration();
                handler.postDelayed(timerRunnable, TIMER_INTERVAL_MS);
                break;
            case Call.STATE_HOLDING:
                statusText.setText(R.string.call_ui_on_hold);
                break;
            case Call.STATE_DISCONNECTED:
                statusText.setText(R.string.call_ui_ended);
                activeControls.setVisibility(View.GONE);
                incomingControls.setVisibility(View.GONE);
                endCallContainer.setVisibility(View.GONE);
                handler.postDelayed(this::finishAndRemoveTask, FINISH_DELAY_MS);
                break;
            default:
                statusText.setText(incoming
                        ? R.string.call_ui_incoming
                        : R.string.call_ui_connecting);
                break;
        }
    }

    private void renderIdentity() {
        String displayNumber = phoneNumber == null || phoneNumber.trim().isEmpty()
                ? getString(R.string.call_ui_unknown_number)
                : phoneNumber.trim();
        String contactName = resolveContactName(displayNumber);
        if (contactName == null || contactName.trim().isEmpty()) {
            titleText.setText(displayNumber);
            numberText.setVisibility(View.GONE);
            avatarText.setText("#");
        } else {
            titleText.setText(contactName);
            numberText.setText(displayNumber);
            numberText.setVisibility(View.VISIBLE);
            avatarText.setText(firstCharacter(contactName));
        }
        renderPhoneLocation(displayNumber);
    }

    private void renderPhoneLocation(String displayNumber) {
        if (displayNumber == null
                || displayNumber.trim().isEmpty()
                || displayNumber.equals(getString(R.string.call_ui_unknown_number))) {
            requestedLocationNumber = "";
            locationText.setVisibility(View.GONE);
            return;
        }
        String normalized = displayNumber.trim();
        if (normalized.equals(requestedLocationNumber)) {
            return;
        }
        requestedLocationNumber = normalized;
        locationText.setText(R.string.call_ui_location_loading);
        locationText.setVisibility(View.VISIBLE);
        PhoneLocationHelper.getPhoneLocation(
                normalized,
                LOCATION_REQUEST_TAG,
                new PhoneLocationHelper.LocationCallback() {
                    @Override
                    public void onResult(String location) {
                        if (!normalized.equals(requestedLocationNumber) || isFinishing()) {
                            return;
                        }
                        locationText.setText(location);
                        locationText.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!normalized.equals(requestedLocationNumber) || isFinishing()) {
                            return;
                        }
                        locationText.setText(R.string.call_ui_location_unknown);
                        locationText.setVisibility(View.VISIBLE);
                    }
                }
        );
    }

    private void renderDuration() {
        long base = connectedAt > 0L ? connectedAt : System.currentTimeMillis();
        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - base) / 1000L);
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        statusText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void renderAudioControls() {
        renderToggleButton(muteButton, ToPhoneInCallService.isMuted());
        renderToggleButton(speakerButton, ToPhoneInCallService.isSpeakerOn());
    }

    private void renderToggleButton(ImageButton button, boolean active) {
        button.setBackgroundResource(active
                ? R.drawable.bg_call_button_active
                : R.drawable.bg_call_button_neutral);
        button.setColorFilter(active
                ? ContextCompat.getColor(this, R.color.call_control_active_icon)
                : Color.WHITE);
    }

    @Nullable
    private String resolveContactName(String number) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );
        String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                return index >= 0 ? cursor.getString(index) : null;
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return null;
    }

    private String firstCharacter(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end).toUpperCase(Locale.getDefault());
    }
}

package in.edu.ssn.testssnapp;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import in.edu.ssn.testssnapp.models.BusObject;
import in.edu.ssn.testssnapp.services.TransmitLocationService;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.FCMHelper;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.YesNoDialogBuilder;
import spencerstudios.com.bungeelib.Bungee;

public class MapActivity extends BaseActivity implements GoogleMap.OnMarkerClickListener, OnMapReadyCallback {
    volatile String routeNo, userId, volunteerBusNo;
    DatabaseReference busLocRef;
    TextView tvStatusBarHeader, tvNoVolunteer, tvVolunteerDetails;
    ImageButton cmdStopVolunteering, cmdStartVolunteering;
    volatile boolean showTimeElapsedTV = true, isBusVolunteer = false, updateListenerToMain = false, isfirstTimeGPSTurnedOn = false;
    volatile Boolean isSharingLoc = false;
    LatLng SSNCEPoint = new LatLng(12.7525, 80.196111);
    GoogleMap googleMap;
    volatile DataSnapshot initialSnapshot;
    MapView busTrackingMap;
    ConnectivityManager.NetworkCallback userNetworkCallback, volunteerNetworkCallback;
    NetworkRequest networkRequest;
    ValueEventListener concurrentVolunteerListener, routeExistslistener, locationChangedListener, sharerChangedListener, onlineStatusChangedListener, speedChangedListener;
    ProgressDialog pd;
    boolean isMapLoaded = false;
    volatile BusObject currentBusObject;
    SecureRandom random;
    volatile Intent i;
    AppCompatTextView tvLastUpdatedTimeDesc, tvLastUpdatedTimeTensDigit, tvLastUpdatedTimeUnitsDigit, tvLastUpdatedTimeUnit;
    ImageView backIV, isBusOnlineIV;
    LinearLayout novolunteerLL;
    ConnectivityManager connectivityManager;
    volatile Timer timer;
    volatile long startTime = System.currentTimeMillis();
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (CommonUtils.alerter(this)) {
            if (!CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                startActivity(new Intent(this, NoNetworkActivity.class).putExtra("key", "null"));
                finish();
                Bungee.fade(this);
                return;
            }
        }
        checkRequirementsAndPermissions();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getWindow().setSustainedPerformanceMode(true);
        }
        if (darkModeEnabled) {
            setContentView(R.layout.activity_map_dark);
            getWindow().setStatusBarColor(getResources().getColor(R.color.darkColor1));
        } else {
            setContentView(R.layout.activity_map);
            getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccent));
        }
        //to try to circumvent google maps API 500 queries-per-second limit.
        //max delay: 6s
        pd = darkModeEnabled ? new ProgressDialog(this, R.style.DarkThemeDialog) : new ProgressDialog(this);
        random = new SecureRandom();
        int waittime = random.nextInt(6) * 1000;
        pd.setMessage("Map load wait time: ~" + waittime / 1000 + (waittime == 1000 ? " second." : " seconds."));
        pd.setCancelable(false);
        pd.show();
        clearOldNotifications();
        Boolean b = getIntent().getBooleanExtra("improper_shutdown", false);
        if (!b && !CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
            new CountDownTimer(waittime, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    initMapViewAndUI(savedInstanceState);
                }
            }.start();
        else
            initMapViewAndUI(savedInstanceState);
    }

    private void clearOldNotifications() {
        FCMHelper.clearNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, getApplicationContext());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            FCMHelper.clearNotification(3, Constants.BUS_TRACKING_NOTIFS_CHANNELID, getApplicationContext());
            FCMHelper.clearNotification(4, Constants.BUS_TRACKING_NOTIFS_CHANNELID, getApplicationContext());
        }
    }

    private void switchToVolunteerNetworkCallback() {
        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        try {
            connectivityManager.unregisterNetworkCallback(userNetworkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            connectivityManager.registerNetworkCallback(networkRequest, volunteerNetworkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchToUserNetworkCallback() {
        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        try {
            connectivityManager.unregisterNetworkCallback(volunteerNetworkCallback);

        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            connectivityManager.registerNetworkCallback(networkRequest, userNetworkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unregisterNetworkCallbacks() {
        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        try {
            connectivityManager.unregisterNetworkCallback(volunteerNetworkCallback);

        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            connectivityManager.unregisterNetworkCallback(userNetworkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initNetworkCallbacks() {
        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (networkRequest == null)
            networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
        if (userNetworkCallback == null)
            userNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) { //user
                    super.onAvailable(network);
                    checkRequirementsAndPermissions();
                    if (currentBusObject == null || currentBusObject.isSharerOnline()) {
                        if (cmdStartVolunteering != null && !cmdStartVolunteering.isEnabled()) {
                            runOnUiThread(() -> {
                                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                cmdStartVolunteering.setEnabled(true);
                            });
                        }
                        return;
                    }
                    if (isVolunteerOfThisBus() && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                        runOnUiThread(() -> {
                        /*if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            tvVolunteerDetails.setText("Reconnecting...");*/
                            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                                tvNoVolunteer.setText(R.string.no_location_value);
                                disableControls();
                            }
                            if (cmdStartVolunteering != null && !cmdStartVolunteering.isEnabled()) {
                                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                cmdStartVolunteering.setEnabled(true);
                            }
                        });
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    if (isVolunteerOfThisBus() && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                        runOnUiThread(() -> {
                            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED) && cmdStartVolunteering.isEnabled()) {
                                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                                cmdStartVolunteering.setEnabled(false);
                            }
                        });
                    if (currentBusObject != null)
                        runOnUiThread(() -> currentBusObject.setUserOnline(false));
                    deactivateInfoChangedListeners();
                }
            };
        if (volunteerNetworkCallback == null)
            volunteerNetworkCallback = new ConnectivityManager.NetworkCallback() {
                volatile boolean isServiceSuspended = false;

                void attemptToReconnect() {
                    clearOldNotifications();
                    if (busLocRef == null) {
                        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(SharedPref.getString(getApplicationContext(), "routeNo"));
                    }
                    busLocRef.child("sharingLoc").onDisconnect().cancel();
                    busLocRef.child("currentSharerID").onDisconnect().cancel();
                    resolveVolunteerStatus();
                    isServiceSuspended = false;
                }

                @Override

                public void onAvailable(@NonNull Network network) { //volunteer
                    super.onAvailable(network);
                    if (!(currentBusObject == null && CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) && !isServiceSuspended))
                        if (currentBusObject == null || currentBusObject.isSharerOnline()) {
                            return;
                        }
                    if (currentBusObject != null && !isServiceSuspended) return;
                    isServiceSuspended = false;
                    checkRequirementsAndPermissions();
                    if (!cmdStartVolunteering.isEnabled()) {
                        runOnUiThread(() -> {
                            cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                            cmdStartVolunteering.setEnabled(false);
                        });
                    }
                    int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                    if (disruptionCount < Constants.MAX_LOCSHARE_RETRIES_ALLOWED) {
                        if (currentBusObject == null || !currentBusObject.isSharerOnline()) {
                            sendServiceNotifMessage("Reconnecting to database", "Checking if any volunteer has taken over, please wait...", true);
                            if (Looper.getMainLooper() == Looper.myLooper()) {
                                if (novolunteerLL != null && novolunteerLL.getVisibility() == View.VISIBLE && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                                    tvNoVolunteer.setText(R.string.no_location_value);
                                else {
                                    tvVolunteerDetails.setText(R.string.fetching_location);
                                    cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                    cmdStartVolunteering.setEnabled(true);
                                    disableControls();
                                }
                            } else runOnUiThread(() -> {
                                if (novolunteerLL.getVisibility() == View.VISIBLE && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                                    tvNoVolunteer.setText(R.string.no_location_value);
                                else {
                                    tvVolunteerDetails.setText("Fetching data..." + "\nThis can take a while if you are not on a moving bus.");
                                    cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                    cmdStartVolunteering.setEnabled(true);
                                    disableControls();
                                }
                            });
                            attemptToReconnect();
                        }
                    } else {
                        unregisterNetworkCallbacks();
                        stopLocationTransmission(false);
                        /*Toast.makeText(getApplicationContext(),
                                "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", Toast.LENGTH_LONG).show();
                        showNotification(3, Constants.BUS_TRACKING_NOTIFS_CHANNELID, "Auto-stopped volunteering",
                                "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", MapActivity.this, new Intent());*/
                        finish();

                    }
                }

                @Override
                public void onLost(@NonNull Network network) { //volunteer
                    super.onLost(network);
                    if (isServiceSuspended) return;
                    deactivateInfoChangedListeners();
                    if (currentBusObject != null)
                        if (Looper.myLooper() == Looper.getMainLooper())
                            currentBusObject.setUserOnline(false);
                        else runOnUiThread(() -> currentBusObject.setUserOnline(false));
                    else return;
                    int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                    SharedPref.putInt(getApplicationContext(), "disruption_count", disruptionCount + 1);
                    if (disruptionCount >= Constants.MAX_LOCSHARE_RETRIES_ALLOWED) {
                        unregisterNetworkCallbacks();
                        stopLocationTransmission(false);
                        Toast.makeText(getApplicationContext(),
                                "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", Toast.LENGTH_LONG).show();
                        showNotification(3, Constants.BUS_TRACKING_NOTIFS_CHANNELID, "Auto-stopped volunteering",
                                "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", MapActivity.this, new Intent());
                        finish();
                        return;
                    }
                    isServiceSuspended = true;
                    suspendLocationTransmission();
                }
            };
        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) || isSharingLoc)
            return;
        connectivityManager.registerNetworkCallback(networkRequest, userNetworkCallback);
    }

    private void checkRequirementsAndPermissions() {
        //String currentTime = new SimpleDateFormat("EEE, MMM dd yyyy, hh:mm:ss").format(System.currentTimeMillis() + SharedPref.getLong(getApplicationContext(), "time_offset")).substring(18);
        DatabaseReference timeRef = FirebaseDatabase.getInstance().getReference("Bus Locations");
        timeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean masterEnable = snapshot.child("masterEnable").getValue(Boolean.class);
                if (masterEnable == null || !masterEnable) {// || startTime == null || endTime == null) {
                    Toast.makeText(getApplicationContext(), "Cannot start the bus tracking feature! Master switch is off!", Toast.LENGTH_LONG).show();
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                        Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
                        i.putExtra("routeNo", getIntent().getStringExtra("routeNo"));
                        i.putExtra("deleteDBValue", true);
                        i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
                        startService(i);
                    }
                    Bungee.slideRight(MapActivity.this);
                    finish();
                    startActivity(new Intent(MapActivity.this, BusRoutesActivity.class));
                } /*else if (Objects.equals(s.substring(0, 3), "Sun")) { //Example s: "Sat, Jul 04 2020, 11:14:47"
                                    Toast.makeText(context, "Cannot start bus tracking feature on Sundays!", Toast.LENGTH_LONG).show();
                                    for (BusRouteViewHolder i : holders)
                                        i.busRouteCV.setEnabled(true);
                                    return;
                                }
                                else if (currentTime < startTime) {
                                    Toast.makeText(context, "Cannot use the bus tracking feature until " + SimpleDateFormat.getTimeInstance().format(startTime) + "!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                else if (currentTime > endTime) {
                                    Toast.makeText(context, "Cannot use the bus tracking feature beyond " + SimpleDateFormat.getTimeInstance().format(endTime) + "!", Toast.LENGTH_LONG).show();
                                    return;
                                } */

                timeRef.removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        String currentTime = "07:00:00";
        if (currentTime.compareTo("08:00:00") > 0 || currentTime.compareTo("06:00:00") < 0) {
            Toast.makeText(getApplicationContext(), "The time now is: " + currentTime + ". You have exceeded the daily time limit allowed to use this feature!", Toast.LENGTH_LONG).show();
            stopLocationTransmission();
            unregisterNetworkCallbacks();
            finish();
        }
    }

    private void resolveVolunteerStatus() {
        final String[] currentSharerID = {"null"};
        if (routeNo == null || routeNo.isEmpty())
            routeNo = SharedPref.getString(getApplicationContext(), "routeNo");
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        if (userId == null || userId.isEmpty())
            userId = SharedPref.getString(getApplicationContext(), "email");

        busLocRef.keepSynced(true);

        //write some test data and check for the state of other values.
        busLocRef.child("testdata").setValue(true,
                (error, ref) -> busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (error == null) {
                            currentSharerID[0] = snapshot.child("currentSharerID").getValue(String.class);
                            isSharingLoc = snapshot.child("sharingLoc").getValue(Boolean.class);
                            busLocRef.child("testdata").removeValue();
                            if ((currentSharerID[0] == null || userId.equals(currentSharerID[0]) || currentSharerID[0].equals("null"))
                                    || isSharingLoc == null || !isSharingLoc)
                                finalizeDecisionToReconnect();
                            else {
                                showNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, "The background service has been stopped", "Another volunteer has taken over since your last disconnection! Thank you for your services!", MapActivity.this, new Intent());
                                stopLocationTransmission(false);
                                switchToUserNetworkCallback();
                            }
                        } else {
                            Log.e("RECONNECTION ERROR:", "MESSAGE: " + error.getMessage() + "\nDETAILS: " + error.getDetails());
                            stopLocationTransmission(false);
                            showNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, "The background service has been stopped", "There was an unexpected error reconnecting to the database! The service has been stopped now. Please come back into the app to see if anyone else has started volunteering!", MapActivity.this, new Intent(MapActivity.this, MapActivity.class).putExtra("routeNo", routeNo));
                            finish();
                            unregisterNetworkCallbacks();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                }));
    }

    private void finalizeDecisionToReconnect() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    FCMHelper.clearNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, MapActivity.this);
                    startTime = System.currentTimeMillis();
                    runOnUiThread(this::disableControls);
                    restartLocationTransmission();
                } else {
                    Toast.makeText(getApplicationContext(), "Your GPS is not enabled! Cannot auto-restart location sharing!", Toast.LENGTH_SHORT).show();
                    isSharingLoc = false;
                    attemptToEnableGPS();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Location permissions have been disabled! Cannot auto-restart location sharing! The service will now stop!", Toast.LENGTH_SHORT).show();
                unregisterNetworkCallbacks();
                finish();
            }
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            FCMHelper.clearNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, MapActivity.this);
            runOnUiThread(() -> {
                startTime = System.currentTimeMillis();
                updateTimeElapsedTextViews();
                disableControls();
            });

        } else {
            Toast.makeText(getApplicationContext(), "Your GPS is not enabled! Cannot auto-restart location sharing!", Toast.LENGTH_SHORT).show();
            if (cmdStartVolunteering == null) unregisterNetworkCallbacks();
            else switchToUserNetworkCallback();
            attemptToEnableGPS();
        }
    }

    private void initUI() {

        cmdStopVolunteering = findViewById(R.id.stop);
        cmdStartVolunteering = findViewById(R.id.start);
        tvStatusBarHeader = findViewById(R.id.tv_trackbus);
        isBusOnlineIV = findViewById(R.id.iv_busOnlineStatus);
        tvVolunteerDetails = findViewById(R.id.tv_volunteerid);

        tvLastUpdatedTimeDesc = findViewById(R.id.tv_lastupdated);
        tvLastUpdatedTimeTensDigit = findViewById(R.id.tv_lastupdatedtimeTensDigit);
        tvLastUpdatedTimeUnitsDigit = findViewById(R.id.tv_lastupdatedtimeUnitsDigit);
        tvLastUpdatedTimeUnit = findViewById(R.id.tv_lastupdatedtimeunit);

        tvLastUpdatedTimeDesc.setText("");
        tvLastUpdatedTimeTensDigit.setText("");
        tvLastUpdatedTimeUnitsDigit.setText("");
        tvLastUpdatedTimeUnit.setText("");

        backIV = findViewById(R.id.backIV);
        novolunteerLL = findViewById(R.id.layout_empty);
        tvNoVolunteer = findViewById(R.id.tv_novolunteer);
        routeNo = getIntent().getStringExtra("routeNo");

        AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1000);
        tvStatusBarHeader.setText(String.format("Track Bus No. %s", routeNo));
        tvStatusBarHeader.startAnimation(fadeIn);
        backIV.startAnimation(fadeIn);
        userId = SharedPref.getString(getApplicationContext(), "email");
        volunteerBusNo = SharedPref.getString(getApplicationContext(), "volunteer_busno", userId);
        isBusVolunteer = isVolunteerOfThisBus();

        backIV.setOnClickListener(v -> {
            onBackPressed();
        });
        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        busLocRef.keepSynced(true);
        routeExistslistener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.child("timeLimitViolation").getValue(Boolean.class) != null) {
                    Boolean timeLimitViolation = dataSnapshot.child("timeLimitViolation").getValue(Boolean.class);
                    if (timeLimitViolation) {
                        if (isBusVolunteer)
                            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
                        else tvNoVolunteer.setText(R.string.no_volunteer_available);
                        return;
                    }
                    hideMapView();
                    Toast.makeText(getApplicationContext(), "You have exceeded the daily time limit allowed to use this feature!", Toast.LENGTH_LONG).show();
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                        stopLocationTransmission();

                    unregisterNetworkCallbacks();
                    deactivateInfoChangedListeners();
                    busLocRef.removeEventListener(this);
                    finish();
                } else if (dataSnapshot.getChildrenCount() < 4) {
                    if (isVolunteerOfThisBus()) {
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED) && !cmdStartVolunteering.isEnabled()) {
                            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                                tvNoVolunteer.setText(R.string.no_location_value);
                                disableControls();
                            }
                            runOnUiThread(() -> {
                                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                cmdStartVolunteering.setEnabled(true);
                            });
                        }
                    }
                } else {
                    startTime = System.currentTimeMillis();
                    showTimeElapsedTV = true;
                    String latLongString = dataSnapshot.child("latLong").getValue(String.class), sharerId = dataSnapshot.child("currentSharerID").getValue(String.class);
                    int speed = dataSnapshot.child("speed").getValue() != null ? (int) (dataSnapshot.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : dataSnapshot.child("speed").getValue(int.class) * 3.6) : 0;
                    if (latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.equals("null"))
                        return;
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) || !sharerId.equals(SharedPref.getString(getApplicationContext(), "email")))
                        updateTimeElapsedTextViews();
                    try {
                        int sep = latLongString.indexOf(',');
                        LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));
                        if (currentBusObject == null)
                            currentBusObject = createBusObject(sharerId, currentlatLongs, speed);

                        else {
                            currentBusObject.setLocation(currentlatLongs, handler, googleMap);
                        }
                        currentBusObject.setUserOnline(true);
                        busTrackingMap.onResume();
                        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                            disableControls();
                        else if (!sharerId.equals(userId)) {
                            cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                            cmdStartVolunteering.setEnabled(false);
                        }
                        updateListenerToMain = true;
                        showMapView();
                        activateInfoChangedListeners();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }/* else if (!CommonUtils.alerter(getApplicationContext())) {
                    if (isVolunteerOfThisBus()) {
                        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                            tvNoVolunteer.setText(R.string.fetching_location);
                            disableControls();
                        } else
                            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
                    } else
                        tvNoVolunteer.setText(R.string.no_volunteer_available);
                    hideMapView();

                } else {
                    if (isBusVolunteer)
                        tvNoVolunteer.setText(R.string.start_volunteering_offlinemsg);
                    else
                        tvNoVolunteer.setText(R.string.user_offline);
                }*/
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                hideMapView();
            }
        };
        busLocRef.addValueEventListener(routeExistslistener);

        //*****************************Volunteer Section ****************************//
        if (!isBusVolunteer) {
            tvNoVolunteer.setText(R.string.no_volunteer_available);
            cmdStartVolunteering.setVisibility(View.GONE);
            cmdStopVolunteering.setVisibility(View.GONE);
            Toast.makeText(getApplicationContext(), "Access granted!", Toast.LENGTH_SHORT).show();
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) isBusOnlineIV.getLayoutParams();
            marginParams.setMargins(0, 0, 150, 0);
        } else {
            cmdStartVolunteering.startAnimation(fadeIn);

            /*if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                disableControls();
                switchToVolunteerNetworkCallback();
                if (CommonUtils.alerter(this)) {
                    tvNoVolunteer.setText(R.string.start_volunteering_offlinemsg);
                } else
                    tvNoVolunteer.setText(R.string.no_location_value);
            } else tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
*/
            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                disableControls();
                switchToVolunteerNetworkCallback();
                Toast.makeText(getApplicationContext(), "Access granted!", Toast.LENGTH_SHORT).show();
                if (CommonUtils.alerter(this))
                    tvNoVolunteer.setText(R.string.volunteer_offline);
                else {
                    tvNoVolunteer.setText(R.string.no_location_value);
                }
            } else if (SharedPref.getBoolean(getApplicationContext(), "stopbutton")) {
                isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                Toast.makeText(getApplicationContext(), R.string.improper_shutdown, Toast.LENGTH_LONG).show();
                unregisterNetworkCallbacks();
                SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
                finish();
                startActivity(new Intent(getApplicationContext(), MapActivity.class).putExtra("routeNo", routeNo).putExtra("improper_shutdown", true));
                return;
            } else
                enableControls();
            //pd = darkModeEnabled ? new ProgressDialog(this, R.style.DarkThemeDialog) : new ProgressDialog(this);

            cmdStartVolunteering.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        attemptToGetLocationPermissions();
                        Toast.makeText(getApplicationContext(), "Plase press the button again after granting permissions!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                if (disruptionCount >= Constants.MAX_LOCSHARE_RETRIES_ALLOWED) {
                    showNotification(3, Constants.BUS_TRACKING_NOTIFS_CHANNELID, "Auto-stopped volunteering",
                            "You are restarting sharing too frequently, and have been disabled from running the service any further for this session. Please restart the app to start volunteering!", MapActivity.this, new Intent());
                    Toast.makeText(getApplicationContext(),
                            "You are restarting sharing too frequently, and have been disabled from running the service any further for this session. Please restart the app to start volunteering!", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                cmdStartVolunteering.setEnabled(false);
                if (CommonUtils.alerter(MapActivity.this)) {
                    Toast.makeText(getApplicationContext(), "You're offline! Please connect to the Internet to start sharing your location to the servers!", Toast.LENGTH_LONG).show();
                    cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                    cmdStartVolunteering.setEnabled(false);
                    return;
                }
                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                cmdStartVolunteering.setEnabled(true);
                pd.setMessage("Attempting to start background service...");
                pd.setCancelable(false);
                pd.show();
                checkRequirementsAndPermissions();
                busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (!dataSnapshot.exists()) {
                            switchToVolunteerNetworkCallback();
                            attemptToEnableGPS();
                            pd.dismiss();
                            return;
                        }
                        String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                        Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                        if (s != null && !s.equals("null") && !s.equals(userId) && b != null && b) {
                            Toast.makeText(getApplicationContext(), R.string.cannot_start_loc_sharing, Toast.LENGTH_LONG).show();
                            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                                stopLocationTransmission(false);
                                busLocRef.child("currentSharerID").setValue(s);
                            }
                        } else {
                            busLocRef.removeEventListener(this);
                            pd.dismiss();
                            cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                            cmdStartVolunteering.setEnabled(true);
                            switchToVolunteerNetworkCallback();
                            attemptToEnableGPS();
                            return;
                        }
                        pd.dismiss();
                        cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                        cmdStartVolunteering.setEnabled(true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            });
            cmdStopVolunteering.setOnClickListener(v -> {
                pd.setMessage("Stopping background service...");
                pd.setCancelable(false);
                cmdStopVolunteering.setEnabled(false);
                cmdStopVolunteering.setImageResource(R.drawable.ic_location_off_disabled);
                YesNoDialogBuilder.createDialog(
                        MapActivity.this,
                        darkModeEnabled,
                        "Confirm stopping location sharing?",
                        "Your location will stop being shared to the servers.",
                        false,
                        (dialog, which) -> {
                            pd.show();
                            stopLocationTransmission();
                            switchToUserNetworkCallback();
                            showSwitchOffGPSDialog();
                            pd.dismiss();
                        }, (dialog, which) -> {
                            dialog.dismiss();
                            cmdStopVolunteering.setImageResource(R.drawable.ic_location_off);
                            cmdStopVolunteering.setEnabled(true);
                            pd.dismiss();
                        }).show();
            });
        }
        tvVolunteerDetails.setVisibility(View.INVISIBLE);
    }

    private void showSwitchOffGPSDialog() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            if (!isFinishing())
                YesNoDialogBuilder.createDialog(this,
                        darkModeEnabled,
                        "Thanks for volunteering!",
                        " Would you like to turn off your GPS now?\nPress 'No' if you'd like to turn it off yourself later.",
                        true,
                        (dialog1, which1) -> {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            dialog1.dismiss();
                        }, (dialog1, which1) -> {
                            dialog1.dismiss();
                        }).show();
    }

    private void startLocationTransmission() {
        checkRequirementsAndPermissions();
        clearOldNotifications();
        if (concurrentVolunteerListener == null)
            concurrentVolunteerListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                    Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) && s != null && !s.equals("null") && !s.equals(SharedPref.getString(getApplicationContext(), "email")) && b != null && b) {
                        stopLocationTransmission(false);
                        if (currentBusObject != null) currentBusObject.setUserOnline(false);
                        enableControls();
                        cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                        cmdStartVolunteering.setEnabled(false);
                        switchToUserNetworkCallback();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            };
        if (busTrackingMap != null) {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                if (busTrackingMap.getVisibility() != View.VISIBLE)
                    tvNoVolunteer.setText(R.string.fetching_location);
                else
                    tvVolunteerDetails.setText(R.string.fetching_location);

                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                cmdStartVolunteering.setEnabled(true);
                disableControls();
            } else runOnUiThread(() -> {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED) && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                    if (busTrackingMap.getVisibility() != View.VISIBLE)
                        tvNoVolunteer.setText(R.string.fetching_location);
                    else
                        tvVolunteerDetails.setText(R.string.fetching_location);
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                    cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                    cmdStartVolunteering.setEnabled(true);
                disableControls();
            });
        }
        busLocRef.addValueEventListener(concurrentVolunteerListener);
        //remove old values, if any.
        busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String s = snapshot.child("latLong").getValue(String.class);
                if (s != null)
                    busLocRef.removeValue((error, ref) -> {
                        i = new Intent(getApplicationContext(), TransmitLocationService.class);
                        i.setAction(TransmitLocationService.ACTION_START_FOREGROUND_SERVICE);
                        i.putExtra("routeNo", routeNo);
                        i.putExtra("suspendFlag", false);
                        startService(i);
                        isSharingLoc = true;
                    });
                else {
                    i = new Intent(getApplicationContext(), TransmitLocationService.class);
                    i.setAction(TransmitLocationService.ACTION_START_FOREGROUND_SERVICE);
                    i.putExtra("routeNo", routeNo);
                    i.putExtra("suspendFlag", false);
                    startService(i);
                    isSharingLoc = true;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void restartLocationTransmission() {
        deactivateInfoChangedListeners();
        startLocationTransmission();
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(SharedPref.getString(getApplicationContext(), "routeNo"));
        busLocRef.keepSynced(true);
        if (Looper.myLooper() == Looper.getMainLooper())
            tvNoVolunteer.setText(R.string.no_location_value);
        else runOnUiThread(() -> tvNoVolunteer.setText(R.string.no_location_value));
        if (routeExistslistener != null)
            busLocRef.addValueEventListener(routeExistslistener);
    }

    private void suspendLocationTransmission() {
        i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_CHANGE_NOTIFICATION_MESSAGE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("suspendFlag", true);
        i.putExtra("messageTitle", "Location sharing suspended");
        i.putExtra("messageContent", "We'll auto-restart location sharing once you're back online in some time.");
        startService(i);
        if (currentBusObject != null && currentBusObject.isSharerOnline())
            currentBusObject.setUserOnline(false);
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", true);
        isSharingLoc = false;
    }

    private void stopLocationTransmission(boolean deleteDBValue) {
        FCMHelper.clearNotification(2, Constants.BUS_TRACKING_NOTIFS_CHANNELID, MapActivity.this);
        if (routeNo == null) routeNo = SharedPref.getString(getApplicationContext(), "routeNo");
        SharedPref.putString(getApplicationContext(), "routeNo", "");
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
        isSharingLoc = false;
        switchToUserNetworkCallback();
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);

        /*if (concurrentVolunteerListener != null)
            busLocRef.removeEventListener(concurrentVolunteerListener);*/
        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
            i = new Intent(getApplicationContext(), TransmitLocationService.class);
            i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
            i.putExtra("routeNo", routeNo);
            i.putExtra("suspendFlag", true);
            i.putExtra("deleteDBValue", deleteDBValue);
            startService(i);
        }
        if (Looper.getMainLooper() == Looper.myLooper()) {
            if (tvNoVolunteer.getText().toString().startsWith("Fetching"))
                tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
            if (pd != null) pd.dismiss();
            cmdStopVolunteering.setImageResource(R.drawable.ic_location_off);
            cmdStopVolunteering.setEnabled(true);
            if (currentBusObject != null)
                currentBusObject.setCurrentVolunteerId(SharedPref.getString(getApplicationContext(), "email"));
            enableControls();

        } else runOnUiThread(() -> {
            if (tvNoVolunteer.getText().toString().startsWith("Fetching"))
                tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
            if (pd != null) pd.dismiss();
            cmdStopVolunteering.setImageResource(R.drawable.ic_location_off);
            cmdStopVolunteering.setEnabled(true);
            if (currentBusObject != null)
                currentBusObject.setCurrentVolunteerId(SharedPref.getString(getApplicationContext(), "email"));
            enableControls();
        });
    }

    private void sendServiceNotifMessage(String messageTitle, String messageContent, boolean suspendFlag) {
        Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_CHANGE_NOTIFICATION_MESSAGE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("messageTitle", messageTitle);
        i.putExtra("suspendFlag", suspendFlag);
        i.putExtra("messageContent", messageContent);
        startService(i);
    }

    private void stopLocationTransmission() {
        stopLocationTransmission(true);
    }

    //*****************************Check whether location permission is granted****************************//

    private void attemptToGetLocationPermissions() {
        ArrayList<String> permissionsRequired = new ArrayList<>();
        Collections.addAll(permissionsRequired, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            //  permissionsRequired.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            Iterator<String> permissionsIterator = permissionsRequired.iterator();
            while (permissionsIterator.hasNext()) {
                if (ContextCompat.checkSelfPermission(this, permissionsIterator.next()) == PackageManager.PERMISSION_GRANTED)
                    permissionsIterator.remove();
            }
            if (permissionsRequired.size() == 0) attemptToEnableGPS();
            else {
                ActivityCompat.requestPermissions(this, permissionsRequired.toArray(new String[0]), 2);
                //Toast.makeText(getApplicationContext(), "You have not granted " + permissionsRequired.size() + (permissionsRequired.size() == 1 ? " permission" : "permissions" + "! Cannot start location sharing!"), Toast.LENGTH_SHORT).show();
            }
        } else attemptToEnableGPS();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) return;
        if (requestCode == 2) {
            for (int i = 0; i < permissions.length; i++)
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i]))
                        showLocationPermissionRationale(permissions[i]);
                    else {
                        Toast.makeText(getApplicationContext(), "The required permissions have been denied! Cannot start location sharing!", Toast.LENGTH_LONG).show();
                        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))

                            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                                switchToUserNetworkCallback();
                            else {
                                unregisterNetworkCallbacks();
                                finish();
                            }
                        if (pd != null) pd.dismiss();
                    }
                    return;
                }
            attemptToEnableGPS();
        }
    }

    private void showLocationPermissionRationale(String permission) {
        if (pd != null) pd.dismiss();
        YesNoDialogBuilder.createDialog(this, darkModeEnabled,
                "Volunteering needs Location permissions",
                "We need your permission to access your location as you are signed up as a volunteer, " +
                        "and your GPS location will be used to determine the bus' location. Denying this permission will cause you " +
                        "to not be able to use this feature.\n\nDo you want to grant the location permissions?", false,
                (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ActivityCompat.requestPermissions(this, new String[]{permission}, 2);
                    }
                },
                (dialog, which) -> {
                    Toast.makeText(getApplicationContext(), "The required permissions have been denied! Cannot start location sharing!", Toast.LENGTH_LONG).show();
                    unregisterNetworkCallbacks();
                    Bungee.fade(MapActivity.this);
                    finish();
                }).show();
    }


    //************************Check whether gps is enabled or not *********************//

    private void attemptToEnableGPS() {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(0);
        locationRequest.setFastestInterval(0);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        //Log.i(TAG "All location settings are satisfied.");
                        if (!isSharingLoc) startLocationTransmission();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        //Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings");
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result
                            //in onActivityResult();
                            isfirstTimeGPSTurnedOn = true;
                            status.startResolutionForResult(MapActivity.this, 1);
                        } catch (IntentSender.SendIntentException e) {
                            if (pd != null) pd.dismiss();
                            Toast.makeText(getApplicationContext(), "Unable to execute your request currently! Please try again in some time!", Toast.LENGTH_LONG).show();
                            //Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        //Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        if (pd != null) pd.dismiss();
                        Toast.makeText(getApplicationContext(), "Unable to get location. Cannot proceed further!", Toast.LENGTH_LONG).show();
                        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                            stopLocationTransmission();
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1)
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Unable to get your location! Cannot start location sharing!", Toast.LENGTH_SHORT).show();
                if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                    if (cmdStopVolunteering == null) unregisterNetworkCallbacks();
                    else switchToUserNetworkCallback();
                    stopLocationTransmission();
                }
            } else if (!isSharingLoc) {
                startLocationTransmission();
            }

    }

    //*****enable start stop button controls and check if user is volunteer of the bus****//

    public void enableControls() {
        if (cmdStartVolunteering == null || cmdStopVolunteering == null || cmdStopVolunteering.getVisibility() == View.GONE)
            return;
        AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
        fadeOut.setDuration(1000);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        cmdStopVolunteering.startAnimation(fadeOut);
        cmdStopVolunteering.setVisibility(View.GONE);
        cmdStartVolunteering.startAnimation(fadeIn);
        cmdStartVolunteering.setVisibility(View.VISIBLE);
    }

    public void disableControls() {
        if (cmdStartVolunteering == null || cmdStopVolunteering == null || cmdStartVolunteering.getVisibility() == View.GONE)
            return;
        AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
        fadeOut.setDuration(1000);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        cmdStartVolunteering.startAnimation(fadeOut);
        cmdStartVolunteering.setVisibility(View.GONE);
        cmdStopVolunteering.startAnimation(fadeIn);
        cmdStopVolunteering.setVisibility(View.VISIBLE);
    }

    public boolean isVolunteerOfThisBus() {
        /*if (routeNo == null || routeNo.isEmpty())
            routeNo = SharedPref.getString(getApplicationContext(), "routeNo");*/
        if (SharedPref.getString(getApplicationContext(), "student_volunteer", userId) == null
                || SharedPref.getString(getApplicationContext(), "student_volunteer", userId).isEmpty()
                || SharedPref.getString(getApplicationContext(), "volunteer_busno", userId) == null
                || SharedPref.getString(getApplicationContext(), "volunteer_busno", userId).isEmpty())
            return false;

        return SharedPref.getString(getApplicationContext(), "student_volunteer", userId).equals("TRUE")
                && SharedPref.getString(getApplicationContext(), "volunteer_busno", userId).equals(routeNo);
    }

    //**************************************** Map Section ***************************************//
    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        return false;
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        this.googleMap = googleMap;
        busTrackingMap.onStart();
        initUI();
        if (currentBusObject == null)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
        else
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
        googleMap.setTrafficEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(10f);
        googleMap.setMaxZoomPreference(19f);

        if (darkModeEnabled)
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.nightmode_mapstyle));

        googleMap.addMarker(new MarkerOptions().title("College").position(SSNCEPoint));
        googleMap.setOnMarkerClickListener(this);
        isMapLoaded = true;
        if (pd != null) pd.dismiss();
        if (initialSnapshot == null)
            hideMapView();
    }

    private void activateInfoChangedListeners() {
        busLocRef.removeEventListener(routeExistslistener);
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (updateListenerToMain) {
                    if (currentBusObject == null) {
                        if (tvVolunteerDetails.getVisibility() == View.VISIBLE)
                            runOnUiThread(() -> tvVolunteerDetails.setText("No data available currently."));
                    } else runOnUiThread(() -> tvVolunteerDetails.setVisibility(View.VISIBLE));

                    if (locationChangedListener == null)
                        locationChangedListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                String latLongString = dataSnapshot.getValue(String.class);
                                if (latLongString == null) {
                                    return;
                                }
                                int sep = latLongString.indexOf(',');
                                LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));
                                if (currentBusObject.getLocation() == null)
                                    runOnUiThread(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentlatLongs, 18f)));
                                currentBusObject.setLocation(currentlatLongs, handler, googleMap);
                                startTime = System.currentTimeMillis();
                                updateTimeElapsedTextViews();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                            }
                        };
                    if (onlineStatusChangedListener == null)
                        onlineStatusChangedListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                Boolean isSharingLoc = dataSnapshot.getValue(Boolean.class);
                                if (currentBusObject == null) return;
                                if (isSharingLoc == null) {
                                    if (!currentBusObject.isSharerOnline()) return;
                                    deactivateInfoChangedListeners();
                                    if (isVolunteerOfThisBus()) {
                                        hideLastUpdatedTV();
                                        showTimeElapsedTV = false;
                                    }
                                    currentBusObject.setUserOnline(false);
                                    runOnUiThread(() -> busLocRef.addValueEventListener(routeExistslistener));
                                    Thread.currentThread().interrupt();
                                } else if (currentBusObject.isSharerOnline() != isSharingLoc) {
                                    currentBusObject.setUserOnline(isSharingLoc);
                                    if (isVolunteerOfThisBus() && !isSharingLoc) {
                                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED) && !cmdStartVolunteering.isEnabled()) {
                                            runOnUiThread(() -> {
                                                cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                                                cmdStartVolunteering.setEnabled(true);
                                            });
                                        }
                                    }
                                    startTime = System.currentTimeMillis();
                                    updateTimeElapsedTextViews();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                            }
                        };
                    if (speedChangedListener == null)
                        speedChangedListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                Integer speed = dataSnapshot.getValue(Integer.class);
                                if (speed != null) {
                                    currentBusObject.setSpeed((int) (speed * 3.6));
                                    startTime = System.currentTimeMillis();
                                    updateTimeElapsedTextViews();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                            }
                        };
                    if (sharerChangedListener == null)
                        sharerChangedListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                String sharerId = dataSnapshot.getValue(String.class);
                                if (sharerId != null && !sharerId.equals("null")) {
                                    currentBusObject.setCurrentVolunteerId(sharerId);
                                    startTime = System.currentTimeMillis();
                                    updateTimeElapsedTextViews();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                            }
                        };
                    busLocRef.child("currentSharerID").addValueEventListener(sharerChangedListener);
                    busLocRef.child("sharingLoc").addValueEventListener(onlineStatusChangedListener);
                    busLocRef.child("speed").addValueEventListener(speedChangedListener);
                    busLocRef.child("latLong").addValueEventListener(locationChangedListener);
                    updateListenerToMain = false;
                }
            }
        }).start();
        if (timer == null) {
            timer = new Timer(false);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (showTimeElapsedTV)
                        updateTimeElapsedTextViews();
                }
            }, 0, 60000);
        }
    }

    private void updateTimeElapsedTextViews() {
        if (!showTimeElapsedTV || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
            return;
        if (tvLastUpdatedTimeDesc.getVisibility() != View.VISIBLE) {
            showLastUpdatedTV();
        }
        String timeElapsed = CommonUtils.getTime(new Date(startTime));
        String timeElapsedDummy = "last updated " + timeElapsed;

        timeElapsed = timeElapsed.substring(0, timeElapsed.indexOf(' '));
        if (timeElapsed.contains("s"))
            timeElapsedDummy = "updated just now";

        String currentlyDisplayedText = tvLastUpdatedTimeDesc.getText().toString() + tvLastUpdatedTimeTensDigit.getText().toString() + tvLastUpdatedTimeUnitsDigit.getText().toString() + tvLastUpdatedTimeUnit.getText().toString();
        if (Objects.equals(timeElapsedDummy, currentlyDisplayedText)) return;

        Animation enterAnim = AnimationUtils.loadAnimation(MapActivity.this, R.anim.slide_up_enter);

        if (timeElapsed.contains("s")) {
            String s = "updated just now";
            runOnUiThread(() -> {
                tvLastUpdatedTimeDesc.setText(s);
                tvLastUpdatedTimeDesc.startAnimation(enterAnim);
                tvLastUpdatedTimeUnit.setText("");
                tvLastUpdatedTimeUnitsDigit.setText("");
                tvLastUpdatedTimeTensDigit.setText("");
            });
        } else if (!currentlyDisplayedText.isEmpty()) {
            String s = timeElapsed;
            if (currentlyDisplayedText.charAt(0) == 'u' || (timeElapsed.charAt(0) == '1' && timeElapsed.length() == 2)) { //text is "updated just now".
                runOnUiThread(() -> {
                    tvLastUpdatedTimeDesc.setText("last updated ");
                    if (currentlyDisplayedText.charAt(0) == 'u')
                        tvLastUpdatedTimeDesc.startAnimation(enterAnim);
                    tvLastUpdatedTimeTensDigit.setText("");
                    tvLastUpdatedTimeUnitsDigit.setText(s.charAt(0) + "");
                    tvLastUpdatedTimeUnit.setText(s.charAt(1) + " ago");
                    tvLastUpdatedTimeUnitsDigit.startAnimation(enterAnim);
                    if (currentlyDisplayedText.charAt(0) == 'u')
                        tvLastUpdatedTimeUnit.startAnimation(enterAnim);
                });
            } else {
                runOnUiThread(() -> {
                    if (currentlyDisplayedText.length() == 19) { //text is "last updated xy ago" where x is from 1 to 9 and y is 'm', 'h', etc.
                        if (s.length() == 3) {
                            tvLastUpdatedTimeTensDigit.setText(s.charAt(0) + "");
                            tvLastUpdatedTimeTensDigit.startAnimation(enterAnim);
                        } else tvLastUpdatedTimeTensDigit.setText("");
                        tvLastUpdatedTimeUnitsDigit.setText(s.charAt(s.length() - 2) + "");
                        tvLastUpdatedTimeUnitsDigit.startAnimation(enterAnim);

                        if (currentlyDisplayedText.charAt(14) != s.charAt(s.length() - 1)) { //time units are not same
                            tvLastUpdatedTimeUnit.setText(s.charAt(s.length() - 1) + "");
                            tvLastUpdatedTimeUnit.startAnimation(enterAnim);
                        }
                    } else { //text is "last updated xyz ago" where x,y are from 1 to 9 and z is 'm', 'h', etc.
                        if (s.length() == 3 && s.charAt(0) != currentlyDisplayedText.charAt(13)) {
                            tvLastUpdatedTimeTensDigit.setText(s.charAt(0) + "");
                            tvLastUpdatedTimeTensDigit.startAnimation(enterAnim);
                        }

                        tvLastUpdatedTimeUnitsDigit.setText(s.charAt(s.length() - 2) + "");
                        tvLastUpdatedTimeUnitsDigit.startAnimation(enterAnim);

                        if (currentlyDisplayedText.charAt(15) != s.charAt(s.length() - 1)) { //time units are not same
                            tvLastUpdatedTimeUnit.setText(s.charAt(s.length() - 1) + "");
                            tvLastUpdatedTimeUnit.startAnimation(enterAnim);
                        }
                    }
                });
            }
        }
    }

    private void showLastUpdatedTV() {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
        runOnUiThread(() -> {
            tvLastUpdatedTimeDesc.setText("");
            tvLastUpdatedTimeTensDigit.setText("");
            tvLastUpdatedTimeUnitsDigit.setText("");
            tvLastUpdatedTimeUnit.setText("");

            AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
            ScaleAnimation zoomIn = new ScaleAnimation(1, 1, 0, 1);

            fadeIn.setFillAfter(true);
            zoomIn.setFillAfter(true);
            zoomIn.setDuration(500);
            fadeIn.setDuration(500);

            ScaleAnimation headerAnimation = new ScaleAnimation(1f, 0.9f, 1f, 0.9f);
            headerAnimation.setDuration(500);
            headerAnimation.setFillAfter(true);

            tvStatusBarHeader.startAnimation(headerAnimation);
            tvLastUpdatedTimeDesc.startAnimation(fadeIn);
            tvLastUpdatedTimeDesc.startAnimation(zoomIn);
            tvLastUpdatedTimeDesc.setVisibility(View.VISIBLE);
            tvLastUpdatedTimeTensDigit.setVisibility(View.VISIBLE);
            tvLastUpdatedTimeUnitsDigit.setVisibility(View.VISIBLE);
            tvLastUpdatedTimeUnit.setVisibility(View.VISIBLE);
        });
        showTimeElapsedTV = true;
    }

    private void hideLastUpdatedTV() {
        if (tvLastUpdatedTimeDesc.getVisibility() == View.GONE) return;
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
        runOnUiThread(() -> {
            AlphaAnimation fadeOut = new AlphaAnimation(1, 0);
            ScaleAnimation shrink = new ScaleAnimation(1, 0, 1, 0);

            fadeOut.setFillAfter(true);
            shrink.setFillAfter(true);
            shrink.setDuration(500);
            fadeOut.setDuration(500);
            AnimationSet animationSet = new AnimationSet(true);
            animationSet.addAnimation(fadeOut);
            animationSet.addAnimation(shrink);
            ScaleAnimation headerAnimation = new ScaleAnimation(0.9f, 1f, 0.9f, 1f);
            headerAnimation.setDuration(500);
            headerAnimation.setFillAfter(true);

            tvLastUpdatedTimeDesc.startAnimation(animationSet);
            tvLastUpdatedTimeTensDigit.startAnimation(animationSet);
            tvLastUpdatedTimeUnitsDigit.startAnimation(animationSet);
            tvLastUpdatedTimeUnit.startAnimation(animationSet);

            tvStatusBarHeader.startAnimation(headerAnimation);
            tvLastUpdatedTimeDesc.setVisibility(View.GONE);
            tvLastUpdatedTimeTensDigit.setVisibility(View.GONE);
            tvLastUpdatedTimeUnitsDigit.setVisibility(View.GONE);
            tvLastUpdatedTimeUnit.setVisibility(View.GONE);
            tvLastUpdatedTimeDesc.setText("");
            tvLastUpdatedTimeTensDigit.setText("");
            tvLastUpdatedTimeUnitsDigit.setText("");
            tvLastUpdatedTimeUnit.setText("");
            showTimeElapsedTV = false;
        });
    }

    private void deactivateInfoChangedListeners() {
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo);
        if (sharerChangedListener != null)
            busLocRef.child("currentSharerID").removeEventListener(sharerChangedListener);
        if (onlineStatusChangedListener != null)
            busLocRef.child("sharingLoc").removeEventListener(onlineStatusChangedListener);
        if (speedChangedListener != null)
            busLocRef.child("speed").removeEventListener(speedChangedListener);
        if (locationChangedListener != null)
            busLocRef.child("latLong").removeEventListener(locationChangedListener);

        if (routeExistslistener != null)
            busLocRef.addValueEventListener(routeExistslistener);
        //runOnUiThread(() -> tvVolunteerDetails.setVisibility(View.GONE));
    }

    private BusObject createBusObject(String sharerId, LatLng currentlatLongs, int speed) {
        if (currentBusObject != null) {
            currentBusObject.setLocation(currentlatLongs, handler, googleMap);
            currentBusObject.setCurrentVolunteerId(sharerId);
            currentBusObject.setSpeed(speed);
            return currentBusObject;
        }
        Marker busMarker = googleMap.addMarker(new MarkerOptions()
                .position(currentlatLongs)
                .icon(darkModeEnabled ? getBitmapDescriptor(R.drawable.ic_bus_yellow) : getBitmapDescriptor(R.drawable.ic_bus_blue))
                .title("Est. Speed: " + speed + " km/h"));
        BusObject busObject = new BusObject();
        busObject.setInfoUpdatedListener(new BusObject.OnInfoUpdatedListener() {
            @Override
            public void onSharerIdChanged(String r, String newSharerId) {
                AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (busObject.isSharerOnline())
                            tvVolunteerDetails.setText(String.format("Current Volunteer%s", busObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + busObject.getCurrentVolunteerId()));
                        else
                            tvVolunteerDetails.setText(String.format("Location last volunteered by%s", busObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + busObject.getCurrentVolunteerId()));
                        tvVolunteerDetails.startAnimation(fadeIn);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                fadeIn.setDuration(500);
                fadeIn.setStartOffset(500);
                tvVolunteerDetails.startAnimation(fadeOut);
            }

            @Override
            public void onLocationChanged(String r, LatLng location) {
                if (Looper.getMainLooper() == Looper.myLooper()) {
                    //googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, googleMap.getCameraPosition().zoom));
                    busObject.moveMarker(googleMap, location);
                } else runOnUiThread(() -> {
                    busObject.moveMarker(googleMap, location);
                    //googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, googleMap.getCameraPosition().zoom));
                });
            }

            @Override
            public void onOnlineStatusChanged(String r, boolean isOnline) {
                //if (isOnline == busObject.isSharerOnline()) return;
                AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (isOnline) {
                            if (!currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")))
                                if (cmdStopVolunteering.getVisibility() == View.VISIBLE)
                                    enableControls();
                            tvVolunteerDetails.setText(String.format("Current Volunteer%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + currentBusObject.getCurrentVolunteerId()));
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);

                        } else {
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                            tvVolunteerDetails.setText(String.format("Location last shared by%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + currentBusObject.getCurrentVolunteerId() + "     "));
                        }
                        isBusOnlineIV.startAnimation(fadeIn);
                        tvVolunteerDetails.startAnimation(fadeIn);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                fadeIn.setDuration(500);
                fadeIn.setStartOffset(500);
                isBusOnlineIV.startAnimation(fadeOut);
                tvVolunteerDetails.startAnimation(fadeOut);
            }

            @Override
            public void onSpeedChanged(String r, int newSpeed) {
            }
        });
        busObject.setRouteNo(routeNo);
        busObject.setCurrentVolunteerId(sharerId);
        busObject.setBusMarker(busMarker);
        busObject.setSpeed(speed);
        return busObject;
    }

    private BitmapDescriptor getBitmapDescriptor(int id) {
        Drawable vectorDrawable = getResources().getDrawable(id);
        int h = vectorDrawable.getIntrinsicHeight();
        int w = vectorDrawable.getIntrinsicWidth();
        vectorDrawable.setBounds(0, 0, w * 2, h * 2);
        Bitmap bm = Bitmap.createBitmap(2 * w, 2 * h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bm);
    }

    private void showMapView() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            if (busTrackingMap.getVisibility() == View.VISIBLE) return;
            novolunteerLL.setVisibility(View.GONE);
            busTrackingMap.setVisibility(View.VISIBLE);
        } else runOnUiThread(() -> {
            if (novolunteerLL == null || busTrackingMap == null) return;
            else if (busTrackingMap.getVisibility() == View.VISIBLE) return;
            novolunteerLL.setVisibility(View.GONE);
            busTrackingMap.setVisibility(View.VISIBLE);
        });
    }

    private void hideMapView() {
        if (busTrackingMap == null || tvNoVolunteer == null) return;
        if (Looper.getMainLooper() == Looper.myLooper()) {
            if (busTrackingMap.getVisibility() == View.GONE) return;
            busTrackingMap.setVisibility(View.GONE);
            novolunteerLL.setVisibility(View.VISIBLE);

        } else runOnUiThread(() -> {
            if (busTrackingMap.getVisibility() == View.GONE) return;
            busTrackingMap.setVisibility(View.GONE);
            novolunteerLL.setVisibility(View.VISIBLE);
        });
    }

    private void initMapViewAndUI(Bundle b) { //map load
        Bundle mapViewBundle = null;
        if (b != null) {
            mapViewBundle = b.getBundle(Constants.GMAPS_TEST_API_KEY);
        }
        busTrackingMap = findViewById(R.id.mapView_bus);
        //attemptToGetLocationPermissions(false);
        busTrackingMap.onCreate(mapViewBundle);
        busTrackingMap.getMapAsync(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!CommonUtils.isMyServiceRunning(this, TransmitLocationService.class)) {
            unregisterNetworkCallbacks();

        }
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onDestroy();
        deactivateInfoChangedListeners();
        if (routeExistslistener != null)
            busLocRef.removeEventListener(routeExistslistener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!CommonUtils.isMyServiceRunning(this, TransmitLocationService.class)) {
            unregisterNetworkCallbacks();
            SharedPref.putInt(getApplicationContext(), "disruption_count", 0);

        }
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onStop();
        if (routeExistslistener != null)
            busLocRef.removeEventListener(routeExistslistener);
    }

    @Override
    public void onResume() {
        checkRequirementsAndPermissions();
        try {
            super.onResume();
            if (busTrackingMap != null && googleMap != null) {
                busTrackingMap.onResume();
                if (googleMap != null)
                    if (currentBusObject != null && currentBusObject.getLocation() != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
                    } else if (SSNCEPoint != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
                    }
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "ERROR: " + e.getMessage() + "\nCAUSE: " + e.getCause(), Toast.LENGTH_LONG).show();
            recreate();
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onPause();
        deactivateInfoChangedListeners();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(Constants.GMAPS_TEST_API_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(Constants.GMAPS_TEST_API_KEY, mapViewBundle);
        }
        busTrackingMap.onSaveInstanceState(mapViewBundle);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkRequirementsAndPermissions();
        initNetworkCallbacks();
        if (googleMap != null) busTrackingMap.onStart();
    }

    @Override
    public void onBackPressed() {
        if (!CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
            hideLastUpdatedTV();
            unregisterNetworkCallbacks();
        }
        Bungee.slideRight(this);
        finish();
    }

    public static void showNotification(int id, String channelIdString, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelIdString, "Location Sharing Status", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.enableLights(true);
            notificationChannel.enableVibration(true);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            notificationChannel.setDescription("Bus Tracking Volunteer Status alerts.");
            notificationManager.createNotificationChannel(notificationChannel);

            NotificationCompat.Builder nbuilder = new NotificationCompat.Builder(context, channelIdString)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setChannelId(channelIdString)
                    .setAutoCancel(true)
                    .setLights(Color.BLUE, 500, 500)
                    .setColorized(true)
                    .setColor(ContextCompat.getColor(context, R.color.colorAccent))
                    .setContentIntent(pendingIntent);
            Notification n = nbuilder.build();
            n.flags = n.flags | Notification.FLAG_ONLY_ALERT_ONCE;
            notificationManager.notify(id, n);

        } else {
            NotificationCompat.Builder nbuilder = new NotificationCompat.Builder(context, channelIdString)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setContentIntent(pendingIntent);

            Notification n = nbuilder.build();
            n.flags = Notification.FLAG_ONLY_ALERT_ONCE;
            notificationManager.notify(id, n);
        }
    }
}
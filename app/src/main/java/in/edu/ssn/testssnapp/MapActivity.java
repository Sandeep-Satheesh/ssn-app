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
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import in.edu.ssn.testssnapp.models.BusObject;
import in.edu.ssn.testssnapp.services.TransmitLocationService;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.FCMHelper;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.YesNoDialogBuilder;
import spencerstudios.com.bungeelib.Bungee;

public class MapActivity extends BaseActivity implements GoogleMap.OnMarkerClickListener, OnMapReadyCallback {
    String routeNo, userId, volunteerId, volunteerBusNo;
    DatabaseReference busLocRef;
    TextView tvStatusBarHeader, tvNoVolunteer;
    ImageButton cmdStopVolunteering, cmdStartVolunteering;
    volatile boolean isSharingLoc = false, isBusVolunteer = false, isDataReady = false, updateListenerToMain = false;
    LatLng SSNCEPoint = new LatLng(12.7525, 80.196111);
    GoogleMap googleMap;
    DataSnapshot initialSnapshot;
    MapView busTrackingMap;
    ConnectivityManager.NetworkCallback userNetworkCallback, volunteerNetworkCallback;
    NetworkRequest networkRequest;
    ValueEventListener routeExistslistener, concurrentVolunteerListener, mainBusInfoChangesListener;
    ProgressDialog pd;
    boolean isMapLoaded = false;
    volatile BusObject currentBusObject;
    volatile String currentSharerID;
    volatile Intent i;
    TextView tvVolunteerDetails;
    ImageView backIV, isBusOnlineIV;
    RelativeLayout mapRL, novolunteerRL;
    ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initNetworkCallbacks();
        if (CommonUtils.alerter(this)) {
            if (!CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                startActivity(new Intent(this, NoNetworkActivity.class).putExtra("key", "null"));
                finish();
                Bungee.fade(this);
                return;
            }
        }
        if (darkModeEnabled) {
            setContentView(R.layout.activity_map_dark);
            clearLightStatusBar(this);
        } else {
            setContentView(R.layout.activity_map);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccent));
        }
        pd = new ProgressDialog(this);
        pd.setOnDismissListener(dialog -> {
            if (isDataReady) {
                if (initialSnapshot != null) {
                    String latLongString = initialSnapshot.child("latLong").getValue(String.class), sharerId = initialSnapshot.child("currentSharerID").getValue(String.class);
                    int speed = initialSnapshot.child("speed").getValue() != null ? (int) (initialSnapshot.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : initialSnapshot.child("speed").getValue(int.class) * 3.6) : 0;
                    boolean isSharingLoc = initialSnapshot.child("sharingLoc").getValue(Boolean.class) == null ? false : initialSnapshot.child("sharingLoc").getValue(Boolean.class);

                    if (latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.isEmpty())
                        return;

                    int sep = latLongString.indexOf(',');
                    LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));
                    currentBusObject = createBusObject(sharerId, currentlatLongs, speed, isSharingLoc);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentlatLongs, 18f));
                    initialSnapshot = null;
                }
                showMapView();
                updateListenerToMain = true;
                listenForInfoChanges();
                busLocRef.removeEventListener(routeExistslistener);
                isDataReady = false;
            }
        });
        pd.setMessage("Loading the map, please wait...");
        pd.setCancelable(false);
        pd.show();
        //to try to circumvent google maps API 500 queries-per-second limit.
        Random random = new Random();
        new CountDownTimer(1 + random.nextInt(6) * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                initUI();
                initMapView(savedInstanceState);
            }
        }.start();
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
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        userNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (!isUserOnline()) {
                    DatabaseReference.goOnline();
                    if (mapRL.getVisibility() != View.VISIBLE)
                        setUserOnline(true, true);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (isUserOnline()) {
                    DatabaseReference.goOffline();
                    setUserOnline(false, true);
                }
            }
        };
        volunteerNetworkCallback = new ConnectivityManager.NetworkCallback() {
            boolean onLostIsRunning = true;

            void attemptToReconnect() {
                FCMHelper.clearNotification(2, MapActivity.this);
                DatabaseReference buslocref = FirebaseDatabase.getInstance().getReference("Bus Locations").child(SharedPref.getString(getApplicationContext(), "routeNo"));
                buslocref.keepSynced(true);
                boolean instantRestart = SharedPref.getBoolean(getApplicationContext(), "instant_restart");
                if (instantRestart) {
                    restartLocationTransmission();
                    SharedPref.putBoolean(getApplicationContext(), "instant_restart", false);
                } else {
                    sendServiceNotifMessage("Reconnecting to database...", "Checking if any volunteer has taken over...", true);
                    if (cmdStopVolunteering != null)
                        runOnUiThread(() -> cmdStopVolunteering.setImageResource(R.drawable.ic_location_off_disabled));
                    resolveVolunteerStatus(buslocref);
                }
                onLostIsRunning = true;
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (isUserOnline() || SharedPref.getString(getApplicationContext(), "routeNo") == null ||
                        SharedPref.getString(getApplicationContext(), "routeNo").isEmpty() || onLostIsRunning)
                    return;
                attemptToReconnect();
            }

            @Override
            public void onLost(@NonNull Network network) {
                onLostIsRunning = true;
                super.onLost(network);
                setUserOnline(false, true);
                int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                SharedPref.putInt(getApplicationContext(), "disruption_count", disruptionCount + 1);
                if (disruptionCount >= 4) {
                    showNotification(3, "1", "Auto-stopped volunteering",
                            "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", MapActivity.this, new Intent(MapActivity.this, MapActivity.class));
                    Toast.makeText(getApplicationContext(),
                            "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", Toast.LENGTH_LONG).show();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            disableControls();
                            if (cmdStartVolunteering != null)
                                cmdStartVolunteering.setVisibility(View.GONE);
                        }
                    });
                    finish();
                }
                DatabaseReference.goOffline();
                final long[] i = {0};
                if (disruptionCount < 4)
                    new CountDownTimer(30000, 100) {

                        @Override
                        public void onTick(long millisUntilFinished) {
                            i[0]++;
                            if (i[0] % 10 != 0) return;
                            if (CommonUtils.alerter(MapActivity.this)) {
                                String msg = "Please reconnect in " + (30 - (i[0] / 10)) + ((30 - (i[0] / 10)) == 1 ? " second." : " seconds.");
                                showNotification(2, "2", "Your internet connection was interrupted.", msg, MapActivity.this, new Intent(MapActivity.this, MapActivity.class));
                            } else {
                                SharedPref.putBoolean(getApplicationContext(), "instant_restart", true);
                                FCMHelper.clearNotification(2, MapActivity.this);
                                onLostIsRunning = false;
                                cancel();
                            }
                        }

                        @Override
                        public void onFinish() {
                            SharedPref.putBoolean(getApplicationContext(), "instant_restart", false);
                            suspendLocationTransmission();
                            FCMHelper.clearNotification(2, MapActivity.this);
                            onLostIsRunning = false;
                        }
                    }.start();

                else {

                    onLostIsRunning = false;
                }
            }
        };
        unregisterNetworkCallbacks();
        if (CommonUtils.alerter(this)) setUserOnline(false, true);
        else setUserOnline(true, true);
        if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) || isSharingLoc)
            connectivityManager.registerNetworkCallback(networkRequest, volunteerNetworkCallback);
        else connectivityManager.registerNetworkCallback(networkRequest, userNetworkCallback);
    }

    private void resolveVolunteerStatus(DatabaseReference buslocref) {
        final String[] isSharing = {"false"};
        if (busLocRef == null) busLocRef = buslocref;
        CountDownLatch done = new CountDownLatch(3);
        busLocRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentSharerID = dataSnapshot.child("currentSharerID").getValue(String.class);
                    isSharing[0] = dataSnapshot.child("currentSharerID").getValue(String.class);
                }
                done.countDown();
                if (done.getCount() == 0)
                    busLocRef.removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
        try {

            if (!done.await(20, TimeUnit.SECONDS)) {
                showNotification(2, "1", "The background service has been stopped", "The server took too long to respond. The background service has been stopped. Thank you for your services!", MapActivity.this, new Intent(MapActivity.this, BusRoutesActivity.class));
                if (cmdStartVolunteering != null) {
                    switchToUserNetworkCallback();
                    runOnUiThread(() -> busLocRef.addValueEventListener(routeExistslistener));
                } else {
                    unregisterNetworkCallbacks();

                }
            } else setUserOnline(true, true);
            //it will wait till the response is received from firebase.

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (Objects.equals(SharedPref.getString(getApplicationContext(), "email"), currentSharerID)
                || (currentSharerID == null || Objects.equals(currentSharerID, "null"))) {

            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        FCMHelper.clearNotification(1, MapActivity.this);
                        restartLocationTransmission();
                    } else {
                        Toast.makeText(getApplicationContext(), "Your GPS is not enabled! Cannot auto-restart location sharing! The background service will now stop.", Toast.LENGTH_SHORT).show();
                        unregisterNetworkCallbacks();
                        stopLocationTransmission();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Location permissions have been disabled! Cannot auto-restart location sharing! The background service will now stop.", Toast.LENGTH_SHORT).show();
                    unregisterNetworkCallbacks();
                    stopLocationTransmission();
                }
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                FCMHelper.clearNotification(1, MapActivity.this);
                restartLocationTransmission();
            } else {
                Toast.makeText(getApplicationContext(), "Your GPS is not enabled! Cannot auto-restart location sharing! The background service will now stop.", Toast.LENGTH_SHORT).show();
                unregisterNetworkCallbacks();
            }
        } else {
            showNotification(2, "1", "The background service has been stopped", "Another volunteer has taken over since your last disconnection! Thank you for your services!", MapActivity.this, new Intent(MapActivity.this, BusRoutesActivity.class));
            if (cmdStartVolunteering != null) {
                switchToUserNetworkCallback();
                if (busLocRef == null) busLocRef = buslocref;
                runOnUiThread(() -> busLocRef.addValueEventListener(routeExistslistener));
            } else {
                unregisterNetworkCallbacks();
            }
        }

    }

    private boolean isUserOnline() {
        return SharedPref.getBoolean(getApplicationContext(), "isUserOnline");
    }

    private void setUserOnline(boolean isOnline, boolean UIUpdate) {
        SharedPref.putBoolean(getApplicationContext(), "isUserOnline", isOnline);
        if (currentBusObject == null || cmdStartVolunteering == null || cmdStopVolunteering == null || isBusOnlineIV == null)
            return;
        if (UIUpdate)
            runOnUiThread(() -> {
                if (isOnline) {
                    cmdStartVolunteering.setEnabled(false);
                    cmdStartVolunteering.setBackground(getResources().getDrawable(R.drawable.ic_location_on_disabled));
                } else {
                    cmdStartVolunteering.setEnabled(true);
                    cmdStartVolunteering.setBackground(getResources().getDrawable(R.drawable.ic_location_on));
                }
                AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (isOnline) {
                            if (currentBusObject != null)
                                tvVolunteerDetails.setText(String.format("Current Volunteer%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + currentBusObject.getCurrentVolunteerId() + "     "));
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);

                        } else {
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                            if (currentBusObject != null)
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
            });
    }

    private void initUI() {
        cmdStopVolunteering = findViewById(R.id.stop);
        cmdStartVolunteering = findViewById(R.id.start);
        tvStatusBarHeader = findViewById(R.id.tv_trackbus);
        isBusOnlineIV = findViewById(R.id.iv_busOnlineStatus);
        busTrackingMap = findViewById(R.id.mapView_bus);
        tvVolunteerDetails = findViewById(R.id.tv_volunterid);
        backIV = findViewById(R.id.backIV);
        mapRL = findViewById(R.id.maplayout);
        novolunteerRL = findViewById(R.id.layout_empty);
        tvNoVolunteer = findViewById(R.id.tv_novolunteer);

        routeNo = getIntent().getStringExtra("routeNo");

        AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1000);
        tvStatusBarHeader.setText(String.format("Track Bus No. %s", routeNo));
        tvStatusBarHeader.startAnimation(fadeIn);
        backIV.startAnimation(fadeIn);
        userId = SharedPref.getString(getApplicationContext(), "email");
        volunteerId = SharedPref.getString(getApplicationContext(), "student_volunteer", userId);
        volunteerBusNo = SharedPref.getString(getApplicationContext(), "volunteer_busno", userId);
        isBusVolunteer = checkVolunteerOfThisBus();
        if (isBusVolunteer) {
            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
            SharedPref.putString(MapActivity.this, "routeNo", routeNo);
        }

        backIV.setOnClickListener(v -> {
            onBackPressed();
        });
        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        DatabaseReference.goOnline();
        busLocRef.keepSynced(true);
        routeExistslistener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getChildrenCount() == 4) {
                    String latLongString = dataSnapshot.child("latLong").getValue(String.class), sharerId = dataSnapshot.child("currentSharerID").getValue(String.class);
                    int speed = dataSnapshot.child("speed").getValue() != null ? (int) (dataSnapshot.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : dataSnapshot.child("speed").getValue(int.class) * 3.6) : 0;
                    boolean isSharingLoc = dataSnapshot.child("sharingLoc").getValue(Boolean.class) == null ? false : dataSnapshot.child("sharingLoc").getValue(Boolean.class);

                    if (latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.equals("null"))
                        return;

                    setUserOnline(true, true);
                    if (!isMapLoaded) {
                        initialSnapshot = dataSnapshot;
                        pd.show();
                        isDataReady = true;
                        return;
                    } else {
                        showMapView();
                        listenForInfoChanges();
                        updateListenerToMain = true;
                        busLocRef.removeEventListener(this);
                    }
                    int sep = latLongString.indexOf(',');
                    LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));
                    currentBusObject = createBusObject(sharerId, currentlatLongs, speed, isSharingLoc);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentlatLongs, 18f));
                    isDataReady = true;
                } else if (getIntent().getStringExtra("routeNo").equals(SharedPref.getString(getApplicationContext(), "routeNo")) && CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                    tvNoVolunteer.setText(R.string.no_location_value);
                else tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                hideMapView();
            }
        };
        busLocRef.addValueEventListener(routeExistslistener);

        //*****************************Volunteer Section ****************************//
        if (!isBusVolunteer) {
            switchToUserNetworkCallback();
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) isBusOnlineIV.getLayoutParams();
            marginParams.setMargins(0, 0, 150, 0);
        } else {
            cmdStartVolunteering.startAnimation(fadeIn);

            if (SharedPref.getBoolean(getApplicationContext(), "stopbutton"))
                if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                    disableControls();
                    if (novolunteerRL.getVisibility() == View.VISIBLE)
                        tvNoVolunteer.setText(R.string.no_location_value);
                } else {
                    Toast.makeText(getApplicationContext(), R.string.improper_shutdown, Toast.LENGTH_LONG).show();
                    SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
                    isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                }
            else
                enableControls();


            cmdStartVolunteering.setOnClickListener(v -> {
                if (CommonUtils.alerter(MapActivity.this)) {
                    Toast.makeText(getApplicationContext(), "No network connection!", Toast.LENGTH_SHORT).show();
                    return;
                }
                int disruptionCount = SharedPref.getInt(getApplicationContext(), "disruption_count");
                SharedPref.putInt(getApplicationContext(), "disruption_count", disruptionCount + 1);
                if (disruptionCount >= 4) {

                    Toast.makeText(getApplicationContext(),
                            "Your network connection seems to be unstable, or you are restarting sharing too frequently. The service has been stopped. Please check your connection for stability, and then go back into the app to start volunteering!", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                //TODO: implement getInternetTime() here.
                busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (!dataSnapshot.exists()) {
                            if (novolunteerRL.getVisibility() == View.VISIBLE) showMapView();
                            attemptToGetLocationPermissions(true);
                            switchToVolunteerNetworkCallback();
                            return;
                        }
                        String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                        Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                        if (s != null && !s.equals("null") && !s.equals(userId) && b != null && b) {
                            Toast.makeText(getApplicationContext(), R.string.cannot_start_loc_sharing, Toast.LENGTH_LONG).show();
                            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {
                                stopLocationTransmission();
                                busLocRef.child("currentSharerID").setValue(s);
                            }
                        } else {
                            if (novolunteerRL.getVisibility() == View.VISIBLE) showMapView();
                            attemptToGetLocationPermissions(true);
                            switchToVolunteerNetworkCallback();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            });
            cmdStopVolunteering.setOnClickListener(v -> YesNoDialogBuilder.createDialog(
                    this,
                    darkModeEnabled,
                    "Confirm stopping location sharing?",
                    "Your location will stop being shared to the servers.",
                    false,
                    (dialog, which) -> {
                        SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
                        SharedPref.putString(getApplicationContext(), "routeNo", "");
                        stopLocationTransmission();
                        switchToUserNetworkCallback();
                        busLocRef.removeValue();
                        if (currentBusObject != null && isBusVolunteer)
                            currentBusObject.setUserOnline(false);
                    },
                    (dialog, which) -> dialog.dismiss()).show());

        }

    }


    private void startLocationTransmission() {
        if (routeNo != null && !routeNo.isEmpty())
            SharedPref.putString(getApplicationContext(), "routeNo", routeNo);
        else routeNo = SharedPref.getString(getApplicationContext(), "routeNo");

        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        concurrentVolunteerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) || isSharingLoc) {
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class) && s != null && !s.equals("null") && !s.equals(SharedPref.getString(getApplicationContext(), "email")) && b != null && b) {
                        stopLocationTransmission();
                        busLocRef.setValue(dataSnapshot.getValue());
                        Toast.makeText(getApplicationContext(), R.string.cannot_start_loc_sharing, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        };
        busLocRef.addValueEventListener(concurrentVolunteerListener);
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", true);

        i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_START_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("suspendFlag", false);
        startService(i);
        isSharingLoc = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disableControls();
            }
        });
    }

    private void restartLocationTransmission() {
        sendServiceNotifMessage("Finalizing decision to reconnect", "Please wait...", true);
        CountDownLatch latch = new CountDownLatch(3);
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(SharedPref.getString(getApplicationContext(), "routeNo"));
        busLocRef.keepSynced(true);
        busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (latch.getCount() > 1) {
                    latch.countDown();
                    busLocRef.removeEventListener(this);
                    busLocRef.addListenerForSingleValueEvent(this);
                    return;
                }
                //if no sharer, or same sharer id, restart service.
                if (Objects.equals(SharedPref.getString(getApplicationContext(), "email"), dataSnapshot.child("currentSharerID").getValue(String.class))
                        || (dataSnapshot.child("currentSharerID").getValue(String.class) == null || Objects.equals(dataSnapshot.child("currentSharerID").getValue(String.class), "null"))) {
                    SharedPref.putBoolean(getApplicationContext(), "stopbutton", true);
                    startLocationTransmission();
                } else {
                    if (mapRL == null) unregisterNetworkCallbacks();
                    else switchToUserNetworkCallback();
                    i = new Intent(getApplicationContext(), TransmitLocationService.class);
                    i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
                    i.putExtra("routeNo", routeNo);
                    i.putExtra("suspendFlag", true);
                    startService(i);
                    showNotification(2, "1", "The background service has been stopped", "Another volunteer has taken over since your last disconnection! Thank you for your services!", MapActivity.this, new Intent(MapActivity.this, BusRoutesActivity.class));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            enableControls();
                        }
                    });
                    SharedPref.putString(getApplicationContext(), "routeNo", "");
                    SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
                    isSharingLoc = false;
                }
                latch.countDown();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showNotification(2, "1", "The background service has been stopped", "There was a connection error.", MapActivity.this, new Intent(MapActivity.this, BusRoutesActivity.class));
                unregisterNetworkCallbacks();
                stopLocationTransmission();
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runOnUiThread(MapActivity.this::disableControls);
    }

    private void suspendLocationTransmission() {
        i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_CHANGE_NOTIFICATION_MESSAGE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("suspendFlag", true);
        i.putExtra("messageTitle", "Location sharing suspended");
        i.putExtra("messageContent", "We'll auto-restart location sharing once you're back online in some time.");
        startService(i);
        isSharingLoc = false;
    }

    private void stopLocationTransmission() {
        FCMHelper.clearNotification(2, MapActivity.this);
        if (routeNo == null) routeNo = SharedPref.getString(getApplicationContext(), "routeNo");
        if (busLocRef == null)
            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        if (currentBusObject != null) currentBusObject.setUserOnline(false);
        if (concurrentVolunteerListener != null)
            busLocRef.removeEventListener(concurrentVolunteerListener);
        i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("suspendFlag", true);
        stopService(i);
        busLocRef.removeValue();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enableControls();
            }
        });
        SharedPref.putString(getApplicationContext(), "routeNo", "");
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
        isSharingLoc = false;
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

    //*****************************Check whether location permission is granted****************************//

    private void attemptToGetLocationPermissions(boolean continueWithEnablingGPS) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (continueWithEnablingGPS)
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2);
                    else
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
                } else {
                    if (continueWithEnablingGPS)
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                    else
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 3);
                }
            } else if (continueWithEnablingGPS)
                attemptToEnableGPS();
        } else if (continueWithEnablingGPS)
            attemptToEnableGPS();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) return;
        if (requestCode == 2) {
            for (int grantResult : grantResults)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    attemptToEnableGPS();
                } else {
                    Toast.makeText(getApplicationContext(), "The required permissions have been denied! Cannot start location sharing!", Toast.LENGTH_LONG).show();
                    if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                        stopLocationTransmission();
                }
        } else if (requestCode == 3) {
            for (int grantResult : grantResults)
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Please enable location permissions for viewing the map!", Toast.LENGTH_LONG).show();
                    finish();
                }
        }
    }


    //************************Check whether gps is enabled or not *********************//

    private void attemptToEnableGPS() {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(200);
        locationRequest.setFastestInterval(100);

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
                        startLocationTransmission();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        //Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings");
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result
                            //in onActivityResult();
                            status.startResolutionForResult(MapActivity.this, 1);
                        } catch (IntentSender.SendIntentException e) {
                            //Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        //Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
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
                if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                    stopLocationTransmission();
            } else {
                Toast.makeText(getApplicationContext(), "Please wait, it may take some time for the changes to be reflected in the map.", Toast.LENGTH_LONG).show();
                startLocationTransmission();
            }

    }

    //*****enable start stop button controls and check if user is volunteer of the bus****//

    public void enableControls() {
        if (cmdStartVolunteering == null || cmdStopVolunteering == null) return;
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
        if (cmdStartVolunteering == null || cmdStopVolunteering == null) return;
        cmdStopVolunteering.setImageResource(R.drawable.ic_location_off);
        AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
        fadeOut.setDuration(1000);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        cmdStartVolunteering.startAnimation(fadeOut);
        cmdStartVolunteering.setVisibility(View.GONE);
        cmdStopVolunteering.startAnimation(fadeIn);
        cmdStopVolunteering.setVisibility(View.VISIBLE);

    }

    public boolean checkVolunteerOfThisBus() {
        if (routeNo == null || routeNo.isEmpty())
            routeNo = SharedPref.getString(getApplicationContext(), "routeNo");
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
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
        googleMap.setTrafficEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(10f);
        googleMap.setMaxZoomPreference(19f);

        if (darkModeEnabled)
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.nightmode_mapstyle));

        googleMap.addMarker(new MarkerOptions().title("College").position(SSNCEPoint));
        googleMap.setOnMarkerClickListener(this);
        busTrackingMap.onStart();
        isMapLoaded = true;
        pd.dismiss();
        if (initialSnapshot == null)
            hideMapView();
    }

    private void listenForInfoChanges() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted())
                    if (updateListenerToMain) {
                        mainBusInfoChangesListener = new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() == 4) {
                                    String latLongString = dataSnapshot.child("latLong").getValue(String.class), sharerId = dataSnapshot.child("currentSharerID").getValue(String.class);
                                    int speed = dataSnapshot.child("speed").getValue() != null ? (int) (dataSnapshot.child("speed").getValue(Integer.class) * 3.6 < 1 ? 0 : dataSnapshot.child("speed").getValue(Integer.class) * 3.6) : 0;
                                    Boolean isSharingLoc = dataSnapshot.child("sharingLoc").getValue(Boolean.class) == null ? false : dataSnapshot.child("sharingLoc").getValue(Boolean.class);

                                    if (isSharingLoc == null || latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.isEmpty())
                                        return;
                                    runOnUiThread(() -> updateChangesToCurrentBusObject(latLongString, sharerId, speed, isSharingLoc));
                                } else if (currentBusObject != null) {
                                    currentBusObject.setUserOnline(false);
                                    busLocRef.removeEventListener(this);
                                    busLocRef.addValueEventListener(routeExistslistener);
                                    updateListenerToMain = false;
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                            }
                        };
                        if (busLocRef == null)
                            busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(SharedPref.getString(getApplicationContext(), "routeNo"));
                        busLocRef.addValueEventListener(mainBusInfoChangesListener);
                        updateListenerToMain = false;
                    }
            }
        }).start();

    }

    private void updateChangesToCurrentBusObject(String latLongString, String sharerId, int speed, boolean isSharingLoc) {
        int sep = latLongString.indexOf(',');
        LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));
        if (currentBusObject == null) {
            currentBusObject = createBusObject(sharerId, currentlatLongs, speed, isSharingLoc);
            currentBusObject.setUserOnline(isSharingLoc);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
        } else if (!sharerId.equals("null")) {
            if (currentlatLongs.latitude != currentBusObject.getLocation().latitude || currentlatLongs.longitude != currentBusObject.getLocation().longitude)
                currentBusObject.setLocation(currentlatLongs);

            if (currentBusObject.getSpeed() != speed)
                currentBusObject.setSpeed(speed);

            if (!Objects.equals(sharerId, currentBusObject.getCurrentVolunteerId()))
                currentBusObject.setCurrentVolunteerId(sharerId);

            else if (currentBusObject.isSharerOnline() != isSharingLoc)
                currentBusObject.setUserOnline(isSharingLoc);
        } else currentBusObject.setUserOnline(false);
    }

    private BusObject createBusObject(String sharerId, LatLng currentlatLongs, int speed, boolean isSharingLoc) {
        if (currentBusObject != null) currentBusObject.getBusMarker().remove();
        Marker busMarker = googleMap.addMarker(new MarkerOptions()
                .position(currentlatLongs)
                .icon(darkModeEnabled ? getBitmapDescriptor(R.drawable.ic_bus_yellow) : getBitmapDescriptor(R.drawable.ic_bus_blue))
                .title("Est. Speed: " + speed + " km/h"));
        BusObject busObject = new BusObject();
        busObject.setLocationUpdatedListener(new BusObject.OnLocationUpdatedListener() {
            @Override
            public void onSharerIdChanged(String r, String newSharerId) {
                runOnUiThread(() -> {
                    if (busObject.isSharerOnline()) {
                        tvVolunteerDetails.setText(String.format("Current Volunteer%s", newSharerId.equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + newSharerId + "     "));
                        isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);

                    } else {
                        isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                        tvVolunteerDetails.setText(String.format("Location last shared by%s", busObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + busObject.getCurrentVolunteerId() + "     "));
                    }
                });
            }

            @Override
            public void onLocationChanged(String r, LatLng location) {
                runOnUiThread(() -> googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, googleMap.getCameraPosition().zoom)));
            }

            @Override
            public void onOnlineStatusChanged(String r, boolean isOnline) {
                runOnUiThread(() -> {
                    AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
                    if (isOnline) {
                        cmdStartVolunteering.setEnabled(false);
                        cmdStartVolunteering.setImageResource(R.drawable.ic_location_on_disabled);
                    } else {
                        cmdStartVolunteering.setEnabled(true);
                        cmdStartVolunteering.setImageResource(R.drawable.ic_location_on);
                    }
                    fadeOut.setDuration(500);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            if (isOnline) {
                                tvVolunteerDetails.setText(String.format("Current Volunteer%s", busObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + busObject.getCurrentVolunteerId() + "     "));
                                isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);

                            } else {
                                isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                                tvVolunteerDetails.setText(String.format("Location last shared by%s", busObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID:\n" + busObject.getCurrentVolunteerId() + "     "));
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
                });
            }

            @Override
            public void onSpeedChanged(String r, int newSpeed) {
                runOnUiThread(() -> {
                    if (busObject.getBusMarker().isInfoWindowShown()) {
                        busObject.getBusMarker().hideInfoWindow();
                        busObject.getBusMarker().showInfoWindow();
                    }
                });
            }
        });
        busObject.setRouteNo(routeNo);
        busObject.setCurrentVolunteerId(sharerId);
        busObject.setBusMarker(busMarker);
        busObject.setSpeed(speed);
        busObject.setUserOnline(isSharingLoc);

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
        novolunteerRL.setVisibility(View.GONE);
        mapRL.setVisibility(View.VISIBLE);
    }

    private void hideMapView() {
        mapRL.setVisibility(View.GONE);
        novolunteerRL.setVisibility(View.VISIBLE);
        if (isBusVolunteer)
            if (CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class))
                tvNoVolunteer.setText(R.string.no_location_value);
            else tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
        else tvNoVolunteer.setText(R.string.no_volunteer_available);
    }

    private void initMapView(Bundle b) { //map load
        Bundle mapViewBundle = null;
        if (b != null) {
            mapViewBundle = b.getBundle(Constants.GMAPS_TEST_API_KEY);
        }
        attemptToGetLocationPermissions(false);
        busTrackingMap.onCreate(mapViewBundle);
        busTrackingMap.getMapAsync(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!SharedPref.getBoolean(getApplicationContext(), "stopbutton")) {
            unregisterNetworkCallbacks();
        }
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onDestroy();
        if (mainBusInfoChangesListener != null)
            busLocRef.removeEventListener(mainBusInfoChangesListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!SharedPref.getBoolean(getApplicationContext(), "stopbutton")) {
            unregisterNetworkCallbacks();
        }
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (busTrackingMap != null && googleMap != null) {
            busTrackingMap.onResume();
            if (currentBusObject != null)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onPause();
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
        if (googleMap != null) busTrackingMap.onStart();
    }

    @Override
    public void onBackPressed() {
        if (!CommonUtils.isMyServiceRunning(getApplicationContext(), TransmitLocationService.class)) {

        }
        Bungee.slideRight(this);
        finish();
    }

    public static void showNotification(int id, String channelIdString, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel(channelIdString, "general", NotificationManager.IMPORTANCE_HIGH));
            Notification.Builder nbuilder = new Notification.Builder(context, channelIdString)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setChannelId(channelIdString)
                    .setAutoCancel(true)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent);

            notificationManager.notify(id, nbuilder.build());
        } else {
            Notification.Builder nbuilder = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setSound(alarmSound)
                    .setContentIntent(pendingIntent);

            notificationManager.notify(id, nbuilder.build());
        }
    }
}
package in.edu.ssn.testssnapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;

import in.edu.ssn.testssnapp.BusRoutesActivity;
import in.edu.ssn.testssnapp.MapActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.SystemTimeChangedReceiver;

public class TransmitLocationService extends Service implements LocationListener {
    DatabaseReference busLocDBRef;
    LocationManager locationManager;
    volatile boolean suspendFlag = false;
    //    double SSNCEPolygon[]; //TODO: The polygon within which we have to check to stop service automatically; check note at end.
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_CHANGE_NOTIFICATION_MESSAGE = "ACTION_CHANGE_NOTIFICATION_MESSAGE";
    TimerTask updateTimeTask, validateTimeTask;
    String routeNo, latLongString, userID;
    int speed;
    SystemTimeChangedReceiver systemTimeChangedReceiver;
    IntentFilter intentFilter;
    Timer timer;

    public static Notification prepareForegroundServiceNotification(boolean goToActivity, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, goToActivity ? intent : new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder nbuilder;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        //Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder;
            notificationManager.createNotificationChannel(new NotificationChannel(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID, "Bus-Tracking service status", NotificationManager.IMPORTANCE_HIGH));
            NotificationChannel channel = notificationManager.getNotificationChannel(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID);
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.setDescription("Bus Tracking Volunteer Status");
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            builder = new Notification.Builder(context, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setChannelId(Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setColorized(true)
                    .setColor(context.getResources().getColor(R.color.colorAccent))
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent);

            return builder.build();
        } else {
            nbuilder = new NotificationCompat.Builder(context, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ssn_logo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(false)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_MAX)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setColorized(true)
                    .setOngoing(true)
                    .setColor(context.getResources().getColor(R.color.colorAccent))
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setContentIntent(pendingIntent);

            return nbuilder.build();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return super.onStartCommand(intent, flags, startId);
        }
        timer = new Timer(true);
        if (validateTimeTask == null)
            validateTimeTask = new TimerTask() {
                @Override
                public void run() {
                    validateCurrentTime();
                }
            };
        if (updateTimeTask == null)
            updateTimeTask = new TimerTask() {
                @Override
                public void run() {
                    if (busLocDBRef != null)
                        busLocDBRef.child("timestamp").setValue(ServerValue.TIMESTAMP);
                }
            };

        switch (intent.getAction()) {
            case ACTION_START_FOREGROUND_SERVICE:
                locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                userID = SharedPref.getString(getApplicationContext(), "email");
                //Build the notification...

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else {
                    startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
                }

                busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);

                timer.scheduleAtFixedRate(validateTimeTask, 0, 1000);
                timer.scheduleAtFixedRate(updateTimeTask, 0, 60000);

                busLocDBRef.child("sharingLoc").onDisconnect().cancel();
                busLocDBRef.child("currentSharerID").onDisconnect().cancel();
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
                intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                systemTimeChangedReceiver = new SystemTimeChangedReceiver();
                try {
                    registerReceiver(systemTimeChangedReceiver, intentFilter);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (locationManager != null) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 100, this);
                        Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (l != null) {
                            checkLocationValidity(l);
                            latLongString = l.getLatitude() + "," + l.getLongitude();
                            busLocDBRef.child("latLong").setValue(latLongString);
                            speed = (int) l.getSpeed();
                            busLocDBRef.child("speed").setValue(speed);
                            busLocDBRef.child("currentSharerID").setValue(userID);
                            busLocDBRef.child("sharingLoc").setValue(true);
                        }
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                busLocDBRef.child("currentSharerID").onDisconnect().setValue("null");
                busLocDBRef.child("sharingLoc").onDisconnect().setValue(false);
//                Toast.makeText(getApplicationContext(), "Location Sharing started successfully.", Toast.LENGTH_SHORT).show();

                break;
            case ACTION_CHANGE_NOTIFICATION_MESSAGE:
                validateCurrentTime();
                String messageTitle = intent.getStringExtra("messageTitle"), messageContent = intent.getStringExtra("messageContent");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);

                if (messageTitle != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, prepareForegroundServiceNotification(!messageTitle.contains("Reconnecting"), messageTitle, messageContent, this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                    } else {
                        startForeground(1, prepareForegroundServiceNotification(!messageTitle.contains("Reconnecting"), messageTitle, messageContent, this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
                    }
                }
                if (locationManager == null)
                    locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                userID = SharedPref.getString(getApplicationContext(), "email");
                if (busLocDBRef == null)
                    busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);

                break;
            case ACTION_STOP_FOREGROUND_SERVICE:
                try {
                    updateTimeTask.cancel();
                    validateTimeTask.cancel();
                    timer.cancel();
                    timer.purge();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                routeNo = intent.getStringExtra("routeNo");
                suspendFlag = intent.getBooleanExtra("suspendFlag", true);
                userID = SharedPref.getString(getApplicationContext(), "email");
                if (routeNo != null && !routeNo.isEmpty())
                    busLocDBRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);

                locationManager.removeUpdates(this);
                try {
                    unregisterReceiver(systemTimeChangedReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (busLocDBRef != null) {
                    if (intent.getBooleanExtra("deleteDBValue", false)) {
                        busLocDBRef.removeValue();
                    }
                }
                stopForeground(true);
                stopSelf();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onLocationChanged(Location location) {
        //changeProviderIfRequired(locationManager.getBestProvider(criteria, true));

        if (CommonUtils.alerter(this) || suspendFlag) return;
        checkLocationValidity(location);
        busLocDBRef.child("currentSharerID").setValue(userID);
        busLocDBRef.child("sharingLoc").setValue(true);
        latLongString = location.getLatitude() + "," + location.getLongitude();
        busLocDBRef.child("latLong").setValue(latLongString);

        speed = (int) location.getSpeed();
        busLocDBRef.child("speed").setValue(speed);
        busLocDBRef.child("sharingLoc").setValue(true);
    }

    private void checkLocationValidity(Location location) {
        if (location.isFromMockProvider() && !SharedPref.getBoolean(getApplicationContext(), "allow_mockloc_provider")) {
            try {
                locationManager.removeUpdates(TransmitLocationService.this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                unregisterReceiver(systemTimeChangedReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            CommonUtils.showNotification(4, Constants.BUS_TRACKING_SERVICENOTIFS_CHANNELID, "Location sharing force-stopped.", "You are not allowed to emulate your GPS location!", getApplicationContext(), new Intent());
            Toast.makeText(getApplicationContext(), "You are not allowed to emulate your GPS location! The service is stopping...", Toast.LENGTH_LONG).show();

            //stop service
            Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
            i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
            i.putExtra("deleteDBValue", true);
            i.putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo);
            startActivity(new Intent(getApplicationContext(), BusRoutesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else startService(i);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (CommonUtils.alerter(this) || suspendFlag) return;
        try {
            if (status == LocationProvider.AVAILABLE) {
                busLocDBRef.child("currentSharerID").setValue(userID);
                busLocDBRef.child("sharingLoc").setValue(true);
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    checkLocationValidity(location);
                    latLongString = location.getLatitude() + "," + location.getLongitude();
                    busLocDBRef.child("latLong").setValue(latLongString);

                    speed = (int) location.getSpeed();
                    busLocDBRef.child("speed").setValue(speed);
                    busLocDBRef.child("sharingLoc").setValue(true);
                }
            } else {
                busLocDBRef.child("sharingLoc").setValue(false);
                busLocDBRef.child("speed").setValue(0);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (!provider.equals(LocationManager.GPS_PROVIDER) || CommonUtils.alerter(getApplicationContext())) {
            suspendFlag = true;
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, prepareForegroundServiceNotification(true, "Sharing your location", "Your location will be used to determine your bus' location.", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
        }
        suspendFlag = false;
        try {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                checkLocationValidity(location);
                latLongString = location.getLatitude() + "," + location.getLongitude();
                busLocDBRef.child("latLong").setValue(latLongString);

                speed = (int) location.getSpeed();
                busLocDBRef.child("speed").setValue(speed);
                busLocDBRef.child("currentSharerID").setValue(userID);
                busLocDBRef.child("sharingLoc").setValue(true);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            busLocDBRef.child("currentSharerID").setValue("null");
            busLocDBRef.child("sharingLoc").setValue(false);
            //busLocDBRef.child("speed").setValue(0);
            suspendFlag = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, prepareForegroundServiceNotification(true, "Unable to use your GPS", "Please turn your GPS on to enable sharing of your location to the database", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(1, prepareForegroundServiceNotification(true, "Unable to use your GPS", "Please turn your GPS on to enable sharing of your location to the database", this, new Intent(this, MapActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo)));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            timer.cancel();
            timer.purge();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            locationManager.removeUpdates(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            unregisterReceiver(systemTimeChangedReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void validateCurrentTime() {
        String currentTime = new SimpleDateFormat("EEE, MMM dd yyyy, HH:mm").format(System.currentTimeMillis() + SharedPref.getLong(getApplicationContext(), "time_offset")).substring(18),
                startTime = SharedPref.getString(getApplicationContext(), "bustracking_starttime"),
                endTime = SharedPref.getString(getApplicationContext(), "bustracking_endtime");

        if (!(currentTime.compareTo(endTime) < 0 && currentTime.compareTo(startTime) > 0)) {
            CommonUtils.showNotification(4, Constants.BUS_TRACKING_GENERALNOTIFS_CHANNELID, "Location sharing force-stopped.", "You have exceeded the time limit allowed to use this feature! Thank you for your services.", getApplicationContext(), new Intent());
            try {
                Toast.makeText(getApplicationContext(), "The current time is: " + currentTime + " hrs. You have exceeded the allowed time limits to use this feature!", Toast.LENGTH_LONG).show();
                startActivity(new Intent(getApplicationContext(), BusRoutesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));

            } catch (Exception e) {
            }
            try {
                timer.cancel();
                timer.purge();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                locationManager.removeUpdates(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                unregisterReceiver(systemTimeChangedReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //stop service
            Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
            i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
            i.putExtra("deleteDBValue", true);
            i.putExtra("routeNo", routeNo == null ? SharedPref.getString(getApplicationContext(), "routeNo") : routeNo);
            startActivity(new Intent(getApplicationContext(), BusRoutesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else startService(i);

        }
    }
}
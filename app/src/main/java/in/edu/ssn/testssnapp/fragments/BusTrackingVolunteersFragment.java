package in.edu.ssn.testssnapp.fragments;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.apache.commons.lang3.StringUtils;

import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.services.TransmitLocationService;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.YesNoDialogBuilder;

import static androidx.core.content.ContextCompat.checkSelfPermission;

public class BusTrackingVolunteersFragment extends Fragment {
    Button cmdStopVolunteering, cmdStartVolunteering;
    EditText etRouteNo;
    String routeNo, userId;
    DatabaseReference busLocRef;
    boolean darkMode;
    volatile boolean isSharingLoc = false;
    private ValueEventListener concurrentVolunteerListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        CommonUtils.addScreen(getContext(), getActivity(), "in.edu.ssn.testssnapp.fragments.BusTrackingVolunteersFragment");
        darkMode = SharedPref.getBoolean(getContext(), "dark_mode");
        View view;
        view = inflater.inflate(darkMode ? R.layout.fragment_bustracking_volunteer : R.layout.fragment_bustracking_volunteer, container, false);
        CommonUtils.initFonts(getContext(), view);
        initUI(view);
        return view;
    }
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    void initUI(View view) {
        cmdStopVolunteering = view.findViewById(R.id.stop);
        cmdStartVolunteering = view.findViewById(R.id.start);
        etRouteNo = view.findViewById(R.id.route);
        userId = SharedPref.getString(getContext(), "email");
        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations");
        etRouteNo.setShowSoftInputOnFocus(true);

        etRouteNo.setOnClickListener(v -> {
            if (!etRouteNo.isEnabled()) return;
            etRouteNo.requestFocus();
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etRouteNo, InputMethodManager.SHOW_IMPLICIT);
        });

        if (SharedPref.getBoolean(getContext(), "stopbutton"))
            if (isMyServiceRunning(TransmitLocationService.class)) disableControls();
            else Toast.makeText(getContext(), "Looks like the application crashed last time. Please hit the 'Stop Volunteering' button to sync yourself with the database!", Toast.LENGTH_LONG).show();
        else
            enableControls();

        concurrentVolunteerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                if (isSharingLoc) {
                    if (s != null && !s.equals("null") && !s.equals(userId)) {
                        Toast.makeText(getContext(), "Location input rejected! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                        stopLocationTransmission();
                    }
                }
                else if (b != null && b) {
                    Toast.makeText(getContext(), "Cannot start location sharing! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                    stopLocationTransmission();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        };
        cmdStopVolunteering.setOnClickListener(v -> YesNoDialogBuilder.createDialog(
                getContext(),
                darkMode,
                "Confirm stopping location sharing?",
                "Your location will stop being shared to the servers.",
                false,
                (dialog, which) -> {
                    SharedPref.putBoolean(getContext(), "stopbutton", false);
                    SharedPref.putString(getContext(), "routeno", "");
                    routeNo = etRouteNo.getText().toString();
                    stopLocationTransmission();
                    busLocRef.child(routeNo).removeValue();
                },
                (dialog, which) -> dialog.dismiss()).show());
        cmdStartVolunteering.setOnClickListener(v -> {
            String routeNoString = etRouteNo.getText().toString().trim();
            if (routeNoString.isEmpty() || !routeExists(routeNoString)) {
                etRouteNo.setError("Invalid route number! Cannot start location sharing!");
                return;
            }
            routeNoString = routeNoString.trim().toUpperCase();
            if (routeNoString.charAt(routeNoString.length() - 1) == 'A')
                routeNoString = Integer.parseInt(routeNoString.substring(0, routeNoString.length() - 1)) + "A";

            BusTrackingVolunteersFragment.this.routeNo = routeNoString;
            busLocRef.child(routeNoString).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                    if (s != null && !s.equals("null") && !s.equals(userId)) {
                        Toast.makeText(getContext(), "Location input rejected! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                        stopLocationTransmission();
                    } else checkForLocationPermissionsAndAvailability();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        });

        if (darkMode) {
            cmdStartVolunteering.setBackgroundColor(getContext().getResources().getColor(R.color.darkColor1));
            cmdStartVolunteering.setTextColor(getContext().getResources().getColor(R.color.colorAccent));
            cmdStopVolunteering.setBackgroundColor(getContext().getResources().getColor(R.color.darkColor1));
            cmdStopVolunteering.setTextColor(getContext().getResources().getColor(R.color.colorAccent));
            etRouteNo.setTextColor(Color.WHITE);
        }
    }

    private boolean routeExists(String route) {
        route = route.toUpperCase();
        if (route.matches("[^a-zA-Z0-9]*")) return false;
        if (route.matches("^[B-Z]*")) return false;
        if (StringUtils.countMatches(route, 'A') > 1) return false;
        try {
            int routeIndex;
            if (route.equals("9A"))
                routeIndex = 10;
            else if (route.equals("30A"))
                routeIndex = 31;
            else routeIndex = Integer.parseInt(route);

            if (routeIndex <= 30) routeIndex++;
            else routeIndex += 2;

            if (routeIndex > Constants.BUS_FLEET_SIZE || routeIndex < 1) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public void enableControls() {
        etRouteNo.setEnabled(true);
        if (darkMode) etRouteNo.setTextColor(Color.WHITE);
        cmdStopVolunteering.setVisibility(View.INVISIBLE);
        cmdStartVolunteering.setVisibility(View.VISIBLE);
    }

    public void disableControls() {
        etRouteNo.setText(SharedPref.getString(getContext(), "routeno"));
        etRouteNo.setEnabled(false);
        if (darkMode)  etRouteNo.setTextColor(Color.GRAY);
        cmdStopVolunteering.setVisibility(View.VISIBLE);
        cmdStartVolunteering.setVisibility(View.INVISIBLE);

    }

    private void startLocationTransmission() {
        SharedPref.putBoolean(getContext(), "stopbutton", true);
        SharedPref.putString(getContext(), "routeno", routeNo);
        busLocRef.child(routeNo).addValueEventListener(concurrentVolunteerListener);
        Intent i = new Intent(getContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_START_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        getActivity().startService(i);
        isSharingLoc = true;
        Toast.makeText(getContext(), "Please wait, it may take some time for the changes to be reflected in the map.", Toast.LENGTH_LONG).show();
        disableControls();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) return;
        if (requestCode == 2) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkIfLocationIsEnabledAndInitializeTransmission();
            }
            else {
                Toast.makeText(getContext(), "Location permission denied! Cannot proceed further!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkForLocationPermissionsAndAvailability() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);

            } else checkIfLocationIsEnabledAndInitializeTransmission();
        }
    }

    private void checkIfLocationIsEnabledAndInitializeTransmission() {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getContext())
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
                            // in onActivityResult().
                            BusTrackingVolunteersFragment.this.startIntentSenderForResult(status.getResolution().getIntentSender(), 1, null, 0, 0, 0, null);
                        } catch (IntentSender.SendIntentException e) {
                            //Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        //Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        Toast.makeText(getContext(), "Unable to get location. Cannot proceed further!", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 1)
            if (resultCode != Activity.RESULT_OK)
                Toast.makeText(getContext(), "Unable to get your location! Cannot start location transmission!", Toast.LENGTH_LONG).show();

            else startLocationTransmission();
    }

    private void stopLocationTransmission() {
        busLocRef.child(routeNo).removeEventListener(concurrentVolunteerListener);
        Intent i = new Intent(getContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("userID", userId);
        getActivity().startService(i);
        enableControls();
        SharedPref.putString(getContext(), "routeno", "");
        SharedPref.putBoolean(getContext(), "stopbutton", false);
        isSharingLoc = false;
    }
}
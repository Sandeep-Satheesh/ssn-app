package in.edu.ssn.testssnapp;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.viewpager.widget.ViewPager;

import com.ogaclejapan.smarttablayout.SmartTabLayout;

import java.util.Objects;

import in.edu.ssn.testssnapp.adapters.ViewPagerAdapter;
import in.edu.ssn.testssnapp.fragments.BusTrackingMapsFragment;
import in.edu.ssn.testssnapp.fragments.BusTrackingVolunteersFragment;
import in.edu.ssn.testssnapp.utils.SharedPref;

public class BusTrackingActivity extends BaseActivity {
    ImageView backIV;
    public ViewPager viewPager;
    String email;
    String volunteer;
    public ProgressDialog pd;
    SmartTabLayout viewPagerTab;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter("in.edu.ssn.testssnapp.LOCSERVICE_LOADING");
        filter.addAction("in.edu.ssn.testssnapp.SHOW_VOLUNTEER_FINISHED_DIAG");
        pd = new ProgressDialog(this);
        pd.setCancelable(false);
        pd.setMessage("Loading...");

        if (darkModeEnabled) {
            setContentView(R.layout.activity_bus_tracking_dark);
            clearLightStatusBar(this);
        } else {
            setContentView(R.layout.activity_bus_tracking);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccent));
        }
        initUI();
        setUpViewPager();

        if (SharedPref.getBoolean(getApplicationContext(), "stopbutton")) {
            viewPager.setCurrentItem(1);
        }
    }

    private void initUI() {
        backIV = findViewById(R.id.backIV);
        backIV.setOnClickListener(v -> {
            onBackPressed();
            finish();
        });
        viewPager = findViewById(R.id.viewPager);
        email = SharedPref.getString(getApplicationContext(),"email");
        volunteer = SharedPref.getString(getApplicationContext(),"student_volunteer", email);
        volunteer = volunteer == null ? "FALSE" : volunteer;
    }

    private void setUpViewPager() {
        viewPagerTab = findViewById(R.id.viewPagerTab);
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new BusTrackingMapsFragment() ,"Track Buses");
        if (volunteer.equals("TRUE")) {
            adapter.addFragment(new BusTrackingVolunteersFragment(), "Volunteers Section");
            viewPager.setAdapter(adapter);
            viewPagerTab.setViewPager(viewPager);
            viewPager.setOffscreenPageLimit(2);
        }else {
            viewPager.setAdapter(adapter);
            viewPagerTab.setVisibility(View.GONE);
        }
    }
}
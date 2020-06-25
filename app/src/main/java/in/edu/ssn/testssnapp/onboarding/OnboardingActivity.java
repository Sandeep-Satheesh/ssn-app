package in.edu.ssn.testssnapp.onboarding;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

import in.edu.ssn.testssnapp.LoginActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.adapters.ViewPagerAdapter;
import in.edu.ssn.testssnapp.utils.SharedPref;
import spencerstudios.com.bungeelib.Bungee;

public class OnboardingActivity extends AppCompatActivity {

    public static boolean firstRun1 = false;
    public static boolean firstRun2 = false;
    public static boolean firstRun3 = false;
    ViewPager viewPager;
    ImageView backgroundIV, backgroundIV1, backgroundIV2;
    CardView signInCV;
    DotsIndicator dotsIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        initUI();

        signInCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPref.putInt(getApplicationContext(), "dont_delete", "is_logged_in", 1);
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                Bungee.slideLeft(OnboardingActivity.this);
                finish();
            }
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int positionOffsetPixels) {
                if (v < 0.70 && i == 0 && !firstRun1) {
                    OneFragment.startAnimation();
                    firstRun1 = true;
                }
                if (v > 0.30 && i == 0 && !firstRun2) {
                    TwoFragment.startAnimation();
                    firstRun2 = true;
                }
                if (v < 0.70 && i == 1 && !firstRun2) {
                    TwoFragment.startAnimation();
                    firstRun2 = true;
                }
                if (v > 0.40 && i == 1 && !firstRun3) {
                    ThreeFragment.startAnimation();
                    firstRun3 = true;
                }

                switch (i) {
                    case 0: {
                        backgroundIV.setRotation(v * 180.0f);
                        backgroundIV.setAlpha(1 - v);
                        backgroundIV.setScaleX(1 - v);
                        backgroundIV.setScaleY(1 - v);

                        backgroundIV1.setRotation(v * 360f);
                        backgroundIV1.setAlpha(v);
                        backgroundIV1.setScaleX(v);
                        backgroundIV1.setScaleY(v);

                        break;
                    }
                    case 1: {
                        backgroundIV1.setRotation(v * 180.0f);
                        backgroundIV1.setAlpha(1 - v);
                        backgroundIV1.setScaleX(1 - v);
                        backgroundIV1.setScaleY(1 - v);

                        backgroundIV2.setRotation(v * 360f);
                        backgroundIV2.setAlpha(v);
                        backgroundIV2.setScaleX(v);
                        backgroundIV2.setScaleY(v);

                        break;
                    }
                    case 2: {
                        backgroundIV2.setRotation(v * 180.0f);
                        backgroundIV2.setAlpha(1 - v);
                        backgroundIV2.setScaleX(1 - v);
                        backgroundIV2.setScaleY(1 - v);

                        break;
                    }
                }
            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 2: {
                        dotsIndicator.setSelectedPointColor(getResources().getColor(R.color.colorAccent));
                        dotsIndicator.animate().scaleY(0).scaleX(0).setDuration(500);
                        signInCV.animate().scaleX(1).scaleY(1).setDuration(500);
                        signInCV.setEnabled(true);
                        break;
                    }

                    default: {
                        dotsIndicator.setSelectedPointColor(getResources().getColor(R.color.colorAccent));
                        dotsIndicator.animate().scaleY(1).scaleX(1).setDuration(500);
                        signInCV.animate().scaleX(0).scaleY(0).setDuration(500);
                        signInCV.setEnabled(false);
                        break;
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    /************************************************************************/
    //Boarding Animations

    void initUI() {
        viewPager = findViewById(R.id.viewPager);
        signInCV = findViewById(R.id.signInCV);

        backgroundIV = findViewById(R.id.backgroundIV);
        backgroundIV1 = findViewById(R.id.backgroundIV1);
        backgroundIV2 = findViewById(R.id.backgroundIV2);
        dotsIndicator = findViewById(R.id.dots_indicator);
        dotsIndicator.setSelectedPointColor(getResources().getColor(R.color.colorAccent));

        Glide.with(this).load(Uri.parse("file:///android_asset/onboarding/bg1_ob.png")).into(backgroundIV);
        Glide.with(this).load(Uri.parse("file:///android_asset/onboarding/bg2_ob.png")).into(backgroundIV1);
        Glide.with(this).load(Uri.parse("file:///android_asset/onboarding/bg3_ob.png")).into(backgroundIV2);

        startAnimation();
        setupViewPager(viewPager);
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new OneFragment(), "OneFragment");
        adapter.addFragment(new TwoFragment(), "TwoFragment");
        adapter.addFragment(new ThreeFragment(), "ThreeFragment");
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);
        dotsIndicator.setViewPager(viewPager);
        dotsIndicator.setDotsClickable(false);
    }

    public void startAnimation() {
        backgroundIV.animate().alpha(1).scaleY(1).scaleX(1).setDuration(600).start();
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(startMain);
        finish();
    }
}
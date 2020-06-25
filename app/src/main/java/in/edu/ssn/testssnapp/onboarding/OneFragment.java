package in.edu.ssn.testssnapp.onboarding;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import in.edu.ssn.testssnapp.R;

public class OneFragment extends Fragment {
    static ImageView towerIV, treesIV;
    static ImageView tempIV;
    static boolean firstRun = false;
    public OneFragment() {
    }

    public static void startAnimation() {
        firstRun = true;
        towerIV.animate()
                .translationY(tempIV.getBaseline())
                .translationX(tempIV.getBaseline())
                .scaleX(1)
                .scaleY(1)
                .alpha(1)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        treesIV.animate()
                .translationY(tempIV.getBaseline())
                .scaleY(1)
                .scaleX(1)
                .alpha(1)
                .setDuration(800)
                .setInterpolator(new DecelerateInterpolator());
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
            }
        }, 0);

    }

    public static void clearAnimation() {
        towerIV.animate().translationX(-60).alpha(0).scaleX((float) 1).scaleY((float) 1).setDuration(1);
        treesIV.animate().translationY(tempIV.getWidth() / 2).alpha(0).scaleY(0).scaleX(0).setDuration(1);
        OnboardingActivity.firstRun1 = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_one, container, false);
        initUI(view);
        startAnimation();
        return view;
    }

    private void initUI(View view) {
        towerIV = view.findViewById(R.id.towerIV);
        treesIV = view.findViewById(R.id.treesIV);
        tempIV = view.findViewById(R.id.tempIV);

        Glide.with(this).load(Uri.parse("file:///android_asset/onboarding/clock_tower_ob.png")).into(towerIV);
        Glide.with(this).load(Uri.parse("file:///android_asset/onboarding/students.png")).into(treesIV);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (!isVisibleToUser) {
            if (firstRun)
                clearAnimation();
        }
    }
}

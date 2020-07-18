package in.edu.ssn.testssnapp.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

public class SystemTimeChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
                if (!CommonUtils.alerter(context))
                    new CommonUtils.getInternetTime(context, internetTime -> {
                        SharedPref.putLong(context, "time_offset", internetTime - System.currentTimeMillis());
                    }).execute();
                    /*long time = SharedPref.getLong(context, "time_offset") + System.currentTimeMillis();
                    String currentTime = new SimpleDateFormat("EEE, MMM dd yyyy, HH:mm:ss").format(time).substring(18);
                    Toast.makeText(context, "TIME CHANGING EVENT CAUGHT!!! THE REAL TIME IS: " + currentTime, Toast.LENGTH_LONG).show();*/
        }
    }

}
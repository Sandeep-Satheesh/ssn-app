package in.edu.ssn.testssnapp.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentSnapshot;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import in.edu.ssn.testssnapp.BuildConfig;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.database.DataBaseHelper;
import in.edu.ssn.testssnapp.models.Club;
import in.edu.ssn.testssnapp.models.ClubPost;
import in.edu.ssn.testssnapp.models.Comments;
import in.edu.ssn.testssnapp.models.Post;

import static com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE;

public class CommonUtils {

    public static Typeface regular, bold, semi_bold;
    private static Boolean is_blocked = false;
    private static Boolean global_chat_is_blocked = false;
    private static Boolean non_ssn_email_is_blocked = false;

    public static void initFonts(Context context, View view) {
        try {
            regular = ResourcesCompat.getFont(context, R.font.open_sans);
            bold = ResourcesCompat.getFont(context, R.font.open_sans_bold);
            semi_bold = ResourcesCompat.getFont(context, R.font.open_sans_semi_bold);

            FontChanger fontChanger = new FontChanger(bold);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /************************************************************************/

    //Hides Keyboard
    public static void hideKeyboard(Activity activity) {
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = activity.getCurrentFocus();
            if (view == null) {
                view = new View(activity);
            }
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Checks for Internet Connectivity
    public static boolean alerter(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return !(activeNetworkInfo != null && activeNetworkInfo.isConnected());
        }
        return false;
    }

    //Unsubscribe to Notification alerts
    //Only for Students login
    public static void UnSubscribeToAlerts(Context context) {
        if (SharedPref.getInt(context, "clearance") != 2)
            FCMHelper.UnSubscribeToTopic(context, Constants.BUS_ALERTS);
        if (SharedPref.getInt(context, "clearance") != 3) {
            FCMHelper.UnSubscribeToTopic(context, Constants.Event);
            FCMHelper.UnSubscribeToTopic(context, Constants.GLOBAL_CHAT);
        }
        if (SharedPref.getInt(context, "clearance") == 0) {
            FCMHelper.UnSubscribeToTopic(context, SharedPref.getString(context, "dept") + SharedPref.getInt(context, "year"));
            FCMHelper.UnSubscribeToTopic(context, SharedPref.getString(context, "dept") + SharedPref.getInt(context, "year") + "exam");
        }
        if (SharedPref.getInt(context, "year") == Integer.parseInt(Constants.fourth))
            FCMHelper.UnSubscribeToTopic(context, SharedPref.getString(context, "dept") + SharedPref.getInt(context, "year") + "place");
    }

    //Checks for current Version Code
    public static String getLatestVersionName(Context context) {
        try {
            String version = Jsoup.connect("https://play.google.com/store/apps/details?id=" + context.getPackageName() + "&hl=en")
                    .timeout(30000)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .get()
                    .select("div.hAyfc:nth-child(4) > span:nth-child(2) > div:nth-child(1) > span:nth-child(1)")
                    .first()
                    .ownText();
            return version;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void openCustomBrowser(Context context, String url) {
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            customTabsIntent.launchUrl(context, Uri.parse(url));
            builder.setToolbarColor(context.getResources().getColor(R.color.colorAccent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Checks for joining year based on year of student.
    public static String getJoiningYear(int year) {
        int cur_year = Calendar.getInstance().get(Calendar.YEAR);
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        int joinYear = 0;
        switch (month) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                joinYear = (cur_year - year);
                break;
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
                joinYear = (cur_year + 1) - year;
                break;
        }
        return String.valueOf(joinYear);
    }

    public static void showWhatsNewDialog(Context context, Boolean darkModeEnabled) {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);

        View dialogView;
        if (darkModeEnabled)
            dialogView = ((Activity) context).getLayoutInflater().inflate(R.layout.whats_new_dialog_dark, null);
        else
            dialogView = ((Activity) context).getLayoutInflater().inflate(R.layout.whats_new_dialog, null);

        dialogBuilder.setView(dialogView);

        TextView versionNameTV = dialogView.findViewById(R.id.versionNameTV);
        TextView changelogTV = dialogView.findViewById(R.id.changelogTV);
        ImageView closeIV = dialogView.findViewById(R.id.closeIV);

        versionNameTV.setText("v" + BuildConfig.VERSION_NAME);
        changelogTV.setText(Constants.changelog);

        final AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        alertDialog.show();

        closeIV.setOnClickListener(v -> alertDialog.dismiss());
    }

    public static int getDayOfWeek(String day) {
        switch (day) {
            case "Mon":
                return 1;
            case "Tue":
                return 2;
            case "Wed":
                return 3;
            case "Thu":
                return 4;
            case "Fri":
                return 5;
            case "Sat":
                return 6;
            default:
                return 7;
        }
    }

    /************************************************************************/
    // checks if wifi is connected to a particular network
    public static boolean checkWifiOnAndConnected(Context context, String ssid) {
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            if (wifiInfo.getNetworkId() == -1)
                return false;   // Not connected to an access point
            else {
                // connected to the required wifi network
                String temp = findSSIDForWifiInfo(wifiMgr, wifiInfo);

                return temp.toLowerCase().equalsIgnoreCase("\"" + ssid + "\"");
            }
        } else
            return false; // Wi-Fi adapter is OFF
    }

    private static String findSSIDForWifiInfo(WifiManager manager, WifiInfo wifiInfo) {
        try {
            List<WifiConfiguration> listOfConfigurations = manager.getConfiguredNetworks();
            for (int index = 0; index < listOfConfigurations.size(); index++) {
                WifiConfiguration configuration = listOfConfigurations.get(index);
                if (configuration.networkId == wifiInfo.getNetworkId()) {
                    return configuration.SSID;
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    /************************************************************************/

    public static String getNameFromEmail(String email) {
        email = email.substring(0, email.indexOf("@"));
        email = email.substring(0, 1).toUpperCase() + email.substring(1);
        email = email.replaceAll("[^A-Za-z]", "");
        return email;
    }

    public static String getTime(Date time) {
        return getTime(System.currentTimeMillis(), time.getTime());
    }

    public static String getTime(long time1, long time2) {
        long t = Math.abs(time1 - time2);
        if (t < 60000)
            return t / 1000 + "s ago";
        else if (t < 3600000)
            return t / 60000 + "m ago";
        else if (t < 86400000)
            return t / 3600000 + "h ago";
        else if (t < 604800000)
            return t / 86400000 + "d ago";
        else if (t < 2592000000L)
            return t / 604800000 + "w ago";
        else if (t < 31536000000L)
            return t / 2592000000L + "M ago";
        else
            return t / 31536000000L + "y ago";
    }

    public static void showNotification(int id, String channelIdString, String title, String message, Context context, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelIdString, "Bus Tracking Status", NotificationManager.IMPORTANCE_HIGH);
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
            n.flags = n.flags | Notification.FLAG_ONLY_ALERT_ONCE;
            notificationManager.notify(id, n);
        }
    }


    public static String getCollectionName(int type) {
        switch (type) {
            case 2:
                return Constants.collection_placement;
            case 3:
                return Constants.collection_club;
            case 4:
                return Constants.collection_post_club;
            case 5:
                return Constants.collection_exam_cell;
            case 6:
                return Constants.collection_event;
            case 7:
                return Constants.collection_post_bus;
            case 8:
                return Constants.COLLECTION_GLOBAL_CHAT;
            default:
                return Constants.collection_post;
        }
    }

    /************************************************************************/
    //Return Object
    public static Post getPostFromSnapshot(Context context, DocumentSnapshot snapshot) {
        Post post = new Post();
        post.setId(snapshot.getString("id"));
        post.setTitle(snapshot.getString("title"));
        post.setDescription(snapshot.getString("description"));
        DocumentSnapshot.ServerTimestampBehavior behavior = ESTIMATE;
        post.setTime(snapshot.getDate("time", behavior));

        ArrayList<String> images = (ArrayList<String>) snapshot.get("img_urls");
        if (images != null && images.size() > 0)
            post.setImageUrl(images);
        else
            post.setImageUrl(new ArrayList<String>());

        try {
            ArrayList<Map<String, String>> files = (ArrayList<Map<String, String>>) snapshot.get("file_urls");
            if (files != null && files.size() != 0) {
                ArrayList<String> fileName = new ArrayList<>();
                ArrayList<String> fileUrl = new ArrayList<>();

                for (int i = 0; i < files.size(); i++) {
                    String name = files.get(i).get("name").trim();
                    name = name.replaceAll("%20", " ");

                    fileName.add(name);
                    fileUrl.add(files.get(i).get("url"));
                }
                post.setFileName(fileName);
                post.setFileUrl(fileUrl);
            } else {
                post.setFileName(new ArrayList<String>());
                post.setFileUrl(new ArrayList<String>());
            }
        } catch (Exception e) {
            //e.printStackTrace();
            post.setFileName(new ArrayList<String>());
            post.setFileUrl(new ArrayList<String>());
        }

        try {
            ArrayList<String> dept = (ArrayList<String>) snapshot.get("dept");
            if (dept != null && dept.size() != 0)
                post.setDept(dept);
            else
                post.setDept(new ArrayList<String>());
        } catch (Exception e) {
            //e.printStackTrace();
            post.setDept(new ArrayList<String>());
        }

        try {
            ArrayList<String> years = new ArrayList<>();
            Map<String, Boolean> year = (HashMap<String, Boolean>) snapshot.get("year");
            TreeMap<String, Boolean> sorted_year = new TreeMap<>(year);
            for (Map.Entry<String, Boolean> entry : sorted_year.entrySet()) {
                if (entry.getValue().booleanValue()) {
                    if (entry.getKey().equals(Constants.fourth)) {
                        years.add("IV");
                    } else if (entry.getKey().equals(Constants.third)) {
                        years.add("III");
                    } else if (entry.getKey().equals(Constants.second)) {
                        years.add("II");
                    } else if (entry.getKey().equals(Constants.first)) {
                        years.add("I");
                    }
                }
            }
            if (years.size() > 1)
                Collections.reverse(years);
            post.setYear(years);
        } catch (Exception e) {
            //e.printStackTrace();
            post.setYear(new ArrayList<String>());
        }

        try {
            String email = snapshot.getString("author");
            post.setAuthor_image_url(email);

            String name = SharedPref.getString(context, "faculty_name", email);
            if (name != null && !name.equals("")) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
                post.setAuthor(name);
            } else if (email != null) {
                email = email.substring(0, 1).toUpperCase() + email.substring(1);
                post.setAuthor(email.split("@")[0]);
            } else
                post.setAuthor("");

            String position = SharedPref.getString(context, "faculty_position", email);
            if (position != null && !position.equals(""))
                post.setPosition(position);
            else
                post.setPosition("Faculty");
        } catch (Exception e) {
            //e.printStackTrace();
            post.setAuthor_image_url("");
            post.setAuthor("");
            post.setPosition("Faculty");
        }
        return post;
    }

    public static Club getClubFromSnapshot(Context context, DocumentSnapshot snapshot) {
        Club club = new Club();
        club.setId(snapshot.getString("id"));
        club.setName(snapshot.getString("name"));
        club.setDp_url(snapshot.getString("dp_url"));
        club.setCover_url(snapshot.getString("cover_url"));
        club.setContact(snapshot.getString("contact"));
        club.setDescription(snapshot.getString("description"));
        try {
            club.setFollowers((ArrayList<String>) snapshot.get("followers"));
        } catch (Exception e) {
            //e.printStackTrace();
            club.setFollowers(null);
        }
        try {
            club.setHead((ArrayList<String>) snapshot.get("head"));
        } catch (Exception e) {
            //e.printStackTrace();
            club.setHead(null);
        }
        return club;
    }

    public static ClubPost getClubPostFromSnapshot(Context context, DocumentSnapshot documentSnapshot) {
        ClubPost post = new ClubPost();
        post.setId(documentSnapshot.getString("id"));
        post.setCid(documentSnapshot.getString("cid"));
        post.setAuthor(documentSnapshot.getString("author"));
        post.setTitle(documentSnapshot.getString("title"));
        post.setDescription(documentSnapshot.getString("description"));
        DocumentSnapshot.ServerTimestampBehavior behavior = ESTIMATE;
        post.setTime(documentSnapshot.getDate("time", behavior));

        ArrayList<String> images = (ArrayList<String>) documentSnapshot.get("img_urls");
        if (images != null && images.size() > 0)
            post.setImg_urls(images);
        else
            post.setImg_urls(new ArrayList<String>());

        try {
            ArrayList<Map<String, String>> files = (ArrayList<Map<String, String>>) documentSnapshot.get("file_urls");
            if (files != null && files.size() != 0) {
                ArrayList<String> fileName = new ArrayList<>();
                ArrayList<String> fileUrl = new ArrayList<>();

                for (int i = 0; i < files.size(); i++) {
                    String name = files.get(i).get("name").trim();
                    name = name.replaceAll("%20", " ");

                    fileName.add(name);
                    fileUrl.add(files.get(i).get("url"));
                }
                post.setFileName(fileName);
                post.setFileUrl(fileUrl);
            } else {
                post.setFileName(new ArrayList<String>());
                post.setFileUrl(new ArrayList<String>());
            }
        } catch (Exception ex) {
            //ex.printStackTrace();
            post.setFileName(new ArrayList<String>());
            post.setFileUrl(new ArrayList<String>());
        }

        try {
            ArrayList<String> like = (ArrayList<String>) documentSnapshot.get("like");
            post.setLike(like);

            if (like != null && like.size() != 0)
                post.setLike(like);
            else
                post.setLike(new ArrayList<String>());
        } catch (Exception ex) {
            //ex.printStackTrace();
            post.setLike(new ArrayList<String>());
        }

        try {
            ArrayList<Comments> comments = (ArrayList<Comments>) documentSnapshot.get("comment");
            if (comments != null && comments.size() > 0)
                post.setComment(comments);
            else
                post.setComment(new ArrayList<Comments>());
        } catch (Exception ex) {
            //ex.printStackTrace();
            post.setComment(new ArrayList<Comments>());
        }

        return post;
    }

    /************************************************************************/

    public static void handleBottomSheet(View v, final Post post, final int type, final Context context) {
        RelativeLayout ll_save, ll_share;
        final TextView saveTV;

        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View sheetView;
        if (SharedPref.getBoolean(context, "dark_mode")) {
            sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_menu_dark, null);
        } else {
            sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_menu, null);
        }

        bottomSheetDialog.setContentView(sheetView);

        ll_save = sheetView.findViewById(R.id.saveLL);
        ll_share = sheetView.findViewById(R.id.shareLL);
        saveTV = sheetView.findViewById(R.id.saveTV);

        final DataBaseHelper dataBaseHelper = DataBaseHelper.getInstance(context);
        if (dataBaseHelper.checkPost(post.getId()))
            saveTV.setText("Remove from Favourites");
        else
            saveTV.setText("Add to Favourites");

        bottomSheetDialog.show();

        ll_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dataBaseHelper.checkPost(post.getId())) {
                    dataBaseHelper.deletePost(post.getId());
                    saveTV.setText("Add to Favourites");
                } else {
                    saveTV.setText("Remove from Favourites");
                    dataBaseHelper.addPost(post, Integer.toString(type));
                }
                bottomSheetDialog.hide();
            }
        });

        ll_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareBody = "Hi all! New posts from " + post.getAuthor().trim() + ". Check it out: https://ssnportal.netlify.app/share.html?type=" + type + "&vca=" + post.getId();
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                context.startActivity(Intent.createChooser(sharingIntent, "Share via"));
            }
        });
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /************************************************************************/

    public static Boolean getIs_blocked() {
        return is_blocked;
    }

    public static void setIs_blocked(Boolean is_blocked) {
        CommonUtils.is_blocked = is_blocked;
    }

    public static Boolean getGlobal_chat_is_blocked() {
        return global_chat_is_blocked;
    }

    public static void setGlobal_chat_is_blocked(Boolean is_blocked) {
        CommonUtils.global_chat_is_blocked = is_blocked;
    }

    public static Boolean getNon_ssn_email_is_blocked() {
        return non_ssn_email_is_blocked;
    }

    public static void setNon_ssn_email_is_blocked(Boolean is_blocked) {
        CommonUtils.non_ssn_email_is_blocked = is_blocked;
    }

    public static void isDebug() {
        if (Constants.debug_mode) {
            Constants.collection_exam_cell = "debug_examcell";
            Constants.collection_placement = "debug_placement";
            Constants.collection_post = "debug_post";
            Constants.collection_post_bus = "debug_post_bus";
            Constants.collection_post_club = "debug_post_club";
            Constants.collection_event = "debug_event";
            Constants.COLLECTION_GLOBAL_CHAT = "debug_global_chat";
            Constants.GLOBAL_CHAT = "debug_global_chat";
        } else {
            Constants.collection_exam_cell = "examcell";
            Constants.collection_placement = "placement";
            Constants.collection_post = "post";
            Constants.collection_post_bus = "post_bus";
            Constants.collection_post_club = "post_club";
            Constants.collection_event = "event";
            Constants.COLLECTION_GLOBAL_CHAT = "global_chat";
            Constants.GLOBAL_CHAT = "global_chat";
        }
    }

    /************************************************************************/

    // utils functions for firebase analytics
    public static void addUserProperty(Context context, String propertyName, String propertyValue) {

        try {
            FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(context);
            analytics.setUserProperty(propertyName, propertyValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addScreen(Context context, Activity activity, String fragmentName) {
        try {
            FirebaseAnalytics.getInstance(context).setCurrentScreen(activity, fragmentName, fragmentName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class getInternetTime extends AsyncTask<Void, Void, Void> {
        OnTimeFetchedListener listener;
        Context c;
        long internetTime = 0;
        ProgressDialog pd;

        public getInternetTime(Context c, OnTimeFetchedListener listener) {
            pd = SharedPref.getBoolean(c, "dark_mode") ? new ProgressDialog(c, R.style.DarkThemeDialog) : new ProgressDialog(c);
            pd.setMessage("Attempting to fetch internet time...");
            pd.setCancelable(false);
            this.c = c;
            this.listener = listener;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd.show();
        }

        @SafeVarargs
        @Override
        protected final Void doInBackground(Void... voids) {
            try {
                internetTime = Timestamp.now().toDate().getTime();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (internetTime == 0)
                pd.setMessage("There was a problem fetching the current time! Stopping...");
            if (listener != null) listener.onTimeFetched(internetTime);
            pd.cancel();
        }

        public interface OnTimeFetchedListener {
            void onTimeFetched(long internetTime);
        }
    }

    public static boolean isMyServiceRunning(Context c, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
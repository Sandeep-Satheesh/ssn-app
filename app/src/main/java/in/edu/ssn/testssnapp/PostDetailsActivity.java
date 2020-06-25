package in.edu.ssn.testssnapp;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.ViewPager;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
//import com.crashlytics.android.Crashlytics;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.hendraanggrian.appcompat.widget.SocialTextView;
import com.hendraanggrian.appcompat.widget.SocialView;

import java.util.ArrayList;

import in.edu.ssn.testssnapp.adapters.ImageAdapter;
import in.edu.ssn.testssnapp.database.DataBaseHelper;
import in.edu.ssn.testssnapp.models.Post;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import spencerstudios.com.bungeelib.Bungee;

public class PostDetailsActivity extends BaseActivity {

    final static String TAG = "PostDetails";
    Post post;
    ImageView backIV, userImageIV, shareIV, bookmarkIV;
    ViewPager imageViewPager;
    TextView authorTV, positionTV, timeTV, titleTV, current_imageTV, attachmentsTV;
    SocialTextView descriptionTV;
    ChipGroup attachmentsChipGroup, yearChipGroup, deptChipGroup;
    RelativeLayout textGroupRL, layout_receive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (darkModeEnabled) {
            setContentView(R.layout.activity_post_details_dark);
            clearLightStatusBar(this);
        } else {
            setContentView(R.layout.activity_post_details);
        }

        post = getIntent().getParcelableExtra("post");
        final int type = getIntent().getIntExtra("type", 0);

        initUI();

        titleTV.setText(post.getTitle().trim());
        descriptionTV.setText(post.getDescription().trim());
        authorTV.setText(post.getAuthor().trim());
        positionTV.setText(post.getPosition().trim());
        timeTV.setText(CommonUtils.getTime(post.getTime()));

        try {
            final TextDrawable.IBuilder builder = TextDrawable.builder()
                    .beginConfig()
                    .toUpperCase()
                    .endConfig()
                    .round();
            ColorGenerator generator = ColorGenerator.MATERIAL;
            int color = generator.getColor(post.getAuthor_image_url());
            TextDrawable ic1 = builder.build(String.valueOf(post.getAuthor().charAt(0)), color);
            userImageIV.setImageDrawable(ic1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (post.getImageUrl() != null && post.getImageUrl().size() != 0) {
            imageViewPager.setVisibility(View.VISIBLE);
            current_imageTV.setVisibility(View.VISIBLE);
            final ImageAdapter imageAdapter = new ImageAdapter(PostDetailsActivity.this, post.getImageUrl(), 0);
            imageViewPager.setAdapter(imageAdapter);

            if (post.getImageUrl().size() == 1)
                current_imageTV.setVisibility(View.GONE);
            else {
                current_imageTV.setVisibility(View.VISIBLE);
                current_imageTV.setText(1 + " / " + post.getImageUrl().size());
                imageViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                    }

                    @Override
                    public void onPageSelected(int pos) {
                        current_imageTV.setText((pos + 1) + " / " + post.getImageUrl().size());
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {

                    }
                });
            }
        } else {
            imageViewPager.setVisibility(View.GONE);
            current_imageTV.setVisibility(View.GONE);
        }

        ArrayList<String> fileName = post.getFileName();
        ArrayList<String> fileUrl = post.getFileUrl();

        if (fileName != null && fileName.size() > 0) {
            attachmentsTV.setVisibility(View.VISIBLE);
            attachmentsChipGroup.setVisibility(View.VISIBLE);

            for (int i = 0; i < fileName.size(); i++) {
                Chip chip = getFilesChip(attachmentsChipGroup, fileName.get(i), fileUrl.get(i));
                attachmentsChipGroup.addView(chip);
            }
        } else {
            attachmentsTV.setVisibility(View.GONE);
            attachmentsChipGroup.setVisibility(View.GONE);
        }

        /*List<String> depts = post.getDept();
        List<String> year = post.getYear();

        if(SharedPref.getInt(getApplicationContext(),"clearance") == 3){
            if(depts != null && depts.size() != 0){
                layout_receive.setVisibility(View.VISIBLE);

                for(int i=0; i<depts.size(); i++){
                    String dept = depts.get(i).toUpperCase();
                    if(dept.length()>3)
                        dept = dept.substring(0,3);

                    Chip dept_chip = getDataChip(deptChipGroup, dept);
                    deptChipGroup.addView(dept_chip);
                }

                for(int i=0; i<year.size(); i++){
                    Chip year_chip = getDataChip(yearChipGroup, year.get(i));
                    yearChipGroup.addView(year_chip);
                }
            }
            else
                layout_receive.setVisibility(View.GONE);
        }
        else
            layout_receive.setVisibility(View.GONE);*/

        backIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        descriptionTV.setOnHyperlinkClickListener(new SocialView.OnClickListener() {
            @Override
            public void onClick(@NonNull SocialView view, @NonNull CharSequence text) {
                String url = text.toString();
                if (!url.startsWith("http") && !url.startsWith("https")) {
                    url = "http://" + url;
                }
                CommonUtils.openCustomBrowser(getApplicationContext(), url);
            }
        });

        shareIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareBody = "Hi all! New posts from " + post.getAuthor().trim() + ". Check it out: https://ssnportal.netlify.app/share.html?type=" + type + "&vca=" + post.getId();
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                startActivity(Intent.createChooser(sharingIntent, "Share via"));
            }
        });

        bookmarkIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSavedPost(post))
                    unSavePost(post);
                else
                    savePost(post, Integer.toString(type));
            }
        });
    }

    void initUI() {
        backIV = findViewById(R.id.backIV);
        userImageIV = findViewById(R.id.userImageIV);
        authorTV = findViewById(R.id.authorTV);
        positionTV = findViewById(R.id.positionTV);
        timeTV = findViewById(R.id.timeTV);
        titleTV = findViewById(R.id.titleTV);
        descriptionTV = findViewById(R.id.descriptionTV);
        shareIV = findViewById(R.id.shareIV);
        current_imageTV = findViewById(R.id.currentImageTV);
        attachmentsTV = findViewById(R.id.attachmentTV);
        imageViewPager = findViewById(R.id.viewPager);
        attachmentsChipGroup = findViewById(R.id.attachmentsGroup);
        yearChipGroup = findViewById(R.id.yearGroup);
        deptChipGroup = findViewById(R.id.deptGroup);
        textGroupRL = findViewById(R.id.textGroupRL);
        layout_receive = findViewById(R.id.layout_receive);
        textGroupRL.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        bookmarkIV = findViewById(R.id.bookmarkIV);

        try {
            if (checkSavedPost(post))
                bookmarkIV.setImageResource(R.drawable.ic_bookmark_saved);
            else
                bookmarkIV.setImageResource(R.drawable.ic_bookmark_unsaved);
        } catch (Exception e) {
            bookmarkIV.setImageResource(R.drawable.ic_bookmark_unsaved);
        }
    }

    /*****************************************************************/
    //Files
    private Chip getFilesChip(final ChipGroup entryChipGroup, final String file_name, final String url) {
        final Chip chip = new Chip(this);
        chip.setChipDrawable(ChipDrawable.createFromResource(this, R.xml.file_name_chip));
        chip.setText(file_name);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CommonUtils.hasPermissions(PostDetailsActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    ActivityCompat.requestPermissions(PostDetailsActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    Toast toast = Toast.makeText(PostDetailsActivity.this, "Downloading...", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    try {
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        Uri downloadUri = Uri.parse(url);
                        DownloadManager.Request request = new DownloadManager.Request(downloadUri);

                        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file_name)
                                .setTitle(file_name)
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                        dm.enqueue(request);
                    } catch (Exception ex) {
                        toast = Toast.makeText(getApplicationContext(), "Download failed!", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        ex.printStackTrace();
                        //Crashlytics.log("stackTrace: " + ex.getStackTrace() + " \n Error: " + ex.getMessage());
                    }

                }


            }
        });

        return chip;
    }

    /*****************************************************************/
    //Year & Dept
    private Chip getDataChip(final ChipGroup entryChipGroup, final String data) {
        final Chip chip = new Chip(this);
        chip.setChipDrawable(ChipDrawable.createFromResource(this, R.xml.year_chip));
        chip.setChipCornerRadius(30f);
        chip.setText(data);
        return chip;
    }

    public Boolean savePost(Post post, String type) {
        try {
            DataBaseHelper dataBaseHelper = DataBaseHelper.getInstance(this);
            dataBaseHelper.addPost(post, type);
            bookmarkIV.setImageResource(R.drawable.ic_bookmark_saved);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        Toast toast = Toast.makeText(this, "Post saved!", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
        return true;
    }

    void unSavePost(Post post) {
        // updating the post status to not saved in shared preference

        DataBaseHelper dataBaseHelper = DataBaseHelper.getInstance(this);
        Log.d(TAG, "post type : " + dataBaseHelper.getPostType(post.getId()));
        dataBaseHelper.deletePost(post.getId());
        bookmarkIV.setImageResource(R.drawable.ic_bookmark_unsaved);

        Toast toast = Toast.makeText(this, "Post unsaved!", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    Boolean checkSavedPost(Post post) {
        DataBaseHelper dataBaseHelper = DataBaseHelper.getInstance(this);
        return dataBaseHelper.checkPost(post.getId());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Bungee.slideRight(PostDetailsActivity.this);
    }
}

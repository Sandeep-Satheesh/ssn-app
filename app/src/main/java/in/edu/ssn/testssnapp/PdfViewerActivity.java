package in.edu.ssn.testssnapp;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.ViewPager;

//import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.Arrays;

import es.voghdev.pdfviewpager.library.PDFViewPager;
import es.voghdev.pdfviewpager.library.RemotePDFViewPager;
import es.voghdev.pdfviewpager.library.adapter.PDFPagerAdapter;
import es.voghdev.pdfviewpager.library.remote.DownloadFile;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import pl.droidsonroids.gif.GifImageView;
import spencerstudios.com.bungeelib.Bungee;

public class PdfViewerActivity extends BaseActivity implements DownloadFile.Listener {

    private static final String TAG = "PdfViewerActivity";
    String url;

    TextView pageNumberTV;
    RemotePDFViewPager remotePDFViewPager;
    PDFViewPager pdfViewPager;
    PDFPagerAdapter adapter;

    ImageView backIV, downloadIV;
    GifImageView progress;
    Uri downloadUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        initUI();

        Intent intent = getIntent();
        url = intent.getStringExtra(Constants.PDF_URL);

        backIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        downloadUri = Uri.parse(url);
        downloadIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!CommonUtils.hasPermissions(PdfViewerActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    ActivityCompat.requestPermissions(PdfViewerActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    downloadFile();
                }
            }
        });

        setupPdf();
    }

    void initUI() {
        pageNumberTV = findViewById(R.id.pageNumberTV);
        pdfViewPager = findViewById(R.id.pdfViewPager);
        backIV = findViewById(R.id.backIV);
        downloadIV = findViewById(R.id.downloadIV);
        progress = findViewById(R.id.progress);
    }

    private void downloadFile(){
        try {
            Toast toast = Toast.makeText(PdfViewerActivity.this, "Downloading...", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);

            File file = new File(downloadUri.getPath());
            String names = file.getName();

            //Handle syllabus from drive link
            if (names.equals("uc"))
                names = "syllabus.pdf";

            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, names)
                    .setTitle(names).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            dm.enqueue(request);
        } catch (Exception ex) {
            Toast toast = Toast.makeText(getApplicationContext(), "Download failed!", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            ex.printStackTrace();
            //Crashlytics.log("stackTrace: " + Arrays.toString(ex.getStackTrace()) + " \n Error: " + ex.getMessage());
        }
    }

    private void setupPdf() {
        remotePDFViewPager = new RemotePDFViewPager(PdfViewerActivity.this, url, this);
        pdfViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int i) {
                pageNumberTV.setText(i + 1 + "/" + adapter.getCount());
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });
    }

    @Override
    public void onSuccess(String url, String destinationPath) {
        adapter = new PDFPagerAdapter(this, destinationPath);
        pageNumberTV.setText("1/" + adapter.getCount());
        pdfViewPager.setAdapter(adapter);
        progress.setVisibility(View.GONE);
    }

    @Override
    public void onFailure(Exception e) {
        progress.setVisibility(View.GONE);
        Log.d(TAG, e.getMessage());
        Toast.makeText(getApplicationContext(), "Error occurred opening the file", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProgressUpdate(int progress, int total) {
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Bungee.slideRight(PdfViewerActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length>0&& grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode==1){
            downloadFile();
        }
    }
}

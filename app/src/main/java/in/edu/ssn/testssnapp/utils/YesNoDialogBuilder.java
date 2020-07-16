package in.edu.ssn.testssnapp.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;

import in.edu.ssn.testssnapp.R;

public class YesNoDialogBuilder extends AlertDialog {
    protected YesNoDialogBuilder(Context context) {
        super(context);
    }
    public static AlertDialog createDialog (Context c, boolean darkMode, String title, String message, boolean cancelable,
                                           OnClickListener yesListener, OnClickListener noListener) {
        AlertDialog.Builder builder = darkMode ? new AlertDialog.Builder(c, R.style.DarkThemeDialog) : new AlertDialog.Builder(c);
        AlertDialog d = builder
                .setTitle(title)
                .setCancelable(cancelable)
                .setNegativeButton("NO", noListener)
                .setPositiveButton("YES", yesListener)
                .setMessage(message)
                .create();
        d.setOnShowListener(dialog -> {
            if (darkMode) {
                d.getButton(BUTTON_POSITIVE).setBackgroundColor(c.getResources().getColor(R.color.darkColor1));
                d.getButton(BUTTON_NEGATIVE).setBackgroundColor(c.getResources().getColor(R.color.darkColor1));
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.YELLOW);
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.YELLOW);
            } else {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#5683AD"));
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#5683AD"));
            }
        });
        return d;
    }
}

package in.edu.ssn.testssnapp.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;

import in.edu.ssn.testssnapp.R;

public class OKOnlyDialogBuilder extends AlertDialog {
    protected OKOnlyDialogBuilder(Context context) {
        super(context);
    }

    public static AlertDialog createDialog(Context c, boolean darkMode, String title, String message, boolean cancelable,
                                           OnClickListener okListener) {
        Builder builder = darkMode ? new Builder(c, R.style.DarkThemeDialog) : new Builder(c);
        AlertDialog d = builder
                .setTitle(title)
                .setCancelable(cancelable)
                .setPositiveButton("OK", okListener)
                .setMessage(message)
                .create();
        d.setOnShowListener(dialog -> {
            if (darkMode) {
                d.getButton(BUTTON_POSITIVE).setBackgroundColor(c.getResources().getColor(R.color.darkColor1));
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            } else
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(c.getResources().getColor(R.color.colorAccent));
        });
        return d;
    }
}

package in.edu.ssn.testssnapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class DividerView extends View {
    static public int ORIENTATION_HORIZONTAL = 0;
    static public int ORIENTATION_VERTICAL = 1;
    static public int PROGRESS_STOP_CROSSED = -1;
    static public int PROGRESS_STOP_IS_NEXT = -2;
    static public int PROGRESS_STOP_IS_NOT_NEXT = -3;

    private Paint mPaint;
    private int orientation, progressMode = PROGRESS_STOP_IS_NOT_NEXT;

    public void setDashColor(int color) {
        mPaint.setColor(color);
    }
    public void setProgressMode(int progressMode) {
        this.progressMode = progressMode;
    }
    public DividerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int dashGap, dashLength, dashThickness;
        //int color;

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DividerView, 0, 0);

        try {
            dashGap = a.getDimensionPixelSize(R.styleable.DividerView_dashGap, 5);
            dashLength = a.getDimensionPixelSize(R.styleable.DividerView_dashLength, 5);
            dashThickness = a.getDimensionPixelSize(R.styleable.DividerView_dashThickness, 3);
            //color = a.getColor(R.styleable.DividerView_color, 0xff000000);
            orientation = a.getInt(R.styleable.DividerView_orientation, ORIENTATION_HORIZONTAL);
        } finally {
            a.recycle();
        }

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dashThickness);
        mPaint.setPathEffect(new DashPathEffect(new float[] { dashLength, dashGap, }, 0));
    }

    public DividerView(Context context) {
        this(context, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (progressMode == PROGRESS_STOP_CROSSED) mPaint.setColor(Color.GREEN);
        else if (progressMode == PROGRESS_STOP_IS_NEXT) {
            if (orientation == ORIENTATION_HORIZONTAL) {
                float center = getHeight() * .5f;
                mPaint.setColor(Color.GREEN);
                canvas.drawLine(0, center, getWidth() / 2f, center, mPaint);
                mPaint.setColor(Color.RED);
                canvas.drawLine(getWidth() / 2f, center, getWidth(), center, mPaint);

            } else {
                float center = getWidth() * .5f;
                mPaint.setColor(Color.GREEN);
                canvas.drawLine(center, 0, center, getHeight() / 2f, mPaint);
                mPaint.setColor(Color.RED);
                canvas.drawLine(center, getHeight() / 2f, center, getHeight(), mPaint);
            }
            return;
        }
        else if (progressMode == PROGRESS_STOP_IS_NOT_NEXT) mPaint.setColor(Color.RED);
        if (orientation == ORIENTATION_HORIZONTAL) {
            float center = getHeight() * .5f;
            canvas.drawLine(0, center, getWidth(), center, mPaint);
        }
        else {
            float center = getWidth() * .5f;
            canvas.drawLine(center, 0, center, getHeight(), mPaint);
        }
    }
}
package com.htf.drdshsdklibrary.Utills;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.view.ViewCompat;

public class RoundedImage extends androidx.appcompat.widget.AppCompatImageView {
    private Path mMaskPath;
    private final Paint mMaskPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mCornerRadius = 80;

    public RoundedImage(Context context) {
        super(context);

        init(context);
    }

    public RoundedImage(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        init(context);
    }

    public RoundedImage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }


    private void init(Context context) {
        ViewCompat.setLayerType(this, View.LAYER_TYPE_SOFTWARE, null);
        mMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mMaskPaint.setColor(context.getResources().getColor(android.R.color.transparent));

        mCornerRadius = 10;
    }

    /**
     * Set the corner radius to use for the RoundedRectangle.
     */
    public void setCornerRadius(int cornerRadius) {
        mCornerRadius = cornerRadius;
        generateMaskPath(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        if (w != oldW || h != oldH) {
            generateMaskPath(w, h);
        }
    }

    private void generateMaskPath(int w, int h) {
        mMaskPath = new Path();
        mMaskPath.addRoundRect(new RectF(0,0,w,h), mCornerRadius, mCornerRadius, Path.Direction.CW);
        mMaskPath.setFillType(Path.FillType.INVERSE_WINDING);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(canvas.isOpaque()) { // If canvas is opaque, make it transparent
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }

        super.onDraw(canvas);

        if(mMaskPath != null) {
            canvas.drawPath(mMaskPath, mMaskPaint);
        }
    }
}
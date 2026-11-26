package com.github.mikeshevchenko.sinclair;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class SquareTextureView extends TextureView {
    public SquareTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int size = widthMode == MeasureSpec.UNSPECIFIED ? height : width;
        setMeasuredDimension(size, size);
    }
}

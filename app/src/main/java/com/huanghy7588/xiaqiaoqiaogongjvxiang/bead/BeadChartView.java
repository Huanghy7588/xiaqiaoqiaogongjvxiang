package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 网格预览视图：双指缩放 1x–8x、单指拖动、双击在 1x/2.5x 间切换。
 * 复用 BeadDraw.drawChart 绘制色块 + 色号，深色格自动白字。
 */
public class BeadChartView extends View {

    private BeadColor[][] grid;
    private BeadDraw.DrawOptions opt = new BeadDraw.DrawOptions();

    private float scale = 1f;
    private float tx = 0f;
    private float ty = 0f;
    private float baseCell = 20f;
    private int viewW, viewH;

    private final float MIN_SCALE = 1f;
    private final float MAX_SCALE = 8f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public BeadChartView(Context context) {
        super(context);
        init(context);
    }

    public BeadChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
                clamp();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx += dx;
                ty += dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                scale = scale > 2f ? 1f : 2.5f;
                clamp();
                invalidate();
                return true;
            }
        });
    }

    public void setGrid(BeadColor[][] g, BeadDraw.DrawOptions o) {
        this.grid = g;
        if (o != null) this.opt = o;
        if (viewW > 0) {
            computeBase();
        }
        invalidate();
    }

    private float padPx() {
        return viewW * 0.06f;
    }

    private void computeBase() {
        if (grid == null || grid.length == 0) {
            baseCell = 20f;
            return;
        }
        int gw = grid[0].length;
        float avail = viewW - padPx() * 2;
        baseCell = Math.max(1f, avail / gw);
        scale = 1f;
        tx = 0;
        ty = 0;
        clamp();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        computeBase();
    }

    private void clamp() {
        if (grid == null) return;
        int gw = grid[0].length;
        int gh = grid.length;
        float cell = baseCell * scale;
        float contentW = gw * cell + padPx() * 2;
        float contentH = gh * cell + padPx() * 2;
        if (contentW <= viewW) tx = (viewW - contentW) / 2f;
        else tx = Math.max(viewW - contentW, Math.min(tx, 0f));
        if (contentH <= viewH) ty = (viewH - contentH) / 2f;
        else ty = Math.max(viewH - contentH, Math.min(ty, 0f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        if (grid == null || grid.length == 0) return;
        int gw = grid[0].length;
        int gh = grid.length;
        float cell = baseCell * scale;
        float ox = padPx() + tx;
        float oy = padPx() + ty;
        BeadDraw.drawChart(canvas, grid, opt, ox, oy, cell, 0, 0, gh, gw, true);
    }
}

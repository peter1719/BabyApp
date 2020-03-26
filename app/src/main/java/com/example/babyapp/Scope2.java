package com.example.babyapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import java.util.ArrayList;


public class Scope2 extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    //Thread t = null;
    SurfaceHolder holder;
    boolean isItOK = false;
    private Canvas c;
    private Paint linePaint;
    private Paint textPaint;
    private ArrayList<Float> bufferSave;
    private int bufferSize = 500;
    private int indexNow = 0;
    private float[] bufferPrintX;
    private float[] bufferPrintY;
    //private boolean[][] label;
    //private boolean emptyFlag;

    private float offsetY = 500;
    private float offsetX = 0;
    private float shiftX = 1;
    private float startY;
    private float painWidth = 5f;

    public void setDrawValue(int totalBuffer, float pWidth) {
        bufferSize = totalBuffer;
        painWidth = pWidth;
        init(null);
    }

    public Scope2(Context context) {
        super(context);
        init(null);
    }

    public Scope2(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init(attributeSet);
    }

    private void init(@Nullable AttributeSet set) {
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setKeepScreenOn(true);

        offsetY = this.getHeight() / 2;
        shiftX = this.getWidth() / bufferSize;
        bufferPrintX = new float[bufferSize];
        bufferPrintY = new float[bufferSize];
        //label = new boolean[3][bufferSize];
        bufferSave = new ArrayList<>();
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLUE);
        linePaint.setStrokeWidth(painWidth);


        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(60f);
//        for (int i = 0; i < bufferSize; i++) {
//            bufferPrintX[i] = 0;
//            bufferPrintY[i] = 0;
//            for (int j = 0; j < 3 ; j++) {
//                label[j][i] = false;
//            }
//        }
    }


    private float textData = 0;

    public void addNewData(float newData) {
        //Log.d("Scop","Data get");
        bufferPrintX[indexNow] = shiftX * indexNow;
        bufferPrintY[indexNow] = 200 - newData;
        textData = newData;
        ++indexNow;
        //postInvalidate();
        if (indexNow == bufferSize)
            indexNow = 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startY = event.getY();
            return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            offsetY += (event.getY() - startY);
            startY = event.getY();
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void reset() {
        offsetX = 0;
        offsetY = this.getHeight() / 2;
        shiftX = ((float) this.getWidth()) / bufferSize;
    }

    @Override
    public void run() {
        while (isItOK) {
            //draw hear
            if (!holder.getSurface().isValid()) {
                continue;
            }
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLUE);
            textPaint.setTextSize(60f);
            try {
                c = holder.lockCanvas();
                c.drawARGB(255, 255, 255, 255);
                for (int i = 0; i < indexNow - 1; i++) {
                    c.drawLine(bufferPrintX[i] + offsetX, bufferPrintY[i] + offsetY
                        , bufferPrintX[i + 1] + offsetX, bufferPrintY[i + 1] + offsetY
                        , linePaint);
                }
                c.drawText(String.format("%.2f", textData), 2, 70, textPaint);
            }catch (Exception e){e.printStackTrace();}
            finally {
                if(c != null)
                    holder.unlockCanvasAndPost(c);
            }
            //Log.d("Scop","i'm running");
        }
    }



    public void pause() {
        isItOK = false;
    }

    public void resume() {
        reset();
        isItOK = true;
        new Thread(this).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isItOK = true;
        new Thread(this).start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isItOK = false;
    }
}

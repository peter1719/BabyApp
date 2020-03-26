package com.example.babyapp;

import android.util.Log;

import static java.lang.Math.atan2;

public class DSP {
    private int axo, ayo, azo, gxo, gyo, gzo = 0; // 測量誤差;
    private float ax, ay, az, gx, gy, gz = 0; // 測量數值;
    private int count = 0;
    final private int calibrate_num = 100;
    private int average_gravity = 0;
    private int arraySize = 3;
    private int arrayEnd = 0;
    private long lastTime = 0;
    private float dt = 0.01f;
    final private float degree = 131; // 1 dgree = 131
    private float angle_pitch, angle_roll = 0;//  仰角/傾角
    private float angle_pitch_buff, angle_roll_buff = 0;// z軸偏差補償
    private float angle_pitch_acc, angle_roll_acc = 0;//加速度計算出的角度
    private float ax_last, ay_last, az_last = 0;
    private float[] medinArray;
    private final int forceNormal = 200;
    public final float[] coffee = {-0.0005f, -0.0011f, -0.0016f, -0.0019f, -0.0016f, -0.0004f, 0.0016f, 0.0042f,
        0.0067f, 0.008f, 0.0071f, 0.0031f, -0.0038f, -0.0124f, -0.0207f, -0.0256f, -0.0242f,
        -0.014f, 0.0058f, 0.0342f, 0.0681f, 0.1027f, 0.1328f, 0.1532f, 0.1604f, 0.1532f, 0.1328f,
        0.1027f, 0.0681f, 0.0342f, 0.0058f, -0.014f, -0.0242f, -0.0256f, -0.0207f, -0.0124f,
        -0.0038f, 0.0031f, 0.0071f, 0.008f, 0.0067f, 0.0042f, 0.0016f, -0.0004f, -0.0016f,
        -0.0019f, -0.0016f, -0.0011f, -0.0005f};

    public DSP() {
        lastTime = System.currentTimeMillis();
        medinArray = new float[arraySize];
        for (int i = 0; i < arraySize; i++)
            medinArray[i] = 0f;

    }

    private boolean Calibrate() {
        if (count > calibrate_num) {
            //加速度校正
            ax -= axo;
            ay -= ayo;
            az -= azo;
            ax /= average_gravity;
            ay /= average_gravity;
            az /= average_gravity;
            az += 1f;
            //角度校正
            gx -= gxo;
            gy -= gyo;
            gz -= gzo;
            return true;
        } else if (count == calibrate_num)//計算偏差
        {
            axo /= calibrate_num;
            ayo /= calibrate_num;
            azo /= calibrate_num;
            gxo /= calibrate_num;
            gyo /= calibrate_num;
            gzo /= calibrate_num;
            average_gravity /= calibrate_num;
            average_gravity *= 0.8;
        } else//計算和
        {
            average_gravity += Math.sqrt(Math.pow(ax, 2) + Math.pow(ay, 2) + Math.pow(az, 2));
//            //calibration
            gxo += gx;
            gyo += gy;
            gzo += gz;
            axo += ax;
            ayo += ay;
            azo += az;
        }
        count++;
        return false;
    }

    public float[] Force(float[] input) {
        ax = input[0];
        ay = input[1];
        az = input[2];
        gx = input[3];
        gy = input[4];
        gz = input[5];
        if (Calibrate()) {
//            Log.d("MainActivity","ax: " + ax + "  ,ay: " + ay+
//                    "  ,az: " + az + "\ngx: " + gx +
//                    " ,gy: " + gy + "  ,gz: " + gz);
            float force = median((float) Math.sqrt(Math.pow(ax, 2) + Math.pow(ay, 2) + Math.pow(az, 2)));
            force *= forceNormal;
            computeAngle();
            stepCounter(force);
            Ruminating_time(force);
            eat_time();
            // 0: step  1: eat_time 2: Ruminating
            return new float[]{force, angle_pitch, moveCount[0], moveCount[1], moveCount[2], Ruminating_counter};
        } else {
            //Log.d("MainActivity","0,0");
            return new float[]{0, 0, 0, 0, 0, 0};
        }
    }

    private float median(float input)//中值濾波 輸入紀錄input 的 queue=> 輸出濾波結果
    {
        medinArray[arrayEnd++] = input;
        if (arrayEnd == arraySize)
            arrayEnd = 0;
        float key = 0;//比較用的數字
        int index = 0;//找到該數字的位置
        for (int i = 0; i < arraySize; i++)//Sorting
        {
            key = medinArray[i];
            index = i - 1;
            while (index > -1 && medinArray[index] > key)//排列數字，大的放右邊
            {
                medinArray[index + 1] = medinArray[index];//數字大放右邊
                --index;//往左邊一個數字觀看
            }
            medinArray[index + 1] = key;//將數字放在正確的位置
        }
        return medinArray[arraySize / 2]; //回傳中位數
    }

    private void computeAngle() {
        dt = (System.currentTimeMillis() - lastTime) / 1000;
        lastTime = System.currentTimeMillis();
        //complementary filter from last value and new data
        float ax_now = ax * 0.2f + ax_last * 0.8f;
        float ay_now = ay * 0.2f + ay_last * 0.8f;
        float az_now = az * 0.2f + az_last * 0.8f;
        ax_last = ax_now;
        ay_last = ay_now;
        az_last = az_now;
        angle_pitch += gy * dt / degree;
        angle_roll += gx * dt / degree;
        //Log.d("mile",Float.toString(dt));
        // 因z軸轉動而改變的角度
        angle_pitch_buff = angle_pitch;
        angle_roll_buff = angle_roll;
        angle_pitch -= angle_roll_buff * Math.sin(Math.toRadians(gz * dt));
        angle_roll += angle_pitch_buff * Math.sin(Math.toRadians(gz * dt));
        ////////////////////////////////////////////
        if (az_now > 0) {
            angle_pitch_acc = (float) Math.toDegrees(atan2(ax_now, Math.sqrt(Math.pow(ay_now, 2) + Math.pow(az_now, 2))));
            angle_roll_acc = (float) Math.toDegrees(atan2(ay_now, Math.sqrt(Math.pow(ax_now, 2) + Math.pow(az_now, 2))));
        } else {
            angle_pitch_acc = (float) Math.toDegrees(atan2(ax_now, -Math.sqrt(Math.pow(ay_now, 2) + Math.pow(az_now, 2))));
            angle_roll_acc = (float) Math.toDegrees(atan2(ay_now, -Math.sqrt(Math.pow(ax_now, 2) + Math.pow(az_now, 2))));
        }
        //Log.d("pow", Double.toString(Math.sqrt(Math.pow(ay, 2) + Math.pow(az, 2))));
        //  Serial.print("  pitch= ");
        //  Serial.print(angle_pitch_acc);
        //  Serial.print("  roll= ");
        //  Serial.print(angle_roll_acc);
        //互補濾波
        angle_pitch = angle_pitch * 0.7f + angle_pitch_acc * 0.3f;
        angle_roll = angle_roll * 0.7f + angle_roll_acc * 0.3f;
    }

    //***********************************可以調整的參數***************************************************************
    private final float[] weight = {0.45f, 20, 0.15f};//力道判斷比重 1.走路震動力道+-% 、 2.吃東西的角度+-degree  、 3.反芻震動力道+-%
    private final int Ruminating_row = 5;// 多少震動次數算一次反芻
    //*************************************************************************************************************/
    private int Ruminating_counter = 0;//反芻震動計算
    private int[] moveCount = {0, 0, 0};//總輸出次數
    private boolean[] place = {false, false, false};//是否回到正常情況的flag

    private void stepCounter(float input)//計步器
    {
        if (input > (1 + weight[0] * 0.8) * forceNormal) //超過往下加速度(落腳) 閥值
        {
            if (!place[0])//不是一直往下
            {
                moveCount[0]++;
                Log.d("step_time", moveCount[0] + "");

                //Serial.println(count[0]);
                //Serial.print("\n");
            }
            place[0] = true;
        } else if (input < (1 - weight[0] * 0.7) * forceNormal) //超過往上加速度(抬腳) 閥值 =>回復flag
        {
            place[0] = false;
        }
    }

    private void Ruminating_time(float input)//反芻計算
    {
        if (input > (1 + weight[2]) * forceNormal)//超過往下加速度(咬) 閥值
        {
            if (!place[2]) {
                Ruminating_counter++;
                //Serial.println( Ruminating_counter);
                //Serial.print("\n");
                Log.d("Ruminating_counter", moveCount[2] + "/" + Ruminating_counter);
            }
            if (Ruminating_counter >= Ruminating_row) //多少次震動算一次反芻
            {
                moveCount[2]++;
                Ruminating_counter = 0;//重新計算
                //test();
                Log.d("Ruminating_counter", moveCount[2] + "/" + Ruminating_counter);
            }
            if (input > (1 + weight[0]) * forceNormal)//超過往下加速度(落腳) 閥值
                Ruminating_counter = 0;//超過反芻力道 => 歸零

            place[2] = true;
        } else if (input < (1 - weight[2]) * forceNormal)//超過往上加速度(張嘴) 閥值 =>回復flag
        {
            if (input < (1 - weight[0]) * forceNormal) {//超過往上加速度(抬腳) 閥值 =>回復flag
                Ruminating_counter = 0;//超過反芻力道 => 歸零
                //Log.d("Ruminating_counter",moveCount[2] + "/" + Ruminating_counter);
            }
            place[2] = false;
        }

        if (input < (1 - weight[0]) * forceNormal) //超過往上加速度(抬腳) 閥值 =>回復flag
        {
            place[0] = false;
            Ruminating_counter = 0;//超過反芻力道 => 歸零
        }
    }

    private void eat_time()//低頭進食次數
    {
        if (angle_pitch < (-40 - weight[1]))//低頭角度低於 -40 - weight
        {
            if (!place[1]) {
                moveCount[1]++;
                Log.d("Eat_time", moveCount[1] + "");
                //Serial.println(count[1]);
                //Serial.print("\n");
            }
            place[1] = true;
        } else if (angle_pitch > (-40 + weight[1]))//抬頭回復flag
        {
            place[1] = false;
        }
    }

    public void reset() {
        //count = 0;
        Ruminating_counter = 0;
        moveCount[0] = 0;
        moveCount[1] = 0;
        moveCount[2] = 0;
    }
}

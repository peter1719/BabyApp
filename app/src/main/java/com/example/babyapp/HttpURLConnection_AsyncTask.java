package com.example.babyapp;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpURLConnection_AsyncTask extends AsyncTask<String, Void, String> {
    private final String TAG = "Log test";
    private String Url;
    private JSONObject data;
    private boolean setFlag = false;

    @Override
    protected String doInBackground(String... strings) {
        return POST(Url, data);
    }

    @Override
    protected void onPostExecute(String s) {
        Log.d(TAG, "onPostExecute : " + s);
    }

    HttpURLConnection_AsyncTask(String Uri_in, JSONObject jsonObject_in) {
        Url = Uri_in;
        data = jsonObject_in;
        setFlag =true;
    }

    private String POST(String APIUrl, JSONObject jsonObject) {
        if(!setFlag)
            return "set Fail";
        String result = "";
        HttpURLConnection connection;
        try {
            URL url = new URL(APIUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(jsonObject.toString());
            outputStream.writeBytes(stringBuilder.toString());
            outputStream.flush();
            outputStream.close();
            // Get the response
            InputStream inputStream = connection.getInputStream();
            int status = connection.getResponseCode();
            if (inputStream != null) {
                InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader in = new BufferedReader(reader);

                String line = "";
                while ((line = in.readLine()) != null) {
                    result += (line + "\n");
                }
            } else {
                result = "Fail.......";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}

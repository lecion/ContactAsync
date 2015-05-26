package com.yliec.contactasync;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;


public class MainActivity extends ActionBarActivity {
    public static final String TAG = "MainActivity";
    ArrayList<HashMap<String, String>> contacts;
    private JSONObject sendJsonObj;
    private String sendData;
    private Button btnSend;
    private EditText etServerAddr;
    private EditText etUsername;
    private boolean finishGet = false;
    private boolean isClick = false;
    private static final int MSG_FINISH = 1;
    private static final int MSG_FAILED = 2;
    private String serverAddr;
    private String username = null;
    private String localPhone;
    private Handler handler = new Handler() {
        @Override
        public void dispatchMessage(Message msg) {
            switch (msg.what) {
                case MSG_FAILED:
                    Toast.makeText(MainActivity.this, "上传失败", Toast.LENGTH_LONG).show();
                    break;
                case MSG_FINISH:
                    Toast.makeText(MainActivity.this, "上传成功", Toast.LENGTH_LONG).show();
                    break;
            }
            super.dispatchMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contacts = new ArrayList<>();
        new Thread(new GetContact()).start();
        etServerAddr = (EditText) findViewById(R.id.et_server_addr);
        etUsername = (EditText) findViewById(R.id.et_user_name);
        btnSend = (Button) findViewById(R.id.btn_async);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverAddr = etServerAddr.getText().toString();
                username = etUsername.getText().toString();
                if (TextUtils.isEmpty(serverAddr) || TextUtils.isEmpty(username)) {
                    Toast.makeText(MainActivity.this, "请输入用户名和服务器地址", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    Log.d(TAG, "localPhone " + localPhone);
                    sendJsonObj.put("username", username);
                    sendJsonObj.put("phone", localPhone);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendData = sendJsonObj.toString();
                if (finishGet) {
                    Toast.makeText(MainActivity.this, "正在上传", Toast.LENGTH_LONG).show();
                    new Thread(new SendContact()).start();
                } else {
                    Toast.makeText(MainActivity.this, "正在获取通信录，请稍后再试", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void generateJson() {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        int size = contacts.size();
        for (int i = 0; i < size; i++) {
            HashMap<String, String> contact = contacts.get(i);
            String phone = contact.get("phone");
            if (phone != null) {
                String[] arr = phone.split("#");
                JSONObject jsonObjArr = new JSONObject();
                try {
                    JSONArray jsonArr = new JSONArray();
                    for (int j = 0; j < arr.length; j++) {
                        jsonArr.put(arr[j]);
                    }
                    jsonObjArr.put("username", contact.get("username"));
                    jsonObjArr.put("phones", jsonArr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(jsonObjArr);
            }
        }
        try {
            Log.d(TAG, jsonArray.toString());
            jsonObject.put("contacts", jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendJsonObj = jsonObject;
    }

    public void getContact() {
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        String pattern = "^((\\+?86)|(\\(\\+86\\)))?1\\d{10}$";
        while (cursor.moveToNext()) {
            StringBuilder sb = new StringBuilder();
            HashMap<String, String> contact = new HashMap<>();
            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            Cursor phoneCursor = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", new String[]{contactId}, null);
            while (phoneCursor.moveToNext()) {
                String phone = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                //处理电话号码格式，去掉空格和横线
//                phone = phone.replace(" ", "").replace("-", "").replace("+", "");
                phone = phone.replace(" ", "").replace("-", "");
                if (phone.matches(pattern)) {
                    sb.append(phone).append("#");
                } else {
                    Log.d(TAG, phone + " false" );
                }
            }
            Log.d("原来的", sb.toString());
            phoneCursor.close();
            if (sb.length() > 0) {
                contact.put("username", contactName);
                String phoneSub = sb.substring(0, sb.length() - 1);
                contact.put("phone", phoneSub);
                Log.d("原来的sub后", phoneSub);
            }
            contacts.add(contact);
            sb = null;
        }
        TelephonyManager manager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        localPhone = manager.getLine1Number();
        Log.d(TAG, "localPhone " + localPhone);
    }

    class GetContact implements Runnable {

        @Override
        public void run() {
            getContact();
            generateJson();
            finishGet = true;
        }
    }

    class SendContact implements Runnable {

        @Override
        public void run() {
            sendContact();
        }
    }

    private void sendContact() {
        Log.d("sendContact", serverAddr);
        Log.d("sendData", sendData);
        URL url = null;
        try {
            url = new URL(serverAddr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.connect();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            bw.write(sendData);
            bw.flush();
            String line;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = br.readLine()) != null) {
                Log.d("replyData", line);
                sb.append(line);
            }
            String replayJson = sb.toString();
            String replayUrl = new JSONObject(replayJson).getString("url");
            viewInBrowser(replayUrl);
            sendFinishedMsg(line);

            bw.close();
            br.close();
            connection.disconnect();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    private void sendFinishedMsg(String line) {
        Message msg = handler.obtainMessage();
        if (line != null || line != "{}") {
            msg.what = MSG_FINISH;
        } else {
            msg.what = MSG_FAILED;
        }
        handler.sendMessage(msg);
    }

    private void viewInBrowser(String replayUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(replayUrl));
        startActivity(intent);
    }


}

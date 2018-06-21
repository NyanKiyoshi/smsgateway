package com.jerrymannel.smsgateway;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MySMSGatewayMainActivity extends Activity {

    private LinearLayout linerLayout_server;
    private LinearLayout linearLayout_message;
    private Switch switch_server;
    private TextView textView_serverStatus;
    private TextView textView_comment;
    private SharedPreferences prefs;

    private String ipAddress;
    private int port;
    private HTTPServer server;
    private SimpleDateFormat sdf;
    private String currentTime;

    private SmsManager sms;
    private String phoneNumber;
    private String message;

    private static final String TAG = "mysmsgateway";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkNetworkState();
        getLocalIpAddress();

        sms = SmsManager.getDefault();

        prefs = this.getSharedPreferences("com.jerrymannel.mysmsgateway", Context.MODE_PRIVATE);

        if (prefs.getInt("port", 0) == 0) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("port", 18080);
            editor.commit();
            port = 18080;
        }

        linerLayout_server = (LinearLayout) findViewById(R.id.linearLayout_server);
        linearLayout_message = (LinearLayout) findViewById(R.id.linearLayout_message);
        textView_serverStatus = (TextView) findViewById(R.id.textView_serverStaus);
        textView_comment = (TextView) findViewById(R.id.textView_comment);
        switch_server = (Switch) findViewById(R.id.switch_server);
        server = null;

        switch_server.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (checkNetworkState()) {
                    if (isChecked) {
                        textView_serverStatus.setText(R.string.serverOn);
                        linerLayout_server
                                .setBackgroundResource(R.drawable.backgroud_start);
                        port = prefs.getInt("port", 0);
                        Log.i(TAG, "Port set to " + port);
                        textView_comment
                                .setText(getString(R.string.connectComment)
                                        + "http://" + ipAddress + ":" + port);

                        Log.i(TAG, "Starting server ...");
                        server = new HTTPServer();
                        server.execute("");
                    } else {
                        textView_serverStatus.setText(R.string.serverOff);
                        linerLayout_server
                                .setBackgroundResource(R.drawable.backgroud_stop);
                        textView_comment.setText(R.string.stopComment);
                        server.cancel(true);
                        server = null;
                        Log.i(TAG, "Server stopped!");
                    }
                } else {
                    switch_server.toggle();
                }
            }
        });
    }

    protected void onPause() {
        Log.i(TAG, "App has gone into pause mode. Stopping server!");
        if (server != null)
            server.cancel(true);
        textView_serverStatus.setText(R.string.serverOff);
        linerLayout_server.setBackgroundResource(R.drawable.backgroud_stop);
        textView_comment.setText(R.string.initialComment);
        switch_server.setChecked(false);
        super.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Murderer!!! The app has been killed!. Stopping server!");
        if (server != null)
            server.cancel(true);
        textView_serverStatus.setText(R.string.serverOff);
        linerLayout_server.setBackgroundResource(R.drawable.backgroud_stop);
        textView_comment.setText(R.string.initialComment);
        switch_server.setChecked(false);
        super.onPause();
    }

    protected void onRestart() {
        Log.i(TAG, "App restart has occured.");
        if (server != null)
            server.cancel(true);
        textView_serverStatus.setText(R.string.serverOff);
        linerLayout_server.setBackgroundResource(R.drawable.backgroud_stop);
        textView_comment.setText(R.string.initialComment);
        switch_server.setChecked(false);
        super.onPause();
        super.onRestart();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = getMenuInflater();
        mi.inflate(R.menu.options_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_clear:
                linearLayout_message.removeAllViews();
                break;
            case R.id.item_setting:
                startActivity(new Intent(this, SettingsView.class));
                break;
            case R.id.item_about:
                startActivity(new Intent(this, AboutView.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkNetworkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        showAlert("No active network connections \navailable.");
        return false;
    }

    private void showAlert(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MySMSGatewayMainActivity.this, s,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getLocalIpAddress() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            ipAddress = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        }
    }

    private void sendSMS() {
        sdf = (SimpleDateFormat) SimpleDateFormat.getTimeInstance();
        currentTime = sdf.format(new Date());

        final MessageView v1 = new MessageView(
                linearLayout_message.getContext(), null);

        try {
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            v1.setData(phoneNumber, message, currentTime);
            showAlert("Message Sent!");
        } catch (Exception e) {
            showAlert("SMS failed, please try again later!");
            v1.setData(phoneNumber, "[FAIL]" + message, currentTime);
            e.printStackTrace();
        }

        runOnUiThread(new Runnable() {
            public void run() {
                linearLayout_message.addView(v1);
            }
        });

    }

    private class HTTPServer extends AsyncTask<String, Void, Void> {
        private void sendHTTPBadRequest(DataOutputStream outputStream) throws IOException {
            outputStream.writeBytes(
                    "HTTP/1.1 400 Bad Request\n"
                            + "Connection: close\n"
                            + "\n");
        }

        private void sendHTTPOK(DataOutputStream outputStream) throws IOException {
            outputStream.writeBytes(
                    "HTTP/1.1 200 OK\n"
                            + "Connection: close\n"
                            + "\n");
        }

        protected Void doInBackground(String... params) {
            try {
                ServerSocket server = new ServerSocket(port);
                Log.i(TAG, "Port Set. Server started!");
                while (true) {
                    Socket socket = server.accept();

                    if (isCancelled()) {
                        socket.close();
                        server.close();
                        break;
                    }

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    // get the first line of the HTTP GET request
                    // sample : GET /?phone=%2B911234567890&message=HelloWorld HTTP/1.1
                    String data = in.readLine();

                    // skip if no data was received
                    if (data == null) {
                        continue;
                    }

                    // get the substring after GET /? and before HTTP/x.x
                    data = data.substring(5, data.length() - 9);
                    Uri url = Uri.parse(data);

                    phoneNumber = url.getQueryParameter("phone");
                    message = url.getQueryParameter("message");

                    // if the URL doesn't contain the sting phone, do nothing.
                    if (phoneNumber == null || message == null) {
                        Log.i(TAG, "Invalid URL");
                        showAlert("Invalid URL");
                        this.sendHTTPBadRequest(out);
                    } else {
                        Log.i(TAG, "Got a request to sent an SMS.");
                        Log.i(TAG, "Phone Number: " + phoneNumber);
                        Log.i(TAG, "Message: " + message);

                        sendSMS();
                        this.sendHTTPOK(out);
                    }

                    out.close();
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}

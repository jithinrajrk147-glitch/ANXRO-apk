package com.anxro.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.os.AsyncTask;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.net.URL;
import java.util.zip.*;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.ArrayList;

public class MainActivity extends Activity {
    WebView webView;
    String VERSION_URL = "https://jithinrajrk147-glitch.github.io/Anxro/app-version.json";
    String APP_FOLDER = "anxro_app";
    private static final int REQ_SPEECH = 101;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // DAY/NIGHT LOGO SWITCH - 6am to 6pm = day, else night
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 18) {
            setTheme(R.style.DayTheme);
        } else {
            setTheme(R.style.NightTheme);
        }
        
        requestPermissions();
        sendNotification("Anxro", "App started");
        
        webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        setContentView(webView);
        
        String openPage = getIntent().getStringExtra("open_page");
        if (openPage != null) {
            loadLocalFile(openPage);
        } else {
            new SilentUpdateTask().execute();
        }
    }
    
    void requestPermissions() {
        String[] perms = {Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
        ArrayList<String> toRequest = new ArrayList<>();
        for(String p : perms) {
            if(ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }
        if(!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String), 100);
        }
    }
    
    public class WebAppInterface {
        @JavascriptInterface
        public void startMic() {
            runOnUiThread(() -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                startActivityForResult(intent, REQ_SPEECH);
            });
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQ_SPEECH && resultCode == RESULT_OK) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get;
            webView.evaluateJavascript("javascript:onMicResult('" + spokenText.replace("'","\\'") + "')", null);
        }
    }
    
    void sendNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "anxro_channel";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Anxro", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        Notification notif = new Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .build();
        nm.notify(1, notif);
    }
    
    void loadLocalFile(String filename) {
        File file = new File(getFilesDir(), APP_FOLDER + "/" + filename);
        if(file.exists()) webView.loadUrl("file://" + file.getAbsolutePath());
        else webView.loadData("Page not found", "text/html", "UTF-8");
    }
    
    class SilentUpdateTask extends AsyncTask<Void, Void, String> {
        @Override protected String doInBackground(Void... params) {
            try {
                String json = downloadString(VERSION_URL);
                JSONObject obj = new JSONObject(json);
                int remoteVersion = obj.getInt("version");
                String zipUrl = obj.getString("zip_url");
                File versionFile = new File(getFilesDir(), "version.txt");
                int localVersion = 0;
                if (versionFile.exists()) {
                    BufferedReader br = new BufferedReader(new FileReader(versionFile));
                    localVersion = Integer.parseInt(br.readLine());
                    br.close();
                }
                if (remoteVersion > localVersion) {
                    File zipFile = new File(getFilesDir(), "update.zip");
                    downloadFile(zipUrl, zipFile);
                    File appDir = new File(getFilesDir(), APP_FOLDER);
                    deleteRecursive(appDir);
                    unzip(zipFile, appDir);
                    zipFile.delete();
                    FileWriter fw = new FileWriter(versionFile);
                    fw.write(String.valueOf(remoteVersion));
                    fw.close();
                }
                return new File(getFilesDir(), APP_FOLDER + "/index.html").getAbsolutePath();
            } catch (Exception e) { return null; }
        }
        @Override protected void onPostExecute(String path) {
            if(path != null) webView.loadUrl("file://" + path);
            else webView.loadData("No app found", "text/html", "UTF-8");
        }
    }
    
    String downloadString(String urlStr) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine())!= null) sb.append(line);
        br.close(); return sb.toString();
    }
    void downloadFile(String urlStr, File file) throws Exception {
        InputStream in = new URL(urlStr).openStream();
        FileOutputStream out = new FileOutputStream(file);
        byte[] buf = new byte; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        out.close(); in.close();
    }
    void unzip(File zipFile, File targetDir) throws Exception {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry ze; while ((ze = zis.getNextEntry())!= null) {
            File file = new File(targetDir, ze.getName());
            if (ze.isDirectory()) file.mkdirs();
            else { new File(file.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buf = new byte; int len;
                while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                fos.close();
            }
        } zis.close();
    }
    void deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) for (File child : fileOrDir.listFiles()) deleteRecursive(child);
        fileOrDir.delete();
    }
}

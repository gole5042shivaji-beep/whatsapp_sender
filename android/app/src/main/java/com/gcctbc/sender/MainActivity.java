package com.gcctbc.sender;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {

    // ===== आपली Apps Script Web App URL =====
    private static final String WEB_APP_URL =
            "https://script.google.com/macros/s/AKfycbynrzXYaxmR7xuIwp5bWbax300nUnCfNszVITFHHqC_I8I5qaUI50ZMe1UzttY1y8Ai/exec";
    private static final String SALT = "gcctbc-sender-android-v1-7Qh2";
    private static final long MAX_OFFLINE_MS = 30L * 24 * 3600 * 1000;   // 30 दिवस ऑफलाइन चालेल
    private static final int REQ_FILE = 4711;

    private SharedPreferences sp;
    private FrameLayout root;
    private WebView web;
    private byte[] htmlBytes;
    private ValueCallback<Uri[]> fileCb;
    private String deviceId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("lic", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#f2f4fa"));
        setContentView(root);
        deviceId = makeDeviceId();
        startFlow();
    }

    // ---------------------------------------------------------------- Device ID
    private String makeDeviceId() {
        try {
            String aid = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (aid == null) aid = "unknown";
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("gcctbc-sender|" + aid).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(String.format("%02X", h[i]));
            String s = sb.toString();
            return "AND-" + s.substring(0, 5) + "-" + s.substring(5, 10) + "-" + s.substring(10, 15) + "-" + s.substring(15, 20);
        } catch (Exception e) {
            return "AND-UNKNOWN";
        }
    }

    // ---------------------------------------------------------------- स्थानिक Activation साठवण
    private String sign(String key, String expiry, long lastCheck) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + "|" + key + "|" + expiry + "|" + lastCheck + "|" + deviceId).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void saveLocal(String key, String name, String expiryIso) {
        long now = System.currentTimeMillis();
        sp.edit().putString("key", key).putString("name", name).putString("expiry", expiryIso)
                .putLong("lastCheck", now).putLong("lastRun", now)
                .putString("sig", sign(key, expiryIso, now)).apply();
    }

    private void clearLocal() {
        sp.edit().clear().apply();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String toIso(String ddMMyyyy) {
        try {
            Date d = new SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(ddMMyyyy);
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
        } catch (Exception e) {
            return ddMMyyyy;
        }
    }

    /** 0 = ठीक, 1 = ऑनलाइन तपासणी आवश्यक, 2 = Activation नाही/खराब, 3 = मुदत संपली */
    private int localState() {
        String key = sp.getString("key", "");
        String exp = sp.getString("expiry", "");
        long last = sp.getLong("lastCheck", 0);
        long lastRun = sp.getLong("lastRun", 0);
        if (key.length() == 0 || exp.length() == 0) return 2;
        if (!sign(key, exp, last).equals(sp.getString("sig", ""))) return 2;
        if (today().compareTo(exp) > 0) return 3;
        long now = System.currentTimeMillis();
        if (now < lastRun - 24L * 3600 * 1000) return 1;          // घड्याळ मागे केले असावे
        if (now - last > MAX_OFFLINE_MS) return 1;                  // खूप दिवस तपासणी नाही
        return 0;
    }

    // ---------------------------------------------------------------- प्रवाह
    private void startFlow() {
        int st = localState();
        if (st == 0) {
            sp.edit().putLong("lastRun", System.currentTimeMillis()).apply();
            showApp();
            recheckInBackground();
        } else if (st == 1) {
            showActivation("इंटरनेट चालू करून पुन्हा तपासणी करा (ऑफलाइन मर्यादा संपली).", true);
        } else if (st == 3) {
            showActivation("मुदत संपली आहे. नवीन Key साठी संपर्क करा.", false);
        } else {
            showActivation(null, false);
        }
    }

    private void recheckInBackground() {
        final String key = sp.getString("key", "");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject r = callServer(key);
                    if (r.optBoolean("success")) {
                        final String exp = toIso(r.optString("expiryDate"));
                        saveLocal(key, r.optString("name"), exp);
                    } else {
                        final String code = r.optString("code");
                        final String msg = r.optString("message", "Activation अवैध आहे.");
                        if ("REVOKED".equals(code) || "INVALID".equals(code) || "DEVICE_LIMIT".equals(code)) {
                            clearLocal();
                            runOnUiThread(new Runnable() { @Override public void run() { dropApp(); showActivation(msg, false); } });
                        } else if ("EXPIRED".equals(code)) {
                            sp.edit().putString("expiry", "2000-01-01").apply();
                            runOnUiThread(new Runnable() { @Override public void run() { dropApp(); showActivation(msg, false); } });
                        }
                    }
                } catch (Exception ignore) { /* ऑफलाइन — स्थानिक Activation वर चालू राहील */ }
            }
        }).start();
    }

    private JSONObject callServer(String key) throws Exception {
        JSONObject req = new JSONObject();
        req.put("action", "senderActivate");
        req.put("licenseKey", key);
        req.put("machineId", deviceId);
        req.put("platform", "android");
        String res = post(req.toString());
        return new JSONObject(res);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    private static String post(String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(WEB_APP_URL).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            HttpURLConnection g = (HttpURLConnection) new URL(loc).openConnection();
            g.setConnectTimeout(10000);
            g.setReadTimeout(20000);
            String body = readAll(g.getResponseCode() >= 400 ? g.getErrorStream() : g.getInputStream());
            g.disconnect();
            return body;
        }
        String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        return body;
    }

    // ---------------------------------------------------------------- Activation स्क्रीन
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showActivation(String message, boolean retryOnly) {
        root.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(48), dp(22), dp(24));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("🔑 WhatsApp Message Sender");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#16203a"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Activation साठी आपली License Key टाका");
        sub.setTextSize(15);
        sub.setTextColor(Color.parseColor("#5b6784"));
        sub.setPadding(0, dp(6), 0, dp(18));
        col.addView(sub);

        final EditText key = new EditText(this);
        key.setHint("XXXXX-XXXXX-XXXXX-XXXXX-X");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        key.setSingleLine(true);
        key.setTextSize(18);
        key.setText(sp.getString("key", ""));
        col.addView(key);

        final TextView status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(12), 0, dp(4));
        if (message != null) {
            status.setText(message);
            status.setTextColor(Color.parseColor("#c62828"));
        }

        final Button go = new Button(this);
        go.setText(retryOnly ? "पुन्हा तपासा" : "Activate करा");
        go.setTextSize(17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        col.addView(go, lp);
        col.addView(status);

        TextView dev = new TextView(this);
        dev.setText("डिव्हाइस ID: " + deviceId);
        dev.setTextSize(12);
        dev.setTextColor(Color.parseColor("#5b6784"));
        dev.setTextIsSelectable(true);
        dev.setPadding(0, dp(26), 0, 0);
        col.addView(dev);

        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String k = key.getText().toString().trim().toUpperCase(Locale.US);
                if (k.length() < 8) {
                    status.setTextColor(Color.parseColor("#c62828"));
                    status.setText("Key टाका.");
                    return;
                }
                go.setEnabled(false);
                status.setTextColor(Color.parseColor("#5b6784"));
                status.setText("तपासत आहे…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String err = null;
                        JSONObject r = null;
                        try {
                            r = callServer(k);
                        } catch (Exception e) {
                            err = "इंटरनेट/सर्व्हर समस्या. इंटरनेट चालू करून पुन्हा प्रयत्न करा.";
                        }
                        final JSONObject fr = r;
                        final String ferr = err;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                go.setEnabled(true);
                                if (ferr != null) {
                                    status.setTextColor(Color.parseColor("#c62828"));
                                    status.setText(ferr);
                                } else if (fr.optBoolean("success")) {
                                    saveLocal(k, fr.optString("name"), toIso(fr.optString("expiryDate")));
                                    Toast.makeText(MainActivity.this, "✅ Activation यशस्वी", Toast.LENGTH_LONG).show();
                                    showApp();
                                } else {
                                    status.setTextColor(Color.parseColor("#c62828"));
                                    status.setText(fr.optString("message", "Activation झाले नाही."));
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------------------------------------------------------- मुख्य ॲप (WebView)
    private void dropApp() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
    }

    private void showApp() {
        try {
            if (htmlBytes == null) {
                InputStream in = getAssets().open("index.html.enc");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                htmlBytes = FileCrypt.decrypt(bo.toByteArray());
            }
        } catch (Exception e) {
            showActivation("ॲप फाइल खराब आहे. पुन्हा Install करा.", false);
            return;
        }
        root.removeAllViews();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        web.addJavascriptInterface(new Bridge(), "AndroidSender");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("sender.app".equals(u.getHost())) {
                    String p = u.getPath();
                    if (p == null || p.equals("/") || p.equals("/index.html")) {
                        return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(htmlBytes));
                    }
                }
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                        new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;   // बाहेरील पृष्ठे उघडू नयेत
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCb = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String url, String msg, final JsResult res) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                        .setPositiveButton("हो", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int i) { res.confirm(); }
                        })
                        .setNegativeButton("नाही", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int i) { res.cancel(); }
                        }).show();
                return true;
            }
            @Override public boolean onJsAlert(WebView v, String url, String msg, final JsResult res) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                        .setPositiveButton("ठीक आहे", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int i) { res.confirm(); }
                        }).show();
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl("https://sender.app/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            Uri[] r = WebChromeClient.FileChooserParams.parseResult(res, data);
            fileCb.onReceiveValue(r);
            fileCb = null;
        } else {
            super.onActivityResult(req, res, data);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    // ---------------------------------------------------------------- WhatsApp ब्रिज
    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private File shareFile(String name) {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    private void toastUi(final String t) {
        runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, t, Toast.LENGTH_LONG).show(); } });
    }

    public class Bridge {
        @JavascriptInterface
        public void send(final String number, final String text, final String imgDataUrl, final String mode) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        String pkg = "biz".equals(mode) ? "com.whatsapp.w4b" : "com.whatsapp";
                        if (!installed(pkg)) pkg = "biz".equals(mode) ? "com.whatsapp" : "com.whatsapp.w4b";
                        if (!installed(pkg)) {
                            Toast.makeText(MainActivity.this, "WhatsApp इन्स्टॉल केलेले नाही", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Intent i;
                        if (imgDataUrl != null && imgDataUrl.length() > 30) {
                            String b64 = imgDataUrl.substring(imgDataUrl.indexOf(',') + 1);
                            byte[] png = Base64.decode(b64, Base64.DEFAULT);
                            File f = shareFile("send.png");
                            FileOutputStream fo = new FileOutputStream(f);
                            fo.write(png);
                            fo.close();
                            Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.gcctbc.sender.fileprovider", f);
                            i = new Intent(Intent.ACTION_SEND);
                            i.setType("image/png");
                            i.putExtra(Intent.EXTRA_STREAM, uri);
                            if (text != null && text.length() > 0) i.putExtra(Intent.EXTRA_TEXT, text);
                            i.putExtra("jid", number + "@s.whatsapp.net");
                            i.setClipData(ClipData.newRawUri("img", uri));
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } else {
                            i = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/" + number + "?text=" + URLEncoder.encode(text == null ? "" : text, "UTF-8")));
                        }
                        i.setPackage(pkg);
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "WhatsApp उघडता आले नाही: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveText(final String filename, final String content) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        File f = shareFile(filename.replaceAll("[^A-Za-z0-9._-]", "_"));
                        FileOutputStream fo = new FileOutputStream(f);
                        fo.write(content.getBytes("UTF-8"));
                        fo.close();
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.gcctbc.sender.fileprovider", f);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/csv");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.setClipData(ClipData.newRawUri("csv", uri));
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "नोंद (CSV) पाठवा / सेव्ह करा"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "फाइल तयार झाली नाही", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
}

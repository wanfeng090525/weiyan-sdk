package com.example.myapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.weiyan.sdk.WYLoginResult;
import com.weiyan.sdk.WYNoticeResult;
import com.weiyan.sdk.WYVerify;
import com.weiyan.sdk.WYVersionResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 登录页 - 微验(WY) SDK 测试 APK
 *
 * 凭证已内置在 libwyverify.so 中，这里只负责界面与调用。
 * 设备码(markcode)由本 APK 生成（ANDROID_ID），传给 SDK 用于单码登录。
 */
public class LoginActivity extends Activity {

    /* 对接授权密钥：必须与 .so 内置密钥一致才能调用 SDK（错误时接口返回"密钥错误"） */
    public static final String SDK_KEY = "wanfeng";

    public static final String PREFS_NAME = "wy_prefs";
    public static final String KEY_KAMI = "kami";
    public static final String KEY_MARKCODE = "markcode";
    public static final String KEY_TYPE = "type";
    public static final String KEY_KM_TYPE = "kmtype";
    public static final String KEY_KM_TYPE_NAME = "kmtype_name";
    public static final String KEY_REMAIN = "remain";
    public static final String KEY_END_TIME = "end_time";
    public static final String KEY_TOKEN = "token";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WYVerify wy;
    private EditText etKami;
    private TextView tvMachineCode;
    private TextView tvError;
    private Button btnLogin;
    private String markcode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etKami = findViewById(R.id.etKami);
        tvMachineCode = findViewById(R.id.tvMachineCode);
        tvError = findViewById(R.id.tvError);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvCopy = findViewById(R.id.tvCopyMachine);

        // 初始化 SDK（凭证密文在 .so 中，需传入授权密钥）
        wy = new WYVerify(SDK_KEY);

        // 获取设备码（微验的 markcode）
        executor.execute(() -> {
            markcode = genMarkcode();
            mainHandler.post(() -> {
                tvMachineCode.setText(TextUtils.isEmpty(markcode) ? "获取失败" : markcode);
                // 自动登录（读取本地保存卡密自动验证）
                String savedKami = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getString(KEY_KAMI, "");
                if (!TextUtils.isEmpty(savedKami) && !TextUtils.isEmpty(markcode)) {
                    tryAutoLogin(savedKami);
                }
            });
        });

        tvCopy.setOnClickListener(v -> {
            if (TextUtils.isEmpty(markcode)) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("machine", markcode));
            Toast.makeText(this, "已复制设备码", Toast.LENGTH_SHORT).show();
        });

        btnLogin.setOnClickListener(v -> doLogin());

        // 更多功能：公告 / 检查更新
        findViewById(R.id.rowNotice).setOnClickListener(v -> loadNotice());
        findViewById(R.id.rowUpdate).setOnClickListener(v -> checkUpdate());
    }

    /* 生成设备码：与 T3 的机器码不同，微验由 App 自行提供 markcode */
    private String genMarkcode() {
        try {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) id = "0000000000000000";
            return id.toUpperCase();
        } catch (Throwable t) {
            return "0000000000000000";
        }
    }

    // ========== 自动登录 ==========

    private void tryAutoLogin(final String kami) {
        final AlertDialog loading = new AlertDialog.Builder(this)
                .setMessage("正在自动登录...")
                .setCancelable(false)
                .create();
        loading.show();
        btnLogin.setEnabled(false);

        executor.execute(() -> {
            final WYLoginResult r = wy.login(kami, markcode);
            mainHandler.post(() -> {
                loading.dismiss();
                if (r != null && r.success) {
                    saveLogin(kami, r);
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    clearSavedKami();
                    showError(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "自动登录失败");
                    btnLogin.setEnabled(true);
                }
            });
        });
    }

    private void clearSavedKami() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove(KEY_KAMI)
                .remove(KEY_TYPE)
                .remove(KEY_KM_TYPE)
                .remove(KEY_KM_TYPE_NAME)
                .remove(KEY_REMAIN)
                .remove(KEY_END_TIME)
                .remove(KEY_TOKEN)
                .apply();
    }

    private void doLogin() {
        final String kami = etKami.getText().toString().trim();
        if (TextUtils.isEmpty(kami)) {
            showError("请输入卡密");
            return;
        }
        if (TextUtils.isEmpty(markcode)) {
            showError("设备码获取失败，请重试");
            return;
        }

        btnLogin.setEnabled(false);
        showError("");
        executor.execute(() -> {
            WYLoginResult r = wy.login(kami, markcode);
            mainHandler.post(() -> {
                btnLogin.setEnabled(true);
                if (r != null && r.success) {
                    saveLogin(kami, r);
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    showError(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "登录失败");
                }
            });
        });
    }

    // ========== 更多功能：公告 / 检查更新 ==========

    private void loadNotice() {
        executor.execute(() -> {
            WYNoticeResult r = wy.getNotice();
            mainHandler.post(() -> {
                if (r != null && r.success) {
                    showDialog("公告", r.notice);
                } else {
                    showError(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "公告获取失败");
                }
            });
        });
    }

    private void checkUpdate() {
        executor.execute(() -> {
            WYVersionResult r = wy.checkUpdate("1.0");
            mainHandler.post(() -> {
                if (r != null && r.success) {
                    if (r.hasUpdate) {
                        showDialog("发现新版本 " + r.version,
                                TextUtils.isEmpty(r.updateshow) ? "请前往下载更新" : r.updateshow);
                    } else {
                        showError("已是最新版本");
                    }
                } else {
                    showError(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "版本查询失败");
                }
            });
        });
    }

    private void showDialog(String title, String msg) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        b.setMessage(TextUtils.isEmpty(msg) ? "(无内容)" : msg);
        b.setPositiveButton("确定", (d, w) -> d.dismiss());
        b.show();
    }

    private void saveLogin(String kami, WYLoginResult r) {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit()
                .putString(KEY_KAMI, kami)
                .putString(KEY_MARKCODE, markcode)
                .putString(KEY_TYPE, r.type)
                .putString(KEY_KM_TYPE, r.kmtype == null ? "" : r.kmtype)
                .putString(KEY_KM_TYPE_NAME, r.kmtypeName == null ? "" : r.kmtypeName)
                .putLong(KEY_REMAIN, r.remain)
                .putLong(KEY_END_TIME, r.endTime)
                .putString(KEY_TOKEN, r.token == null ? "" : r.token)
                .apply();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextUtils.isEmpty(msg) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wy != null) {
            wy.destroy();
            wy = null;
        }
        executor.shutdown();
    }
}

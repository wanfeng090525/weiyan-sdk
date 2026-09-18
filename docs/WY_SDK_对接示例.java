package com.example.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import com.weiyan.sdk.WYHeartbeatResult;
import com.weiyan.sdk.WYLoginResult;
import com.weiyan.sdk.WYNoticeResult;
import com.weiyan.sdk.WYUnbindResult;
import com.weiyan.sdk.WYVerify;
import com.weiyan.sdk.WYVersionResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 微验(WY) SDK 对接示例（可直接复制到你的项目中使用）
 *
 * 覆盖全部 5 个接口：公告 / 检查更新 / 单码登录 / 单码解绑 / 心跳验证
 *
 * 使用前提（见《WY_SDK_对接文档.md》）：
 *   1. libwyverify.so（arm64-v8a）放入 app/src/main/jniLibs/arm64-v8a/；
 *   2. com.weiyan.sdk 下 7 个 Java 类（WYVerify/WYResult/WYLoginResult/
 *      WYHeartbeatResult/WYNoticeResult/WYVersionResult/WYUnbindResult）放入项目；
 *   3. AndroidManifest.xml 声明 <uses-permission android:name="android.permission.INTERNET"/>；
 *   4. 构造 WYVerify 必须传入授权密钥 "wanfeng"，密钥错误时所有接口返回失败。
 *
 * 调用注意：
 *   - 所有接口均为耗时网络操作，必须在子线程调用（示例使用单线程池）；
 *   - UI 刷新请切回主线程（示例使用 Handler）；
 *   - 心跳官方建议 10~60 秒一次，Token 默认 120 秒过期（code=105 时需重新登录）。
 *
 * 对接密钥：wanfeng
 */
public class WySdkDemo {

    /* ==================== 对接配置 ==================== */

    /** 授权密钥：必须与 .so 内置密钥一致，错误时接口返回 msg="密钥错误" */
    private static final String SDK_KEY = "wanfeng";
    /** 心跳间隔：官方建议 10~60 秒，Token 默认 120 秒过期 */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    /** 服务器状态码：105 = 数据过期（Token 失效），需重新登录 */
    private static final int CODE_DATA_EXPIRED = 105;

    /* ==================== 运行环境 ==================== */

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WYVerify wy;          // SDK 实例
    private String kami = "";     // 卡密
    private String markcode = ""; // 设备码
    private String token = "";    // 登录令牌（心跳用）

    private boolean hbRunning = false;                       // 心跳开关
    private final Runnable hbTask = new Runnable() {
        @Override public void run() {
            heartbeat();                                     // 执行一次心跳
            if (hbRunning) mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    /** 回调：把结果抛回 UI 层展示 */
    public interface Callback {
        void onResult(String message);
    }

    public WySdkDemo(Context context) {
        this.context = context.getApplicationContext();
        markcode = genMarkcode();   // 设备码（推荐 ANDROID_ID）
    }

    /* ==================== 1. 初始化 + 单码登录 ==================== */

    /**
     * 登录：传入卡密，成功后可拿到 token / 卡密类型中文名 / 剩余次数或到期时间。
     * 调用方把 token 持久化保存（SharedPreferences），App 重启后免登录。
     */
    public void login(final String kami, final Callback cb) {
        this.kami = kami;
        wy = new WYVerify(SDK_KEY);   // 必须传授权密钥，否则所有接口返回失败
        executor.execute(() -> {
            WYLoginResult r = wy.login(kami, markcode);
            mainHandler.post(() -> {
                if (r == null) { cb.onResult("登录失败：SDK 返回空"); return; }
                if (r.success) {
                    token = r.token;                       // 心跳必传
                    StringBuilder sb = new StringBuilder("登录成功\n");
                    sb.append("卡密类型：" + (TextUtils.isEmpty(r.kmtypeName) ? "—" : r.kmtypeName) + "\n");
                    sb.append("剩余次数：" + r.remain + "\n");
                    if (r.endTime > 0) sb.append("到期时间：" + fmtTime(r.endTime) + "\n");
                    sb.append("Token：" + r.token);
                    saveToken(token);                      // 持久化 token
                    startHeartbeat();                      // 登录成功自动开启心跳
                    cb.onResult(sb.toString());
                } else {
                    cb.onResult("登录失败：" + r.msg);
                }
            });
        });
    }

    /* ==================== 2. 心跳验证（自动） ==================== */

    /** 启动自动心跳（每 30 秒一次），把状态刷回 UI */
    public void startHeartbeat() {
        if (hbRunning) return;
        hbRunning = true;
        mainHandler.postDelayed(hbTask, HEARTBEAT_INTERVAL_MS);
    }

    /** 停止自动心跳（页面退出 / 解绑后调用） */
    public void stopHeartbeat() {
        hbRunning = false;
        mainHandler.removeCallbacks(hbTask);
    }

    /** 执行一次心跳：成功刷新到期时间/卡类型；code=105 表示 Token 过期需重新登录 */
    private void heartbeat() {
        if (TextUtils.isEmpty(token)) { notifyUser("心跳未执行：无 Token，请先登录"); return; }
        executor.execute(() -> {
            WYHeartbeatResult r = wy.heartbeat(kami, markcode, token);
            mainHandler.post(() -> {
                if (r == null) { notifyUser("心跳失败：SDK 返回空"); return; }
                if (r.success) {
                    String msg = "心跳正常 · " +
                            (r.endTime > 0 ? fmtTime(r.endTime) : "无到期时间") +
                            (TextUtils.isEmpty(r.onlinenum) ? "" : " · 在线" + r.onlinenum + "人");
                    /* 登录响应可能不含 kmtype，心跳返回的 timetype 是权威来源，
                     * .so 已把 timetype 转成中文名（永久卡/天卡/...），可用来刷新"卡类型"显示 */
                    if (!TextUtils.isEmpty(r.timetypeName)) msg += "\n卡类型：" + r.timetypeName;
                    notifyUser(msg);
                } else if (r.code == CODE_DATA_EXPIRED) {
                    notifyUser("令牌已过期，请重新登录");
                    stopHeartbeat();
                } else {
                    notifyUser("心跳失败：" + r.msg);
                }
            });
        });
    }

    /* ==================== 3. 单码解绑 ==================== */

    /**
     * 解绑：当前设备登录态失效，卡密可在新设备重新使用。
     * 成功后应停止心跳并清空本地 token。
     */
    public void unbind(final Callback cb) {
        executor.execute(() -> {
            WYUnbindResult r = wy.unbind(kami, markcode);
            mainHandler.post(() -> {
                if (r != null && r.success) {
                    stopHeartbeat();
                    clearToken();
                    cb.onResult("解绑成功，剩余可解绑次数：" + r.remain);
                } else {
                    cb.onResult(r != null && !TextUtils.isEmpty(r.msg) ? "解绑失败：" + r.msg : "解绑失败");
                }
            });
        });
    }

    /* ==================== 4. 公告 ==================== */

    public void getNotice(final Callback cb) {
        executor.execute(() -> {
            WYNoticeResult r = wy.getNotice();
            mainHandler.post(() -> cb.onResult(
                    r != null && r.success ? "公告：" + r.notice : "公告获取失败"));
        });
    }

    /* ==================== 5. 检查更新 ==================== */

    public void checkUpdate(final String currentVersion, final Callback cb) {
        executor.execute(() -> {
            WYVersionResult r = wy.checkUpdate(currentVersion);
            mainHandler.post(() -> {
                if (r != null && r.success) {
                    if (r.hasUpdate) {
                        cb.onResult("发现新版本 " + r.version + "\n" + r.updateshow +
                                (r.updatemust ? "\n（强制更新）" : "") + "\n下载地址：" + r.updateurl);
                    } else {
                        cb.onResult("已是最新版本");
                    }
                } else {
                    cb.onResult(r != null && !TextUtils.isEmpty(r.msg) ? "版本查询失败：" + r.msg : "版本查询失败");
                }
            });
        });
    }

    /* ==================== 6. 释放资源 ==================== */

    /** Activity.onDestroy() 中调用，停止心跳并释放 SDK */
    public void release() {
        stopHeartbeat();
        if (wy != null) {
            wy.destroy();
            wy = null;
        }
        executor.shutdown();
    }

    /* ==================== 工具 ==================== */

    /** 生成设备码：微验由 App 自行提供 markcode，推荐使用 ANDROID_ID */
    private String genMarkcode() {
        try {
            String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return TextUtils.isEmpty(id) ? "0000000000000000" : id.toUpperCase();
        } catch (Throwable t) {
            return "0000000000000000";
        }
    }

    /** token 持久化（SharedPreferences），App 重启后可免登录直接心跳 */
    private void saveToken(String token) {
        getPrefs().edit().putString("wy_token", token).apply();
    }

    private void clearToken() {
        getPrefs().edit().remove("wy_token").apply();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences("wy_sdk_demo", Context.MODE_PRIVATE);
    }

    /** 把消息弹给用户（示例用 Toast，可按需替换成 TextView/回调） */
    private void notifyUser(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    private static String fmtTime(long sec) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(sec * 1000L));
    }
}

package com.example.myapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.weiyan.sdk.WYHeartbeatResult;
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
 * 主页面 - 微验(WY) SDK 测试 APK
 *
 * 功能：账号信息（卡密/设备码/卡类型/剩余次数或到期时间）、公告、检查更新、退出登录
 */
public class MainActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /* 心跳间隔：官方文档建议 10-60 秒请求一次，Token 默认 120 秒过期 */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    /* 状态码：105=数据过期(令牌过期) */
    private static final int CODE_DATA_EXPIRED = 105;

    private final Handler hbHandler = new Handler(Looper.getMainLooper());
    private final Runnable hbTask = new Runnable() {
        @Override
        public void run() {
            doHeartbeat();
            if (hbRunning) hbHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };
    private boolean hbRunning = false;

    private WYVerify wy;
    private String kami = "";
    private String markcode = "";
    private String type = "";
    private long remain;
    private long endTime;
    private String token = "";
    private String appVer = "1.0";
    private boolean destroyed = false;
    private TextView hbStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        kami = sp.getString(LoginActivity.KEY_KAMI, "");
        markcode = sp.getString(LoginActivity.KEY_MARKCODE, "");
        type = sp.getString(LoginActivity.KEY_TYPE, "");
        remain = sp.getLong(LoginActivity.KEY_REMAIN, 0);
        endTime = sp.getLong(LoginActivity.KEY_END_TIME, 0);
        token = sp.getString(LoginActivity.KEY_TOKEN, "");

        if (TextUtils.isEmpty(kami)) {
            backToLogin();
            return;
        }

        // 初始化 SDK（凭证在 .so 中）
        wy = new WYVerify();

        setContentView(buildContent());
    }

    // ========== UI 构建（iOS 分组表格风格） ==========

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF2F2F7);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 导航栏
        root.addView(navBar());

        // 分组一：账号信息
        root.addView(sectionHeader("账号信息"));
        LinearLayout accountGroup = group();
        addInfoCell(accountGroup, "卡密", kami);
        addInfoCell(accountGroup, "设备码", markcode);
        addInfoCell(accountGroup, "卡类型", cardTypeText());
        if ("single".equals(type)) {
            addInfoCell(accountGroup, "剩余次数", String.valueOf(remain));
        } else {
            addInfoCell(accountGroup, "到期时间", endTime > 0 ? fmtTime(endTime) : "—");
        }
        addInfoCell(accountGroup, "登录令牌", token);
        hbStatusView = addInfoCell(accountGroup, "心跳状态", "未开始");
        root.addView(groupContainer(accountGroup));

        // 分组二：功能
        root.addView(sectionHeader("功能"));
        LinearLayout funcGroup = group();
        addActionCell(funcGroup, "公告", true, v -> loadNotice());
        addActionCell(funcGroup, "检查更新", true, v -> checkUpdate());
        addActionCell(funcGroup, "解绑卡密", true, v -> confirmUnbind());
        root.addView(groupContainer(funcGroup));

        // 分组三：其他
        root.addView(sectionHeader("其他"));
        LinearLayout otherGroup = group();
        addInfoCell(otherGroup, "版本", appVer);
        root.addView(groupContainer(otherGroup));

        // 退出登录
        TextView logout = new TextView(this);
        logout.setText("退出登录");
        logout.setTextSize(17);
        logout.setGravity(Gravity.CENTER);
        logout.setTextColor(0xFFFF3B30);
        logout.setBackground(bg(R.drawable.bg_btn_red));
        logout.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(dp(16), dp(24), dp(16), dp(24));
        root.addView(logout, lp);

        return scroll;
    }

    private String cardTypeText() {
        if ("single".equals(type)) return "次数卡";
        if ("code".equals(type)) return "单码";
        if ("timing".equals(type)) return "时长卡";
        return TextUtils.isEmpty(type) ? "—" : type;
    }

    private String fmtTime(long sec) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(sec * 1000L));
        } catch (Throwable t) {
            return String.valueOf(sec);
        }
    }

    private View navBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView left = new TextView(this);
        left.setText("");

        TextView title = new TextView(this);
        title.setText("我的");
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1C1C1E);
        title.setGravity(Gravity.CENTER);

        TextView right = new TextView(this);
        right.setText("");
        right.setTextSize(16);
        right.setTextColor(0xFF007AFF);

        bar.addView(left, new LinearLayout.LayoutParams(dp(60),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(right, new LinearLayout.LayoutParams(dp(60),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 底部分割线
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Color.WHITE);
        wrap.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        View divider = new View(this);
        divider.setBackgroundColor(0xFFE5E5EA);
        wrap.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return wrap;
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF8E8E93);
        tv.setPadding(dp(20), dp(20), dp(20), dp(8));
        return tv;
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        return g;
    }

    private LinearLayout groupContainer(LinearLayout group) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(bg(R.drawable.bg_cell));
        wrap.setPadding(dp(16), 0, dp(16), 0);
        wrap.addView(group, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), 0, dp(16), 0);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private TextView addInfoCell(LinearLayout group, String title, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(0, dp(14), 0, dp(14));
        cell.setBackground(bg(R.drawable.bg_cell_plain));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(0xFF1C1C1E);

        TextView v = new TextView(this);
        v.setText(TextUtils.isEmpty(value) ? "—" : value);
        v.setTextSize(16);
        v.setTextColor(0xFF8E8E93);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        v.setGravity(Gravity.END);

        cell.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(v, new LinearLayout.LayoutParams(dp(220),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        group.addView(cell);
        group.addView(divider());
        return v;
    }

    private void addActionCell(LinearLayout group, String title, boolean arrow,
                               View.OnClickListener listener) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(0, dp(14), 0, dp(14));
        cell.setBackground(bg(R.drawable.bg_cell_plain));
        cell.setOnClickListener(listener);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(0xFF1C1C1E);

        cell.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (arrow) {
            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextSize(20);
            chevron.setTextColor(0xFFC7C7CC);
            chevron.setGravity(Gravity.CENTER);
            cell.addView(chevron, new LinearLayout.LayoutParams(dp(24),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        group.addView(cell);
        group.addView(divider());
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFE5E5EA);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        v.setLayoutParams(lp);
        return v;
    }

    private android.graphics.drawable.Drawable bg(int res) {
        return getDrawable(res);
    }

    private int dp(int v) {
        return Math.round(v * density());
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    // ========== 业务功能 ==========

    private void loadNotice() {
        executor.execute(() -> {
            WYNoticeResult r = wy.getNotice();
            mainHandler.post(() -> {
                if (destroyed) return;
                if (r != null && r.success) {
                    showDialog("公告", r.notice);
                } else {
                    toast(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "公告获取失败");
                }
            });
        });
    }

    private void checkUpdate() {
        executor.execute(() -> {
            WYVersionResult r = wy.checkUpdate(appVer);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (r != null && r.success) {
                    if (r.hasUpdate) {
                        showDialog("发现新版本 " + r.version,
                                TextUtils.isEmpty(r.updateshow) ? "请前往下载更新" : r.updateshow);
                    } else {
                        toast("已是最新版本");
                    }
                } else {
                    toast(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "版本查询失败");
                }
            });
        });
    }

    private void confirmUnbind() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("解绑卡密");
        b.setMessage("解绑后当前设备将失去登录状态，卡密可在新设备重新使用。\n确定要解绑吗？");
        b.setNegativeButton("取消", null);
        b.setPositiveButton("解绑", (d, w) -> doUnbind());
        b.show();
    }

    private void doUnbind() {
        executor.execute(() -> {
            WYUnbindResult r = wy.unbind(kami, markcode);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (r != null && r.success) {
                    showDialog("解绑成功", "剩余可解绑次数：" + r.remain);
                } else {
                    toast(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "解绑失败");
                }
            });
        });
    }

    /* 自动心跳：每 30 秒一次，结果直接刷新到主页"心跳状态"行 */
    private void doHeartbeat() {
        if (destroyed) return;
        if (TextUtils.isEmpty(token)) {
            updateHbStatus("未登录(无令牌)");
            return;
        }
        executor.execute(() -> {
            WYHeartbeatResult r = wy.heartbeat(kami, markcode, token);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (r != null && r.success) {
                    StringBuilder sb = new StringBuilder("正常 · ");
                    sb.append(r.endTime > 0 ? fmtTime(r.endTime) : "无到期时间");
                    if (!TextUtils.isEmpty(r.onlinenum)) sb.append(" · 在线").append(r.onlinenum).append("人");
                    updateHbStatus(sb.toString());
                } else if (r != null && r.code == CODE_DATA_EXPIRED) {
                    /* Token 默认 120 秒过期，过期后需重新登录获取新令牌 */
                    updateHbStatus("令牌过期，请退出重新登录");
                } else {
                    updateHbStatus(r != null && !TextUtils.isEmpty(r.msg) ? r.msg : "心跳失败");
                }
            });
        });
    }

    private void updateHbStatus(String s) {
        if (hbStatusView != null) hbStatusView.setText(TextUtils.isEmpty(s) ? "—" : s);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hbRunning) {
            hbRunning = true;
            hbHandler.postDelayed(hbTask, 2_000L); /* 进入页面先等 2 秒再开始心跳 */
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hbRunning = false;
        hbHandler.removeCallbacks(hbTask);
    }

    private void confirmLogout() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("退出登录");
        b.setMessage("确定要退出当前账号吗？");
        b.setNegativeButton("取消", null);
        b.setPositiveButton("退出", (d, w) -> {
            SharedPreferences sp = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
            sp.edit().clear().apply();
            backToLogin();
        });
        b.show();
    }

    // ========== 工具 ==========

    private void showDialog(String title, String msg) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        b.setMessage(TextUtils.isEmpty(msg) ? "(无内容)" : msg);
        b.setPositiveButton("确定", (d, w) -> d.dismiss());
        b.show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void backToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (wy != null) {
            wy.destroy();
            wy = null;
        }
        executor.shutdown();
        super.onDestroy();
    }
}

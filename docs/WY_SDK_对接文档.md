# 微验(WY)验证 SDK · .so 对接文档

> 版本：v1.1.6 ｜ 架构：arm64-v8a（Android 8.0+）
> 功能：公告、检查更新、单码登录、单码解绑、心跳验证、卡密类型（永久卡/天卡…）识别

---

## 1. 概述

`libwyverify.so` 是微验(WY)验证协议的本地实现，把网络验证的完整流程（请求加密、响应解密、签名校验）封装在 .so 内：

- 应用侧只需传入 **卡密 + 设备码**，即可完成登录、解绑、心跳；
- 验证服务器域名、接口路径、5 个接口调用码、签名密钥、RC4 密钥、Base64 表等**全部以密文编译进 .so**，不暴露给应用层；
- 卡密时长类型（永久卡/天卡/…）的中文映射**集成在 .so 内**，应用不维护任何映射表。

### 1.1 支持的功能

| 功能 | 接口 | 说明 |
|---|---|---|
| 公告 | `getNotice()` | 获取后台公告 |
| 检查更新 | `checkUpdate(ver)` | 获取最新版本号/更新地址/是否强制 |
| 单码登录 | `login(kami, markcode)` | 返回 token / 剩余次数 / 到期时间 |
| 单码解绑 | `unbind(kami, markcode)` | 解绑后原 token 失效 |
| 心跳验证 | `heartbeat(kami, markcode, token)` | 建议 10~60 秒一次，防令牌过期 |

---

## 2. 安全与加固说明（重要）

### 2.1 授权密钥（必须传入）

- **对接密钥：`wanfeng`**
- 构造时必须传入：`new WYVerify("wanfeng")`
- 密钥作用：**对接授权 + 运行期解密**。
  - 校验：.so 内置 `SHA256(wanfeng)` 摘要（单向哈希，不可逆），用传入密钥的摘要比对；
  - 解密：解密密钥 = `SHA256(传入密钥 || 构建随机IV)`，用该密钥 RC4 解密内部常量。
- **密钥错误**：所有接口返回 `success=false, msg="密钥错误"`；即使绕过授权检查，常量也解不出正确值（双重保护）。

### 2.2 构建时自动加密（无明文）

每次构建 .so（含 GitHub Actions 自动构建）都会执行 `android/tools/gen_wy_constants.py`：

1. 生成随机 IV（16 字节），**每次构建产物密文不同**（防重放/比对逆向）；
2. 用派生密钥 RC4 加密：验证域名、接口路径、5 个接口调用码、签名密钥、RC4 密钥、自定义 Base64 表、token 键名；
3. 生成的加密头 `wy_constants_enc.h` 不提交仓库、不进源码。

**实测**：对 release 的 .so 执行 `strings` 扫描，无任何明文链接/调用码/协议密钥/密钥明文。

---

## 3. 集成步骤（Android）

### 3.1 准备文件

把以下内容放进你的 Android 项目：

```
app/src/main/jniLibs/arm64-v8a/libwyverify.so        ← .so 文件
app/src/main/java/com/weiyan/sdk/WYVerify.java       ← 以下 7 个 SDK 类
app/src/main/java/com/weiyan/sdk/WYResult.java
app/src/main/java/com/weiyan/sdk/WYLoginResult.java
app/src/main/java/com/weiyan/sdk/WYHeartbeatResult.java
app/src/main/java/com/weiyan/sdk/WYNoticeResult.java
app/src/main/java/com/weiyan/sdk/WYVersionResult.java
app/src/main/java/com/weiyan/sdk/WYUnbindResult.java
```

> 注意：`libwyverify.so` 是 **arm64-v8a** 架构，需在真机/模拟器 arm64 环境运行；x86 模拟器请自行向 SDK 方索取对应架构产物。

### 3.2 Gradle 配置

```gradle
android {
    defaultConfig {
        ndk {
            abiFilters "arm64-v8a"   // 只打包 arm64，控制 APK 体积
        }
    }
}
```

### 3.3 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 4. 快速开始

```java
import com.weiyan.sdk.WYVerify;
import com.weiyan.sdk.WYLoginResult;

public class Demo {
    // 1. 授权密钥（与 .so 内置一致）
    private static final String SDK_KEY = "wanfeng";

    void login(String kami, String markcode) {
        // 2. 创建实例（必须传密钥）
        WYVerify wy = new WYVerify(SDK_KEY);

        // 3. 登录：kami=卡密，markcode=设备码（推荐用 ANDROID_ID / 自定义设备唯一标识）
        WYLoginResult r = wy.login(kami, markcode);

        if (r.success) {
            String token = r.token;          // 登录令牌，心跳用
            String typeName = r.kmtypeName;  // 卡密类型中文名：永久卡/天卡/次数卡…
            long remain = r.remain;          // 剩余次数（次数卡）
            long endTime = r.endTime;        // 到期时间戳（时长卡）
            // …… 保存 token 到本地，后续心跳使用
        } else {
            // r.msg 为失败原因
        }

        // 4. 不再使用时释放
        wy.destroy();
    }
}
```

> 设备码建议使用持久化、且能标识单台设备的字符串（如 `Settings.Secure.ANDROID_ID`），同一卡密换设备登录会触发后台的绑定/限制策略。

---

## 5. API 参考

### 5.1 类 `WYVerify`

| 方法 | 返回 | 说明 |
|---|---|---|
| `WYVerify(String key)` | — | 构造，`key` 必须为授权密钥 `wanfeng`，否则全部接口失败 |
| `WYNoticeResult getNotice()` | 公告 | 获取后台公告内容 |
| `WYVersionResult checkUpdate(String currentVersion)` | 版本 | 检查更新，传当前版本号 |
| `WYLoginResult login(String kami, String markcode)` | 登录 | 单码登录 |
| `WYUnbindResult unbind(String kami, String markcode)` | 解绑 | 单码解绑 |
| `WYHeartbeatResult heartbeat(String kami, String markcode, String kamitoken)` | 心跳 | 心跳验证，`kamitoken` 为登录返回的 token |
| `void destroy()` | — | 释放底层资源 |

### 5.2 基类 `WYResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 是否成功 |
| `msg` | String | 失败时的消息（如"密钥错误"/"卡密不存在"） |

### 5.3 `WYLoginResult`（登录）

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | String | ktype：`code`=单码 / `single`=次数卡 |
| `kmtype` | String | 卡密时长类型标识：`free/hour/day/week/month/season/year/longuse/single` |
| `kmtypeName` | String | **卡密类型中文名**（由 .so 生成）：免费卡/时卡/天卡/周卡/月卡/季卡/年卡/永久卡/次数卡 |
| `code` | long | 服务器返回 code（`11242` 成功） |
| `remain` | long | 剩余可登录次数（次数卡） |
| `endTime` | long | 到期时间戳(秒)（时长卡） |
| `token` | String | 登录令牌 `msg.token`，**心跳验证必传** |

### 5.4 `WYHeartbeatResult`（心跳）

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | long | 服务器返回 code（`200` 成功；`105`=数据过期/令牌失效） |
| `endTime` | long | 到期时间戳(秒) |
| `type` | String | 卡密类型 |
| `timetype` | String | 卡密时长类型标识 |
| `timetypeName` | String | **卡密类型中文名**（由 .so 生成） |
| `onlinenum` | String | 在线人数 |
| `check` | String | 数据校验值 |

> 心跳返回的 `timetype` 是权威来源：若登录响应未含卡密类型，可在首次心跳成功后用 `timetypeName` 刷新"卡类型"显示。

### 5.5 `WYUnbindResult`（解绑）

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | long | 服务器返回 code（`200` 成功） |
| `remain` | long | 剩余可解绑次数 `msg.num` |

### 5.6 `WYNoticeResult`（公告）

| 字段 | 类型 | 说明 |
|---|---|---|
| `notice` | String | 公告内容 |

### 5.7 `WYVersionResult`（检查更新）

| 字段 | 类型 | 说明 |
|---|---|---|
| `hasUpdate` | boolean | 是否有新版本 |
| `version` | String | 最新版本号 |
| `updateshow` | String | 更新说明 |
| `updateurl` | String | 下载地址 |
| `updatemust` | boolean | 是否强制更新 |

---

## 6. 卡密时长类型（集成在 .so 内）

| kmtype / timetype | 中文名 | 说明 |
|---|---|---|
| `free` | 免费卡 | |
| `hour` | 时卡 | |
| `day` | 天卡 | |
| `week` | 周卡 | |
| `month` | 月卡 | |
| `season` | 季卡 | |
| `year` | 年卡 | |
| `longuse` | 永久卡 | |
| `single` | 次数卡 | 按次数计 |

> 该映射表在 .so 内部实现，应用层拿到的永远是中文名（`kmtypeName` / `timetypeName`），**应用无需、也不应在 Java 层再维护映射**。

---

## 7. 完整示例（登录 + 心跳 + 解绑）

```java
public class WyManager {
    private static final String SDK_KEY = "wanfeng";
    private WYVerify wy;
    private String kami, markcode, token;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable heartbeatTask;

    public void start(String kami, String markcode) {
        this.kami = kami;
        this.markcode = markcode;
        wy = new WYVerify(SDK_KEY);            // 必须传密钥

        WYLoginResult r = wy.login(kami, markcode);
        if (r.success) {
            token = r.token;
            startHeartbeat();                  // 登录成功后启动心跳
        } else {
            // r.msg 处理登录失败
        }
    }

    /** 心跳：官方建议 10~60 秒一次，token 默认 120 秒过期 */
    private void startHeartbeat() {
        heartbeatTask = new Runnable() {
            @Override public void run() {
                WYHeartbeatResult hb = wy.heartbeat(kami, markcode, token);
                if (hb.success) {
                    // hb.timetypeName 刷新卡类型显示；hb.endTime 刷新到期时间
                } else if (hb.code == 105) {
                    // 令牌过期：需重新登录
                }
                handler.postDelayed(this, 30_000L);
            }
        };
        handler.postDelayed(heartbeatTask, 30_000L);
    }

    public void unbindNow() {
        WYUnbindResult u = wy.unbind(kami, markcode);
        // u.success / u.remain 处理
        stopHeartbeat();
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) handler.removeCallbacks(heartbeatTask);
    }

    public void release() {
        stopHeartbeat();
        if (wy != null) wy.destroy();
    }
}
```

---

> 完整可复制的对接示例（含全部 5 个接口、自动心跳、Token 持久化、资源释放）见同目录 **[WY_SDK_对接示例.java](WY_SDK_对接示例.java)**。

---

## 8. 常见问题

**Q1：调用任何接口都返回 `msg=密钥错误`？**
构造时传入的密钥不是 `wanfeng`。必须 `new WYVerify("wanfeng")`。

**Q2：登录返回 `success=true`，但"卡类型"为空？**
登录响应可能不含卡密时长类型（后台配置不同）。属正常现象：心跳成功后 .so 会用 `timetypeName`（如"永久卡"）补全显示。

**Q3：心跳返回 `code=105`？**
令牌过期。需要携带原卡密重新 `login()` 获取新 token，再继续心跳。

**Q4：能在 x86 模拟器上跑吗？**
不能。当前产物为 `arm64-v8a`，需 arm64 真机/模拟器，或向 SDK 方索取 x86_64 产物。

**Q5：.so 里会不会被看到链接和调用码？**
不会。每次构建都会重新加密（随机 IV + RC4），链接/调用码/协议密钥全部密文，`strings` 扫描无明文。

**Q6：同设备重装后 token 丢了怎么办？**
token 建议持久化（SharedPreferences）。丢失后重新登录即可，不影响使用。

---

## 9. 版本历史

| 版本 | 说明 |
|---|---|
| v1.1.0 ~ v1.1.4 | 登录/公告/更新/解绑/心跳基础对接；卡密类型 .so 中文映射 |
| v1.1.5 | 敏感信息加密：链接/调用码 XOR 加密进 .so；引入密钥授权调用 |
| v1.1.6 | 加固升级：解密密钥 = SHA256(wanfeng‖随机IV)，RC4 加密，每次构建密文不同；wanfeng 仅存 SHA256 摘要 |

---

*文档对应仓库：`wanfeng090525/weiyan-sdk`（.so 与测试 APK 见 GitHub Release）*

/**
 * 微验(WY)验证 SDK - 核心实现
 */

#include "wyverify.h"
#include <cstdlib>
#include <chrono>
#include <random>

namespace wy {

/* 发送微验请求：params 拼上 id 后整体走 8 层编码 */
std::string WYVerify::post(const std::string &id, const std::string &params) {
    std::string full = "id=" + id;
    if (!params.empty()) full += "&" + params;
    return wy_httppost(WY_HOST, WY_PATH, wy_encode_request(full));
}

/* JSON 值转字符串：后台部分字段可能返回数字（如 type/onlinenum），
 * 用 nlohmann 的 value("", "") 会抛 type_error，这里统一兼容 */
static std::string wy_json_to_str(const wy_json &v) {
    if (v.is_string()) return v.get<std::string>();
    if (v.is_number_integer()) return std::to_string(v.get<long long>());
    if (v.is_number_unsigned()) return std::to_string(v.get<unsigned long long>());
    if (v.is_number_float()) return std::to_string(v.get<double>());
    return "";
}

/* 从登录响应 msg 中提取卡密时长类型(kmtype)：
 * 键名后台可自定义，按官方文档《卡密时长类型》的标识集合做启发式扫描 */
static std::string wy_extract_kmtype(const wy_json &msg, const std::string &ktype_key) {
    if (!msg.is_object()) return "";
    static const char *kKmTypes[] = {
        "free", "hour", "day", "week", "month", "season", "year", "longuse", "single"
    };
    for (auto it = msg.begin(); it != msg.end(); ++it) {
        if (!it.value().is_string()) continue;
        std::string v = it.value().get<std::string>();
        if (it.key() == ktype_key) continue;   /* 跳过卡密类型 ktype */
        for (const char *k : kKmTypes) {
            if (v == k) return v;
        }
    }
    return "";
}

/* 从登录响应 msg 中提取 token：
 * 键名后台可自定义，先按配置键名取，失败则跳过 ktype/校验值做启发式扫描 */
static std::string wy_extract_token(const wy_json &msg) {
    if (!msg.is_object()) return "";
    if (msg.contains(WY_KEY_TOKEN) && msg[WY_KEY_TOKEN].is_string())
        return msg[WY_KEY_TOKEN].get<std::string>();
    static const std::string ktype_key = "u1686821dd22b8b848108aa079f8dab50";
    for (auto it = msg.begin(); it != msg.end(); ++it) {
        if (!it.value().is_string()) continue;
        std::string v = it.value().get<std::string>();
        if (it.key() == ktype_key) continue;              /* 跳过卡密类型 */
        if (v.length() == 32 && v.find_first_not_of("0123456789abcdef") == std::string::npos)
            continue;                                     /* 跳过 md5 校验值 */
        return v;
    }
    return "";
}

/* ========== 公告 ========== */

WYNoticeResult WYVerify::getNotice() {
    WYNoticeResult r;
    std::string body = post(WY_ID_NOTICE, "");
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }
    try {
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);
        if (j.contains("msg") && j["msg"].is_object() && j["msg"].contains("app_gg")) {
            r.notice = j["msg"]["app_gg"].get<std::string>();
        }
        r.success = true;
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 检查更新 ========== */

WYVersionResult WYVerify::checkUpdate(const std::string &currentVersion) {
    WYVersionResult r;
    std::string body = post(WY_ID_UPDATE, "");
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }
    try {
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);
        if (j.contains("msg") && j["msg"].is_object()) {
            const wy_json &m = j["msg"];
            r.version = m.value("version", "");
            r.updateshow = m.value("updateshow", "");
            r.updateurl = m.value("updateurl", "");
            r.updatemust = (m.value("updatemust", "n") == "y");
            if (!r.version.empty() && r.version != currentVersion) {
                r.hasUpdate = true;
            }
        }
        r.success = true;
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 单码登录 ========== */

WYLoginResult WYVerify::login(const std::string &kami, const std::string &markcode) {
    WYLoginResult r;

    /* 时间戳 + 随机数 */
    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* sign = md5("kami=...&markcode=...&t=...&" + SIGN_KEY) */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t + "&" + WY_SIGN_KEY);

    /* 组装参数并发送 */
    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&value=" + value;
    std::string body = post(WY_ID_LOGIN, params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        std::string plain = wy_decode_login_response(body);
        wy_json j = wy_json::parse(plain);

        long code = j.value("yab45d10cdcfdb028840375669da177a2", (long)-1);
        r.code = code;

        /* 失败：d7ae7b... 为消息字符串 */
        if (code != 11242) {
            if (j["d7ae7b0247872bf8e70e25b4c01e5f2b5"].is_string())
                r.msg = j["d7ae7b0247872bf8e70e25b4c01e5f2b5"].get<std::string>();
            else
                r.msg = "登录失败(code=" + std::to_string(code) + ")";
            return r;
        }

        const wy_json &data = j["d7ae7b0247872bf8e70e25b4c01e5f2b5"];
        long id = data.value("ie66a556e80e2f031a6f0217c909de76b", (long)0);

        /* 服务器校验（参考源码中的三重校验，均通过才认为成功） */
        std::string v1 = data.value("q6704c7537bf2", "");
        std::string v2 = data.value("wfe71da0479bd", "");
        std::string v3 = data.value("ybceae94482323a", "");

        std::string calc1 = wy_md5(wy_sha1(WY_CHECK_TEXT + std::to_string(code) + std::to_string(id)));
        std::string calc2 = wy_md5(wy_sha1(t + std::to_string(id)));
        std::string calc3 = wy_sha1(wy_sha1(sign + sign + WY_CHECK_TEXT + std::to_string(code)));

        if (v1 != calc1 || v2 != calc2 || v3 != calc3) {
            r.msg = "服务器校验失败";
            return r;
        }

        r.type = data.value("u1686821dd22b8b848108aa079f8dab50", "");
        r.kmtype = wy_extract_kmtype(data, "u1686821dd22b8b848108aa079f8dab50");
        if (r.type == "single") {
            r.remain = data.value("c27f13333b755643373328a41fc2c2be1", (long)0);
        } else {
            r.endTime = data.value("s88c959deb8c303e8c81faa52ea5d9e87", (long)0);
        }
        r.token = wy_extract_token(data);
        r.success = true;
        r.msg = "登录成功";
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 单码解绑 ========== */

WYUnbindResult WYVerify::unbind(const std::string &kami, const std::string &markcode) {
    WYUnbindResult r;

    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* 与登录同签名公式 */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t + "&" + WY_SIGN_KEY);

    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&value=" + value;
    std::string body = post(WY_ID_UNBIND, params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        /* 实测解绑响应与登录同一套解密 */
        std::string plain = wy_decode_login_response(body);
        wy_json j = wy_json::parse(plain);

        r.code = j.value("code", (long)-1);
        if (r.code == 200 || r.code == 11242) {
            if (j.contains("msg") && j["msg"].is_object())
                r.remain = j["msg"].value("num", (long)0);
            r.success = true;
            r.msg = "解绑成功";
        } else {
            if (j.contains("msg") && j["msg"].is_string())
                r.msg = j["msg"].get<std::string>();
            else
                r.msg = "解绑失败(code=" + std::to_string(r.code) + ")";
        }
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

/* ========== 心跳验证 ========== */

WYHeartbeatResult WYVerify::heartbeat(const std::string &kami, const std::string &markcode,
                                      const std::string &kamitoken) {
    WYHeartbeatResult r;

    auto now = std::chrono::system_clock::now();
    long timestamp = std::chrono::duration_cast<std::chrono::seconds>(
                         now.time_since_epoch()).count();
    std::string t = std::to_string(timestamp);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dist(100000, 999999);
    std::string value = std::to_string(dist(gen));

    /* 心跳签名需带上 kamitoken */
    std::string sign = wy_md5("kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                              "&kamitoken=" + kamitoken + "&" + WY_SIGN_KEY);

    std::string params = "kami=" + kami + "&markcode=" + markcode + "&t=" + t +
                         "&sign=" + sign + "&kamitoken=" + kamitoken + "&value=" + value;
    std::string body = post(WY_ID_HEARTBEAT, params);
    if (body.empty()) {
        r.msg = "请求失败";
        return r;
    }

    try {
        /* 实测心跳响应与公告/更新同一套解密 */
        std::string plain = wy_decode_notice_response(body);
        wy_json j = wy_json::parse(plain);

        r.code = j.value("code", (long)-1);
        if (r.code == 200 || r.code == 11242) {
            if (j.contains("msg") && j["msg"].is_object()) {
                const wy_json &m = j["msg"];
                r.endTime   = m.value("endtime", (long)0);
                /* 后台可能以数字返回这些字段，用兼容助手读取 */
                if (m.contains("type"))      r.type      = wy_json_to_str(m["type"]);
                if (m.contains("timetype"))  r.timetype  = wy_json_to_str(m["timetype"]);
                if (m.contains("onlinenum")) r.onlinenum = wy_json_to_str(m["onlinenum"]);
                if (m.contains("check"))     r.check     = wy_json_to_str(m["check"]);
            }
            r.success = true;
            r.msg = "心跳成功";
        } else {
            if (j.contains("msg") && j["msg"].is_string())
                r.msg = j["msg"].get<std::string>();
            else
                r.msg = "心跳失败(code=" + std::to_string(r.code) + ")";
        }
    } catch (const std::exception &e) {
        r.msg = std::string("解析失败: ") + e.what();
    }
    return r;
}

} /* namespace wy */

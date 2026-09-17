/**
 * 微验(WY)验证 SDK - 核心类
 *
 * 实现公告获取、版本检查、单码登录，协议复刻自微验 C++ v2 源码。
 * 所有凭证/密钥硬编码在 wy_util.h 中，编译进 .so。
 */

#ifndef WYVERIFY_H
#define WYVERIFY_H

#include <string>
#include <ctime>
#include "json.hpp"
#include "wy_util.h"

using wy_json = nlohmann::json;

namespace wy {

/* ========== 结果结构体 ========== */

struct WYResult {
    bool success = false;
    std::string msg;   /* 失败时服务器返回的消息 */
};

struct WYNoticeResult {
    bool success = false;
    std::string msg;
    std::string notice;
};

struct WYVersionResult {
    bool success = false;
    std::string msg;
    bool hasUpdate = false;
    std::string version;
    std::string updateshow;
    std::string updateurl;
    bool updatemust = false;
};

struct WYLoginResult {
    bool success = false;
    std::string msg;          /* 失败消息 */
    std::string type;         /* single / timing */
    long code = 0;            /* 服务器 code（11242 成功） */
    long remain = 0;          /* single 类型：剩余可登录次数 */
    long endTime = 0;         /* timing 类型：到期时间戳 */
};

/* ========== 核心类 ========== */

class WYVerify {
public:
    WYVerify() = default;

    /* 获取公告 */
    WYNoticeResult getNotice();

    /* 检查更新，currentVersion 为客户端当前版本号 */
    WYVersionResult checkUpdate(const std::string &currentVersion);

    /* 单码登录，markcode 为设备码 */
    WYLoginResult login(const std::string &kami, const std::string &markcode);

private:
    /* 发送微验请求并返回原始响应体 */
    std::string post(const std::string &id, const std::string &params);
};

} /* namespace wy */

#endif /* WYVERIFY_H */

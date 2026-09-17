/**
 * 微验 SDK - 本地协议测试（不依赖 NDK）
 * 用法: g++ -std=c++17 -O2 -I../android/jni main.cpp ../android/jni/weiyan/wyverify.cpp -o wy_test
 */
#include <iostream>
#include "../android/jni/weiyan/wyverify.h"

using namespace std;

int main() {
    wy::WYVerify v;

    cout << "========== 1. 获取公告 ==========" << endl;
    auto notice = v.getNotice();
    cout << "success: " << notice.success << endl;
    cout << "msg: " << notice.msg << endl;
    cout << "notice: " << notice.notice << endl;

    cout << "\n========== 2. 检查更新 ==========" << endl;
    auto ver = v.checkUpdate("1.0");
    cout << "success: " << ver.success << endl;
    cout << "msg: " << ver.msg << endl;
    cout << "hasUpdate: " << ver.hasUpdate << endl;
    cout << "version: " << ver.version << endl;
    cout << "updateshow: " << ver.updateshow << endl;
    cout << "updateurl: " << ver.updateurl << endl;
    cout << "updatemust: " << ver.updatemust << endl;

    cout << "\n========== 3. 单码登录(无效卡密测试解密) ==========" << endl;
    auto lg = v.login("test-kami-000", "test-device-000");
    cout << "success: " << lg.success << endl;
    cout << "msg: " << lg.msg << endl;
    cout << "code: " << lg.code << endl;
    cout << "type: " << lg.type << endl;
    cout << "remain: " << lg.remain << endl;
    cout << "endTime: " << lg.endTime << endl;
    cout << "token: " << lg.token << endl;

    cout << "\n========== 4. 单码解绑(无效卡密测试解密) ==========" << endl;
    auto ub = v.unbind("test-kami-000", "test-device-000");
    cout << "success: " << ub.success << endl;
    cout << "msg: " << ub.msg << endl;
    cout << "code: " << ub.code << endl;
    cout << "remain: " << ub.remain << endl;

    cout << "\n========== 5. 心跳验证(无效token测试解密) ==========" << endl;
    auto hb = v.heartbeat("test-kami-000", "test-device-000", "test-token-000");
    cout << "success: " << hb.success << endl;
    cout << "msg: " << hb.msg << endl;
    cout << "code: " << hb.code << endl;
    cout << "endTime: " << hb.endTime << endl;
    cout << "type: " << hb.type << endl;
    cout << "timetype: " << hb.timetype << endl;
    cout << "onlinenum: " << hb.onlinenum << endl;

    return 0;
}

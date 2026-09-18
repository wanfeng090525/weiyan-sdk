/**
 * 微验协议编码/解码工具（本地验证用，走 HTTP 代理配合 curl）
 *
 * 用法:
 *   wy_proto encode "<id>,<params>"   -> 输出 8 层编码后的 POST 参数
 *   wy_proto notice <响应文件>         -> 公告/更新响应解密为 JSON
 *   wy_proto login  <响应文件>         -> 登录响应解密为 JSON
 */
#include <iostream>
#include <fstream>
#include <sstream>
#include "../android/jni/weiyan/wy_util.h"

using namespace std;

int main(int argc, char **argv) {
    /* 本地调试工具：注入授权密钥派生解密密钥（.so 内由 WYVerify::init 完成） */
    wy_set_dk("wanfeng");

    if (argc < 2) {
        cerr << "usage: wy_proto encode|notice|login ..." << endl;
        return 1;
    }
    string op = argv[1];
    if (op == "encode" && argc >= 3) {
        // argv[2] = "id,params"
        string full = argv[2];
        size_t comma = full.find(',');
        string id = full.substr(0, comma);
        string params = (comma == string::npos) ? "" : full.substr(comma + 1);
        string p = "id=" + id + (params.empty() ? "" : "&" + params);
        cout << wy_encode_request(p) << endl;
    } else if (op == "notice" && argc >= 3) {
        ifstream f(argv[2]);
        stringstream ss; ss << f.rdbuf();
        cout << wy_decode_notice_response(ss.str()) << endl;
    } else if (op == "login" && argc >= 3) {
        ifstream f(argv[2]);
        stringstream ss; ss << f.rdbuf();
        cout << wy_decode_login_response(ss.str()) << endl;
    }
    return 0;
}

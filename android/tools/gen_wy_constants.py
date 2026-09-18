#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""微验(WY) SDK 常量加密生成器

每次构建 .so 前运行：把网络验证链接、接口调用码、协议密钥等明文常量
用授权密钥 "wanfeng" 逐字节 XOR 加密，生成 wy_constants_enc.h。
加密后的 .so 二进制内不包含任何明文链接/调用码/协议密钥。

用法:
    python3 tools/gen_wy_constants.py
输出:
    android/jni/weiyan/wy_constants_enc.h   (自动生成，勿手改)
"""
import os

# 授权密钥：对接方必须传入该密钥才能调用 .so（与 C++ 侧 wy_auth_key 一致）
KEY = "wanfeng"
# 密钥自身混淆字节：密钥先用该字节 XOR 后以 hex 存储，避免 .so 中出现明文密钥
XOR_BYTE = 0xA5

# 明文常量表：(宏后缀, 明文值, 注释)
# 注意：此处是源码明文清单，编译产物中全部为密文
CONSTS = [
    ("HOST",            "wy.llua.cn",                         "验证服务器域名"),
    ("PATH",            "v2/671c3381301b6f153f4d80ac40035687", "验证接口路径"),
    ("SIGN_KEY",        "v71911e38905280a39cde76182a7f3f9",    "sign 尾部拼接密钥"),
    ("CHECK_TEXT",      "v71911e38905280a39cde76182a7f3f9",    "响应校验文本"),
    ("NOTICE_XOR_KEY",  "o1575eb3e5b1023986c5f5a320b03e8",     "公告/更新响应 RC4 密钥"),
    ("LOGIN_RC4_KEY1",  "s152f82315f5467e54e94",               "登录响应第一层 RC4 密钥"),
    ("LOGIN_RC4_KEY2",  "l522705c8a2c827ff761d8773",           "登录响应第二层 RC4 密钥"),
    ("REQ_RC4_KEY1",    "q77feae6b8446586fb5b7",               "请求第一层 RC4 密钥"),
    ("REQ_CUSTOM_B64",  "fOrYsXDLKjTzilcw6bn321paIxNBetV95MvohAQq+UZRmu/gCyEH0k874SJFWGdP", "请求自定义 Base64 表"),
    ("REQ_RC4_KEY2",    "c5f939f2cfd9fd7c21aec26b60390",       "请求第二层 RC4 密钥"),
    ("ID_NOTICE",       "ms0mYguHG2G",                         "公告接口调用码"),
    ("ID_UPDATE",       "9PcyLozlM4Y",                         "更新接口调用码"),
    ("ID_LOGIN",        "4ooszUNauTB",                         "登录接口调用码"),
    ("ID_UNBIND",       "4210AA536BA",                         "解绑接口调用码"),
    ("ID_HEARTBEAT",    "F482D033AE0",                         "心跳接口调用码"),
    ("KEY_TOKEN",       "token",                               "登录响应 token 键名"),
]


def enc(plain: str) -> str:
    """明文常量 XOR 密钥后的 hex 字符串"""
    kb = KEY.encode()
    out = bytearray()
    for i, b in enumerate(plain.encode()):
        out.append(b ^ kb[i % len(kb)])
    return out.hex()


def enc_key() -> str:
    """密钥自身混淆：逐字节 XOR XOR_BYTE 后 hex"""
    return bytes(b ^ XOR_BYTE for b in KEY.encode()).hex()


def gen(out_path: str) -> None:
    lines = []
    lines.append("/* ============================================================")
    lines.append(" * 微验(WY) SDK - 加密常量（自动生成，勿手改）")
    lines.append(" * 由 tools/gen_wy_constants.py 用授权密钥加密生成，")
    lines.append(" * .so 二进制内不含任何明文链接/调用码/协议密钥。")
    lines.append(" * ============================================================ */")
    lines.append("#ifndef WY_CONSTANTS_ENC_H")
    lines.append("#define WY_CONSTANTS_ENC_H")
    lines.append("")
    lines.append("/* 授权密钥混淆字节：wanfeng 逐字节 XOR 该字节后以 hex 存储 */")
    lines.append("#define WY_AUTH_KEY_XOR_BYTE 0x%02X" % XOR_BYTE)
    lines.append("#define WY_AUTH_KEY_HEX \"%s\"" % enc_key())
    lines.append("")
    for name, plain, comment in CONSTS:
        lines.append("/* %s */" % comment)
        lines.append("#define WY_ENC_%s \"%s\"" % (name, enc(plain)))
        lines.append("")
    lines.append("#endif /* WY_CONSTANTS_ENC_H */")
    content = "\n".join(lines) + "\n"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("generated: %s (%d consts, key=%s)" % (out_path, len(CONSTS), "wanfeng"))


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.normpath(os.path.join(here, "..", "jni", "weiyan", "wy_constants_enc.h"))
    gen(out)

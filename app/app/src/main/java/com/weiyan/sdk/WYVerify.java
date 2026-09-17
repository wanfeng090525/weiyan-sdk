package com.weiyan.sdk;

/**
 * 微验(WY)验证 SDK - Java 调用入口
 *
 * 用法:
 *   WYVerify v = new WYVerify();
 *   WYNoticeResult  notice = v.getNotice();
 *   WYVersionResult ver    = v.checkUpdate("1.0");
 *   WYLoginResult   login  = v.login(kami, markcode);   // markcode 为设备码
 *   v.destroy();
 *
 * 说明: 所有凭证/密钥都编译在 libwyverify.so 内，本类不含任何密钥。
 */
public class WYVerify {

    static {
        System.loadLibrary("wyverify");
    }

    private long handle;

    public WYVerify() {
        handle = nativeCreate();
    }

    /** 获取公告 */
    public WYNoticeResult getNotice() {
        return nativeGetNotice(handle);
    }

    /** 检查更新，currentVersion 为客户端当前版本号 */
    public WYVersionResult checkUpdate(String currentVersion) {
        return nativeCheckUpdate(handle, currentVersion);
    }

    /** 单码登录，markcode 为设备码 */
    public WYLoginResult login(String kami, String markcode) {
        return nativeLogin(handle, kami, markcode);
    }

    /** 释放底层资源 */
    public void destroy() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    /* ========== JNI ========== */
    private static native long nativeCreate();
    private static native void nativeDestroy(long handle);
    private static native WYNoticeResult nativeGetNotice(long handle);
    private static native WYVersionResult nativeCheckUpdate(long handle, String currentVersion);
    private static native WYLoginResult nativeLogin(long handle, String kami, String markcode);
}

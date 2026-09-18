package com.weiyan.sdk

/**
 * 微验(WY)验证 SDK - Kotlin 调用入口
 *
 * 用法:
 *   val v = WYVerify("wanfeng")                       // 必须传入授权密钥才能调用
 *   val notice: WYNoticeResult? = v.getNotice()
 *   val ver: WYVersionResult?    = v.checkUpdate("1.0")
 *   val login: WYLoginResult?    = v.login(kami, markcode)     // markcode 为设备码
 *   val unbind: WYUnbindResult?  = v.unbind(kami, markcode)    // 解绑后登录态失效
 *   val hb: WYHeartbeatResult?   = v.heartbeat(kami, markcode, login.token)
 *   v.destroy()
 *
 * 说明: 网络验证链接/接口调用码/协议密钥均以密文编译在 libwyverify.so 内，
 * 调用方必须传入授权密钥，密钥错误时所有接口返回失败（msg=密钥错误）。
 */
class WYVerify(private val key: String) {

    private var handle: Long = 0

    init {
        handle = nativeCreate(key)
    }

    /** 获取公告 */
    fun getNotice(): WYNoticeResult? = nativeGetNotice(handle)

    /** 检查更新，currentVersion 为客户端当前版本号 */
    fun checkUpdate(currentVersion: String): WYVersionResult? =
        nativeCheckUpdate(handle, currentVersion)

    /** 单码登录，markcode 为设备码 */
    fun login(kami: String, markcode: String): WYLoginResult? =
        nativeLogin(handle, kami, markcode)

    /** 单码解绑，markcode 为设备码 */
    fun unbind(kami: String, markcode: String): WYUnbindResult? =
        nativeUnbind(handle, kami, markcode)

    /** 心跳验证，kamitoken 为登录返回的 msg.token */
    fun heartbeat(kami: String, markcode: String, kamitoken: String): WYHeartbeatResult? =
        nativeHeartbeat(handle, kami, markcode, kamitoken)

    /** 释放底层资源 */
    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private companion object {
        init {
            System.loadLibrary("wyverify")
        }

        /* ========== JNI ========== */
        @JvmStatic external fun nativeCreate(key: String): Long
        @JvmStatic external fun nativeDestroy(handle: Long)
        @JvmStatic external fun nativeGetNotice(handle: Long): WYNoticeResult?
        @JvmStatic external fun nativeCheckUpdate(handle: Long, currentVersion: String): WYVersionResult?
        @JvmStatic external fun nativeLogin(handle: Long, kami: String, markcode: String): WYLoginResult?
        @JvmStatic external fun nativeUnbind(handle: Long, kami: String, markcode: String): WYUnbindResult?
        @JvmStatic external fun nativeHeartbeat(handle: Long, kami: String, markcode: String, kamitoken: String): WYHeartbeatResult?
    }
}

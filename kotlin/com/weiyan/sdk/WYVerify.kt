package com.weiyan.sdk

/* ====================================================================
 * 微验(WY)验证 SDK - Kotlin 单文件版（7 个类全部在此文件内）
 *
 * 用法:
 *   val v = WYVerify("wanfeng")                       // 必须传入授权密钥才能调用
 *   val notice: WYNoticeResult? = v.getNotice()
 *   val ver: WYVersionResult?   = v.checkUpdate("1.0")
 *   val login: WYLoginResult?   = v.login(kami, markcode)     // markcode 为设备码
 *   val unbind: WYUnbindResult? = v.unbind(kami, markcode)    // 解绑后登录态失效
 *   val hb: WYHeartbeatResult?  = v.heartbeat(kami, markcode, login.token)
 *   v.destroy()
 *
 * 说明: 网络验证链接/接口调用码/协议密钥均以密文编译在 libwyverify.so 内，
 * 调用方必须传入授权密钥，密钥错误时所有接口返回失败（msg=密钥错误）。
 * 卡密时长类型中文名（永久卡/天卡/...）由 .so 内映射生成。
 * ==================================================================== */

/** 微验结果基类 */
open class WYResult {
    /** 是否成功 */
    var success: Boolean = false
    /** 失败时的消息（如"密钥错误"/"卡密不存在"） */
    var msg: String = ""
}

/** 单码登录结果 */
class WYLoginResult : WYResult() {
    /** ktype：code=单码 / single=次数卡 */
    var type: String = ""
    /** kmtype 卡密时长类型：free/hour/day/week/month/season/year/longuse/single */
    var kmtype: String = ""
    /** kmtype 中文显示名（永久卡/天卡/...），由 .so 内映射生成 */
    var kmtypeName: String = ""
    /** 服务器返回 code（11242 成功） */
    var code: Long = 0
    /** single 类型：剩余可登录次数 */
    var remain: Long = 0
    /** timing 类型：到期时间戳(秒) */
    var endTime: Long = 0
    /** 登录令牌 msg.token（心跳验证用） */
    var token: String = ""
}

/** 心跳验证结果 */
class WYHeartbeatResult : WYResult() {
    /** 服务器返回 code（200 成功；105=数据过期/令牌失效） */
    var code: Long = 0
    /** msg.endtime 到期时间(秒) */
    var endTime: Long = 0
    /** msg.type 卡密类型(单码:code，次数卡:single) */
    var type: String = ""
    /** msg.timetype 卡密时长类型 */
    var timetype: String = ""
    /** msg.timetype 中文显示名（永久卡/天卡/...），由 .so 内映射生成 */
    var timetypeName: String = ""
    /** msg.onlinenum 在线人数 */
    var onlinenum: String = ""
    /** msg.check 数据校验值 */
    var check: String = ""
}

/** 单码解绑结果 */
class WYUnbindResult : WYResult() {
    /** 服务器返回 code（200 成功） */
    var code: Long = 0
    /** 剩余可解绑次数 msg.num */
    var remain: Long = 0
}

/** 公告结果 */
class WYNoticeResult : WYResult() {
    /** 公告内容 */
    var notice: String = ""
}

/** 版本检查结果 */
class WYVersionResult : WYResult() {
    /** 是否有新版本 */
    var hasUpdate: Boolean = false
    /** 最新版本号 */
    var version: String = ""
    /** 更新说明 */
    var updateshow: String = ""
    /** 下载地址 */
    var updateurl: String = ""
    /** 是否强制更新 */
    var updatemust: Boolean = false
}

/**
 * 微验(WY)验证 SDK - Kotlin 调用入口
 *
 * @param key 授权密钥（对接密钥），错误时无法调用任何接口
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

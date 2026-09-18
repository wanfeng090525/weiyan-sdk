package com.weiyan.sdk

/** 微验结果基类 */
open class WYResult {
    /** 是否成功 */
    var success: Boolean = false
    /** 失败时的消息（如"密钥错误"/"卡密不存在"） */
    var msg: String = ""
}

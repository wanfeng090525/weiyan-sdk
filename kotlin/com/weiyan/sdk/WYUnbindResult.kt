package com.weiyan.sdk

/** 单码解绑结果 */
class WYUnbindResult : WYResult() {
    /** 服务器返回 code（200 成功） */
    var code: Long = 0
    /** 剩余可解绑次数 msg.num */
    var remain: Long = 0
}

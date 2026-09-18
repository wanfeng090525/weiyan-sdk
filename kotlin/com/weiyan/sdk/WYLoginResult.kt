package com.weiyan.sdk

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

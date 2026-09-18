package com.weiyan.sdk

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

package com.weiyan.sdk;

/** 单码登录结果 */
public class WYLoginResult extends WYResult {
    /** single=次数卡 / timing=时长卡 */
    public String type = "";
    /** 服务器返回 code（11242 成功） */
    public long code;
    /** single 类型：剩余可登录次数 */
    public long remain;
    /** timing 类型：到期时间戳(秒) */
    public long endTime;
}

package com.weiyan.sdk

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

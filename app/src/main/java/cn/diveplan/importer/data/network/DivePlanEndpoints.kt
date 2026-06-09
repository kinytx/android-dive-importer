package cn.diveplan.importer.data.network

/**
 * ECS 后端 endpoint —— 全部相对 [BASE_URL]。
 *
 * 跟 gas-dive-server `BleProbeBindController` / `BleProbeCapturesController` 对齐。
 * 之后 P3/P4 加 `/api/me/dives/parse` 等。
 */
object DivePlanEndpoints {
    const val BASE_URL = "https://api.diveplan.cn"

    /** Android 端：6 位码换 ApiKey（匿名 POST，不带 Authorization） */
    const val CONSUME_BIND_CODE = "/api/ble-probe/bind-codes/consume"

    /** Android 端：上传嗅探样本（要带 X-Api-Key） */
    const val PROBE_CAPTURES = "/api/me/ble-probe-captures"

    /** P3+：dump 文件解析 */
    const val DIVES_PARSE = "/api/me/dives/parse"
}

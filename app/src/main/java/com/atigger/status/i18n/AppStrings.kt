package com.atigger.status.i18n

import com.atigger.status.data.AppLanguage

class AppStrings private constructor(
    private val language: AppLanguage
) {
    val appTitle = text("\u54ea\u5412\u72b6\u6001\u76d1\u63a7", "Nezha Monitor Android")
    fun updated(value: String) = text("\u6700\u8fd1\u66f4\u65b0 $value", "Updated $value")
    val settings = text("\u8bbe\u7f6e", "Settings")
    val online = text("\u4ec5\u5728\u7ebf", "Online")
    val allGroups = text("\u5168\u90e8", "All")
    val groups = text("\u5206\u7ec4", "Groups")
    val ungrouped = text("\u672a\u5206\u7ec4", "Ungrouped")
    val refresh = text("\u5237\u65b0", "Refresh")
    val confirm = text("\u786e\u5b9a", "Confirm")
    val monitorSettings = text("\u76d1\u63a7\u8bbe\u7f6e", "Monitor Settings")
    val cancel = text("\u8fd4\u56de", "Back")
    val connection = text("\u8fde\u63a5\u4fe1\u606f", "Connection")
    val connectionHint = text(
        "\u8bf7\u8f93\u5165\u76d1\u63a7\u7ad9\u70b9\u5730\u5740",
        "Enter the monitor site URL."
    )
    val monitorUrl = text("\u76d1\u63a7\u5730\u5740", "Monitor URL")
    val requireLogin = text("\u9700\u8981\u767b\u5f55", "Require Login")
    val username = text("\u7528\u6237\u540d", "Username")
    val password = text("\u5bc6\u7801", "Password")
    val languageLabel = text("\u5e94\u7528\u8bed\u8a00", "App Language")
    val languageHint = text("\u9ed8\u8ba4\u4e2d\u6587\uff0c\u53ef\u5207\u6362\u4e3a\u82f1\u6587\u3002", "Chinese by default, switchable to English.")
    val liveUpdateDisplayLabel = text("\u5b9e\u51b5\u901a\u77e5\u663e\u793a", "Live Update Metric")
    val liveUpdateDisplayHint = text(
        "\u9009\u62e9\u5b9e\u51b5\u901a\u77e5\u91cc\u4f18\u5148\u663e\u793a\u7684\u5173\u952e\u6307\u6807\u3002",
        "Choose which key metric should be highlighted in the Live Update."
    )
    val chinese = "\u4e2d\u6587"
    val english = "English"
    val metricCpu = "CPU"
    val metricMemory = text("\u5185\u5b58", "Memory")
    val metricNetwork = text("\u4e0a\u4e0b\u884c\u901f\u7387", "Up/Down Speed")
    val saveConfig = text("\u4fdd\u5b58\u914d\u7f6e", "Save Config")
    val loadingServerData = text("\u6b63\u5728\u52a0\u8f7d\u670d\u52a1\u5668\u6570\u636e...", "Loading server data...")
    val loadingStepFetchingToken = text("\u83b7\u53d6 Token", "Fetch token")
    val loadingStepFetchingGroups = text("\u83b7\u53d6\u5206\u7ec4", "Fetch groups")
    val loadingStepConnectingWs = text("\u5efa\u7acb WebSocket", "Connect WebSocket")
    val loadingStepWaitingFirstFrame = text("\u7b49\u5f85\u9996\u5e27\u6570\u636e", "Wait for first frame")
    val loadingStatusDone = text("\u5df2\u5b8c\u6210", "Done")
    val loadingStatusActive = text("\u8fdb\u884c\u4e2d", "In progress")
    val loadingStatusPending = text("\u7b49\u5f85\u4e2d", "Pending")
    val loadingStatusFallback = text("\u5df2\u56de\u9000", "Fallback")
    val loadingStageFetchingTokenTitle = text("\u6b63\u5728\u83b7\u53d6\u767b\u5f55 Token...", "Fetching login token...")
    val loadingStageFetchingTokenDetail = text(
        "\u6b63\u5728\u8bf7\u6c42 /api/v1/login \u5e76\u83b7\u53d6 nz-jwt\u3002",
        "Requesting /api/v1/login and fetching the nz-jwt token."
    )
    val loadingStageFetchingGroupsTitle = text("\u6b63\u5728\u83b7\u53d6\u5206\u7ec4...", "Fetching groups...")
    val loadingStageFetchingGroupsDetail = text(
        "\u5148\u62c9\u53d6\u5206\u7ec4\u548c\u8282\u70b9\u5f52\u7c7b\u4fe1\u606f\u3002",
        "Loading groups and node mapping first."
    )
    val loadingStageConnectingWsTitle = text("\u6b63\u5728\u5efa\u7acb\u5b9e\u65f6\u8fde\u63a5...", "Connecting live updates...")
    val loadingStageConnectingWsDetail = text(
        "\u6b63\u5728\u8fde\u63a5 WebSocket \u5b9e\u65f6\u901a\u9053\u3002",
        "Opening the WebSocket live channel."
    )
    val loadingStageWaitingFirstFrameTitle = text("\u5b9e\u65f6\u8fde\u63a5\u5df2\u5efa\u7acb\uff0c\u7b49\u5f85\u9996\u5e27\u6570\u636e...", "Connected, waiting for first frame...")
    val loadingStageWaitingFirstFrameDetail = text(
        "\u5df2\u7ecf\u8fde\u4e0a WebSocket\uff0c\u6536\u5230\u9996\u6b21\u63a8\u9001\u540e\u5c31\u4f1a\u663e\u793a\u5217\u8868\u3002",
        "The WebSocket is open. The list will appear after the first push frame arrives."
    )
    fun loadingRetry(current: Int, total: Int) = text(
        "\u91cd\u8bd5 $current / $total",
        "Retry $current / $total"
    )
    val loadingGroupFallbackNotice = text(
        "\u5206\u7ec4\u83b7\u53d6\u5931\u8d25\uff0c\u5df2\u56de\u9000\u4e3a\u65e0\u5206\u7ec4\u6a21\u5f0f\uff0c\u7ee7\u7eed\u5c1d\u8bd5 WebSocket \u8fde\u63a5\u3002",
        "Fetching groups failed. Falling back to an ungrouped list while WebSocket connection continues."
    )
    val noMonitorConfig = text("\u8fd8\u6ca1\u6709\u76d1\u63a7\u914d\u7f6e", "No Monitor Config")
    val setupHint = text(
        "\u5148\u586b\u5199\u4f60\u7684\u76d1\u63a7\u5730\u5740\u548c\u53ef\u9009\u767b\u5f55\u4fe1\u606f\uff0c\u4fdd\u5b58\u540e\u624d\u80fd\u5f00\u59cb\u8ba2\u9605\u72b6\u6001\u3002",
        "Set your monitor URL and optional login info before the app starts streaming."
    )
    val openSettings = text("\u53bb\u914d\u7f6e", "Open Settings")
    val pressBackAgainToExit = text(
        "\u518d\u6309\u4e00\u6b21\u8fd4\u56de\u9000\u51fa\u7a0b\u5e8f",
        "Press back again to exit"
    )
    val notificationPermissionTitle = text("\u9700\u8981\u901a\u77e5\u6743\u9650", "Notification Access Required")
    val notificationPermissionMessage = text(
        "\u9700\u8981\u901a\u77e5\u6743\u9650\u624d\u80fd\u6b63\u5e38\u63d0\u9192\u4f60\u670d\u52a1\u5668\u72b6\u6001\u53d8\u5316\u3002\u70b9\u51fb\u201c\u786e\u5b9a\u201d\u540e\u5c06\u6253\u5f00\u5f53\u524d\u5e94\u7528\u7684\u7cfb\u7edf\u8bbe\u7f6e\u9875\uff0c\u8bf7\u5728\u90a3\u91cc\u624b\u52a8\u5f00\u542f\u901a\u77e5\u3002",
        "Notification access is required for server status alerts. Tap Confirm to open this app's system settings and enable notifications manually."
    )
    val notificationSettingsMessage = text(
        "\u5f53\u524d\u5e94\u7528\u901a\u77e5\u5df2\u88ab\u7cfb\u7edf\u5173\u95ed\u3002\u70b9\u51fb\u201c\u786e\u5b9a\u201d\u540e\u5c06\u6253\u5f00\u5f53\u524d\u5e94\u7528\u7684\u7cfb\u7edf\u8bbe\u7f6e\u9875\uff0c\u8bf7\u5728\u90a3\u91cc\u624b\u52a8\u5f00\u542f\u901a\u77e5\u3002",
        "App notifications are currently disabled. Tap Confirm to open this app's system settings and enable notifications manually."
    )
    val loadFailed = text("\u52a0\u8f7d\u5931\u8d25", "Load Failed")
    val retry = text("\u91cd\u8bd5", "Retry")
    fun nodes(count: Int) = text("\u5171 $count \u4e2a\u8282\u70b9", "$count nodes")
    val noNodes = text("\u5f53\u524d\u7b5b\u9009\u6761\u4ef6\u4e0b\u6ca1\u6709\u8282\u70b9\u6570\u636e", "No nodes matched the current filter.")
    val reconnect = text("\u91cd\u65b0\u8fde\u63a5", "Reconnect")
    val followedNode = text("\u5173\u6ce8\u8282\u70b9", "Followed Node")
    val unfollow = text("\u53d6\u6d88\u5173\u6ce8", "Unfollow")
    val follow = text("\u5173\u6ce8", "Follow")
    val followed = text("\u5df2\u5173\u6ce8", "Followed")
    val followHint = text(
        "\u70b9\u51fb\u4efb\u610f\u8282\u70b9\u7684\u201c\u5173\u6ce8\u201d\uff0c\u9664\u4e86\u56fa\u5b9a\u5728\u8fd9\u91cc\uff0c\u8fd8\u4f1a\u540c\u6b65\u5230\u7cfb\u7edf\u5b9e\u51b5\u901a\u77e5\u3002",
        "Follow a node to pin it here and mirror it into the system Live Update notification."
    )
    val followMissingHint = text(
        "\u5df2\u5173\u6ce8\u7684\u8282\u70b9\u6682\u65f6\u4e0d\u5728\u6700\u65b0\u63a8\u9001\u91cc\uff0c\u7b49\u4e0b\u4e00\u5e27\u66f4\u65b0\u6216\u53d6\u6d88\u5173\u6ce8\u5373\u53ef\u3002",
        "The followed node is not in the latest frame yet. Wait for the next update or unfollow it."
    )
    val cpu = "CPU"
    val tcp = "TCP"
    val udp = "UDP"
    val ipv4 = "IPv4"
    val ipv6 = "IPv6"
    val usage = text("\u8d44\u6e90", "Usage")
    val network = text("\u7f51\u7edc", "Network")
    val traffic = text("\u6d41\u91cf", "Traffic")
    val uptime = text("\u5728\u7ebf", "Uptime")
    val ip = "IP"
    val connectionLabel = text("\u8fde\u63a5", "Connection")
    val memory = text("\u5185\u5b58", "Memory")
    val disk = text("\u78c1\u76d8", "Disk")
    val swap = text("\u4ea4\u6362", "Swap")
    val down = text("\u4e0b\u884c", "Down")
    val up = text("\u4e0a\u884c", "Up")
    val load = text("\u8d1f\u8f7d", "Load")
    val totalIn = text("\u603b\u5165", "In")
    val totalOut = text("\u603b\u51fa", "Out")
    val process = text("\u8fdb\u7a0b", "Proc")
    val plan = text("\u5957\u9910", "Plan")
    val billing = text("\u8d26\u5355", "Billing")
    val bandwidth = text("\u5e26\u5bbd", "Bandwidth")
    val trafficVolume = text("\u6d41\u91cf", "Traffic")
    val cycle = text("\u5468\u671f", "Cycle")
    val due = text("\u5230\u671f", "Due")
    val autoRenew = text("\u81ea\u52a8\u7eed\u8d39", "Auto renew")
    val noAutoRenew = text("\u4e0d\u81ea\u52a8\u7eed\u8d39", "No auto renew")
    val onlineStatus = text("\u5728\u7ebf", "Online")
    val offlineStatus = text("\u79bb\u7ebf", "Offline")
    fun cpuCriticalText(value: String) = "CPU $value"
    fun memoryCriticalText(value: String) = text("\u5185\u5b58 $value", "Mem $value")
    fun networkCriticalText(downValue: String?, upValue: String?): String? {
        val downText = downValue?.let { "D$it" }
        val upText = upValue?.let { "U$it" }
        return listOfNotNull(downText, upText).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
    val systemInfoUnavailable = text("\u6682\u65e0\u7cfb\u7edf\u4fe1\u606f", "No system info")
    val cpuInfoUnavailable = text("\u6682\u65e0 CPU \u4fe1\u606f", "No CPU info")
    val usageInfoUnavailable = text("\u6682\u65e0\u8d44\u6e90\u5360\u7528\u4fe1\u606f", "No usage info")
    val networkInfoUnavailable = text("\u6682\u65e0\u7f51\u7edc\u4fe1\u606f", "No network info")
    val trafficInfoUnavailable = text("\u6682\u65e0\u6d41\u91cf\u7edf\u8ba1", "No traffic info")
    val connectionInfoUnavailable = text("\u6682\u65e0\u8fde\u63a5\u4fe1\u606f", "No connection info")
    fun uptimePrefix(value: String) = text("\u5728\u7ebf\u65f6\u957f $value", "Uptime $value")
    fun duration(days: Long, hours: Long, minutes: Long): String {
        return if (language == AppLanguage.ZH) {
            buildString {
                if (days > 0) append("${days}\u5929")
                if (hours > 0) append("${hours}\u5c0f\u65f6")
                if (minutes > 0 || isEmpty()) append("${minutes}\u5206\u949f")
            }
        } else {
            buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || isEmpty()) append("${minutes}m")
            }.trim()
        }
    }
    val enterMonitorUrl = text("\u8bf7\u8f93\u5165\u76d1\u63a7\u5730\u5740\u3002", "Please enter the monitor URL.")
    val invalidMonitorUrl = text(
        "\u76d1\u63a7\u5730\u5740\u5fc5\u987b\u662f http:// \u6216 https:// \u5f00\u5934\u7684\u5b8c\u6574\u5730\u5740\u3002",
        "The monitor URL must be a full http:// or https:// URL."
    )
    val loginRequiresCredentials = text(
        "\u542f\u7528\u767b\u5f55\u65f6\u5fc5\u987b\u586b\u5199\u7528\u6237\u540d\u548c\u5bc6\u7801\u3002",
        "Username and password are required when login is enabled."
    )
    val unknownError = text("\u672a\u77e5\u9519\u8bef", "Unknown error")
    val loginFailedCheckConfig = text(
        "\u767b\u5f55\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7528\u6237\u540d\u3001\u5bc6\u7801\u548c\u76d1\u63a7\u5730\u5740\u914d\u7f6e\u3002",
        "Login failed. Check the username, password, and monitor URL."
    )
    val failedToParseLoginResponse = text("\u65e0\u6cd5\u89e3\u6790\u767b\u5f55\u54cd\u5e94\u3002", "Failed to parse login response.")
    val failedToParseServerGroupResponse = text(
        "\u65e0\u6cd5\u89e3\u6790\u5206\u7ec4\u54cd\u5e94\u3002",
        "Failed to parse group response."
    )
    fun serverGroupRequestFailedHttp(code: Int) = text(
        "\u5206\u7ec4\u8bf7\u6c42\u5931\u8d25\uff0cHTTP $code",
        "Group request failed, HTTP $code"
    )
    val serverGroupRequestFailed = text(
        "\u5206\u7ec4\u8bf7\u6c42\u5931\u8d25\u3002",
        "Group request failed."
    )
    val failedToParseWebsocketPayload = text("\u65e0\u6cd5\u89e3\u6790 WebSocket \u8fd4\u56de\u6570\u636e\u3002", "Failed to parse websocket payload.")
    fun webSocketRequestFailedHttp(code: Int) = text("WebSocket \u8bf7\u6c42\u5931\u8d25\uff0cHTTP $code", "WebSocket request failed, HTTP $code")
    val webSocketRequestFailed = text("WebSocket \u8bf7\u6c42\u5931\u8d25\u3002", "WebSocket request failed.")
    val openSettingsAfterConnectionFailure = text(
        "\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u76d1\u63a7\u5730\u5740\u3001\u534f\u8bae\u548c\u767b\u5f55\u914d\u7f6e\u3002",
        "Connection failed. Check the monitor URL, protocol, and login settings."
    )
    fun webSocketClosedUnexpectedly(code: Int, reason: String) = text(
        "WebSocket \u610f\u5916\u5173\u95ed\uff08code $code\uff0creason: $reason\uff09\u3002",
        "WebSocket closed unexpectedly (code $code, reason: $reason)."
    )
    val liveUpdate = text("\u6b63\u5728\u8fde\u63a5", "Connecting")
    val liveUpdateChannelName = text("\u72b6\u6001\u5b9e\u51b5\u901a\u77e5", "Status Live Update")
    val liveUpdateConnectionFailed = text("\u5b9e\u51b5\u901a\u77e5\u8fde\u63a5\u5931\u8d25", "Live Update connection failed")
    fun connectingFollowedNode(id: Int) = text("\u6b63\u5728\u8fde\u63a5\u5173\u6ce8\u8282\u70b9 #$id", "Connecting to followed node #$id")
    val connectingDetail = text("\u5efa\u7acb\u8fde\u63a5\u540e\u4f1a\u6301\u7eed\u663e\u793a\u6700\u65b0\u63a8\u9001\u72b6\u6001\u3002", "The notification will keep updating as new websocket frames arrive.")
    fun waitingForFollowedNode(id: Int) = text("\u6b63\u5728\u7b49\u5f85\u5173\u6ce8\u8282\u70b9 #$id \u7684\u6700\u65b0\u72b6\u6001", "Waiting for followed node #$id")
    fun latestFrame(value: String) = text("\u6700\u8fd1\u4e00\u5e27\u63a8\u9001\u65f6\u95f4 $value", "Latest websocket frame at $value.")
    fun updatedAt(value: String) = text("\u6700\u8fd1\u66f4\u65b0 $value", "Updated at $value")
    val channelDescription = text("\u5b9e\u65f6\u663e\u793a\u5173\u6ce8\u8282\u70b9\u7684\u6700\u65b0\u72b6\u6001\u3002", "Shows the latest status of the followed node.")
    fun agentVersion(value: String) = text("\u63a2\u9488 $value", "Agent $value")

    private fun text(zh: String, en: String): String = if (language == AppLanguage.ZH) zh else en

    companion object {
        fun of(language: AppLanguage): AppStrings = AppStrings(language)
    }
}

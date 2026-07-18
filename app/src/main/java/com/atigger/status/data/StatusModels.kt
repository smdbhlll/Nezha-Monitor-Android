package com.atigger.status.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.atigger.status.i18n.AppStrings
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

data class StatusResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: List<ServerDto> = emptyList()
)

data class StatusWsResponse(
    val now: Long? = null,
    val online: Int? = null,
    val servers: List<ServerDto> = emptyList()
)

data class StatusSnapshot(
    val lastUpdated: String,
    val servers: List<ServerUiModel>
)

sealed interface StatusStreamEvent {
    data object Connecting : StatusStreamEvent
    data object WaitingForFirstSnapshot : StatusStreamEvent
    data class SnapshotReceived(val snapshot: StatusSnapshot) : StatusStreamEvent
}

data class ServerGroupResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: List<ServerGroupItemDto> = emptyList()
)

data class ServerGroupItemDto(
    val group: ServerGroupDto = ServerGroupDto(),
    val servers: List<Int> = emptyList()
)

data class ServerGroupDto(
    val id: Int = 0,
    val name: String = ""
)

data class ServerDto(
    val id: Int = 0,
    val name: String = "",
    val uuid: String = "",
    @SerializedName("public_note")
    val publicNote: String? = null,
    @SerializedName("display_index")
    val displayIndex: Int = 0,
    val host: HostDto = HostDto(),
    val state: StateDto = StateDto(),
    val geoip: GeoIpDto = GeoIpDto(),
    @SerializedName("country_code")
    val countryCode: String? = null,
    @SerializedName("last_active")
    val lastActive: String = ""
)

data class HostDto(
    val platform: String? = null,
    @SerializedName("platform_version")
    val platformVersion: String? = null,
    val cpu: List<String> = emptyList(),
    @SerializedName("mem_total")
    val memTotal: Long? = null,
    @SerializedName("disk_total")
    val diskTotal: Long? = null,
    @SerializedName("swap_total")
    val swapTotal: Long? = null,
    val arch: String? = null,
    @SerializedName("boot_time")
    val bootTime: Long? = null,
    val version: String? = null,
    val virtualization: String? = null
)

data class StateDto(
    val cpu: Double? = null,
    @SerializedName("mem_used")
    val memUsed: Long? = null,
    @SerializedName("swap_used")
    val swapUsed: Long? = null,
    @SerializedName("disk_used")
    val diskUsed: Long? = null,
    @SerializedName("net_in_transfer")
    val netInTransfer: Long? = null,
    @SerializedName("net_out_transfer")
    val netOutTransfer: Long? = null,
    @SerializedName("net_in_speed")
    val netInSpeed: Long? = null,
    @SerializedName("net_out_speed")
    val netOutSpeed: Long? = null,
    val uptime: Long? = null,
    @SerializedName("load_1")
    val load1: Double? = null,
    @SerializedName("load_5")
    val load5: Double? = null,
    @SerializedName("load_15")
    val load15: Double? = null,
    @SerializedName("tcp_conn_count")
    val tcpConnCount: Int? = null,
    @SerializedName("udp_conn_count")
    val udpConnCount: Int? = null,
    @SerializedName("process_count")
    val processCount: Int? = null
)

data class GeoIpDto(
    val ip: IpDto = IpDto(),
    @SerializedName("country_code")
    val countryCode: String? = null
)

data class IpDto(
    @SerializedName("ipv4_addr")
    val ipv4Addr: String? = null,
    @SerializedName("ipv6_addr")
    val ipv6Addr: String? = null
)

data class PublicNoteDto(
    val billingDataMod: BillingDto? = null,
    val planDataMod: PlanDto? = null,
    val customData: CustomDto? = null
)

data class BillingDto(
    val startDate: String? = null,
    val endDate: String? = null,
    val autoRenewal: String? = null,
    val cycle: String? = null,
    val amount: String? = null
)

data class PlanDto(
    val bandwidth: String? = null,
    val trafficVol: String? = null,
    val trafficType: String? = null,
    val IPv4: String? = null,
    val IPv6: String? = null
)

data class CustomDto(
    val location: String? = null,
    val flag: String? = null
)

data class ServerUiModel(
    val id: Int,
    val name: String,
    val isOnline: Boolean,
    val groupIds: Set<Int>,
    val cpuCriticalText: String?,
    val memoryCriticalText: String?,
    val networkCriticalText: String?,
    val platformLine: String,
    val cpuLine: String,
    val usageLine: String,
    val networkLine: String,
    val trafficLine: String,
    val connectionLine: String,
    val planLine: String?,
    val billingLine: String?,
    val ipLine: String?,
    val uptimeLine: String?,
    val locationTag: String?,
    val versionTag: String?,
    // Raw values for graphical display
    val cpuPercent: Float?,           // 0-100
    val memoryPercent: Float?,        // 0-100
    val memoryUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val diskPercent: Float?,          // 0-100
    val diskUsedBytes: Long?,
    val diskTotalBytes: Long?,
    val swapPercent: Float?,          // 0-100
    val netInSpeed: Long?,            // bytes/s
    val netOutSpeed: Long?,           // bytes/s
    val netInTransfer: Long?,         // total bytes
    val netOutTransfer: Long?,        // total bytes
    val tcpConnCount: Int?,
    val udpConnCount: Int?,
    val processCount: Int?
)

data class ServerGroupUiModel(
    val id: Int,
    val name: String,
    val serverIds: Set<Int>
)

fun ServerDto.toUiModel(
    gson: Gson,
    strings: AppStrings,
    now: Instant,
    groupIds: Set<Int> = emptySet()
): ServerUiModel {
    val note = parsePublicNote(gson)
    val online = isServerOnline(now)
    val platformLine = listOfNotNull(host.platform, host.platformVersion, host.arch, host.virtualization)
        .joinToString(" / ")
        .ifBlank { strings.systemInfoUnavailable }
    val cpuLine = host.cpu.firstOrNull().orEmpty().ifBlank { strings.cpuInfoUnavailable }

    val usageLine = buildList {
        addIfNotBlank(strings.cpu, state.cpu?.let { "${formatPercent(it)}%" })
        addIfNotBlank(strings.memory, formatUsage(state.memUsed, host.memTotal))
        addIfNotBlank(strings.disk, formatUsage(state.diskUsed, host.diskTotal))
        addIfNotBlank(strings.swap, formatUsage(state.swapUsed, host.swapTotal))
    }.joinToString("  ")

    val networkLine = buildList {
        addIfNotBlank(strings.down, state.netInSpeed?.let { "${formatBytes(it)}/s" })
        addIfNotBlank(strings.up, state.netOutSpeed?.let { "${formatBytes(it)}/s" })
        addIfNotBlank(
            strings.load,
            listOfNotNull(
                state.load1?.let(::formatLoad),
                state.load5?.let(::formatLoad),
                state.load15?.let(::formatLoad)
            ).takeIf { it.isNotEmpty() }?.joinToString("/")
        )
    }.joinToString("  ")

    val trafficLine = buildList {
        addIfNotBlank(strings.totalIn, state.netInTransfer?.let(::formatBytes))
        addIfNotBlank(strings.totalOut, state.netOutTransfer?.let(::formatBytes))
    }.joinToString("  ")

    val connectionLine = buildList {
        addIfNotBlank(strings.tcp, state.tcpConnCount?.toString())
        addIfNotBlank(strings.udp, state.udpConnCount?.toString())
        addIfNotBlank(strings.process, state.processCount?.toString())
    }.joinToString("  ")

    val planLine = listOfNotNull(
        note.planDataMod?.bandwidth?.let { "${strings.bandwidth} $it" },
        note.planDataMod?.trafficVol?.let { "${strings.trafficVolume} $it" },
        note.planDataMod?.IPv4?.let { "${strings.ipv4} $it" },
        note.planDataMod?.IPv6?.let { "${strings.ipv6} $it" }
    ).takeIf { it.isNotEmpty() }?.joinToString("  ")

    val billingLine = listOfNotNull(
        note.billingDataMod?.amount,
        note.billingDataMod?.cycle?.let { "${strings.cycle} $it" },
        note.billingDataMod?.endDate?.let { "${strings.due} ${formatDate(it)}" },
        note.billingDataMod?.autoRenewal?.let {
            if (it == "1") strings.autoRenew else strings.noAutoRenew
        }
    ).takeIf { it.isNotEmpty() }?.joinToString("  ")

    val ips = listOfNotNull(geoip.ip.ipv4Addr, geoip.ip.ipv6Addr)
    val ipLine = ips.takeIf { it.isNotEmpty() }?.joinToString("  ")

    // Compute raw percentage values
    val memoryPercent = computePercent(state.memUsed, host.memTotal)
    val diskPercent = computePercent(state.diskUsed, host.diskTotal)
    val swapPercent = computePercent(state.swapUsed, host.swapTotal)

    return ServerUiModel(
        id = id,
        name = name,
        isOnline = online,
        groupIds = groupIds,
        cpuCriticalText = state.cpu?.let { strings.cpuCriticalText("${formatPercent(it)}%") },
        memoryCriticalText = formatUsagePercent(state.memUsed, host.memTotal)?.let(strings::memoryCriticalText),
        networkCriticalText = strings.networkCriticalText(
            state.netInSpeed?.let { "${formatBytes(it)}/s" },
            state.netOutSpeed?.let { "${formatBytes(it)}/s" }
        ),
        platformLine = platformLine,
        cpuLine = cpuLine,
        usageLine = usageLine.ifBlank { strings.usageInfoUnavailable },
        networkLine = networkLine.ifBlank { strings.networkInfoUnavailable },
        trafficLine = trafficLine.ifBlank { strings.trafficInfoUnavailable },
        connectionLine = connectionLine.ifBlank { strings.connectionInfoUnavailable },
        planLine = planLine,
        billingLine = billingLine,
        ipLine = ipLine,
        uptimeLine = state.uptime?.let { strings.uptimePrefix(formatDuration(it, strings)) },
        locationTag = note.customData?.location
            ?: countryCode?.uppercase(Locale.getDefault())
            ?: geoip.countryCode?.uppercase(Locale.getDefault()),
        versionTag = host.version,
        // Raw values for graphical display
        cpuPercent = state.cpu?.toFloat(),
        memoryPercent = memoryPercent,
        memoryUsedBytes = state.memUsed,
        memoryTotalBytes = host.memTotal,
        diskPercent = diskPercent,
        diskUsedBytes = state.diskUsed,
        diskTotalBytes = host.diskTotal,
        swapPercent = swapPercent,
        netInSpeed = state.netInSpeed,
        netOutSpeed = state.netOutSpeed,
        netInTransfer = state.netInTransfer,
        netOutTransfer = state.netOutTransfer,
        tcpConnCount = state.tcpConnCount,
        udpConnCount = state.udpConnCount,
        processCount = state.processCount
    )
}

fun ServerUiModel.criticalStatusText(metric: LiveUpdateMetric, strings: AppStrings): String {
    return when (metric) {
        LiveUpdateMetric.CPU -> cpuCriticalText ?: memoryCriticalText ?: networkCriticalText
        LiveUpdateMetric.MEMORY -> memoryCriticalText ?: cpuCriticalText ?: networkCriticalText
        LiveUpdateMetric.NETWORK -> networkCriticalText ?: cpuCriticalText ?: memoryCriticalText
    } ?: if (isOnline) strings.onlineStatus else strings.offlineStatus
}

fun List<ServerGroupItemDto>.toUiModels(): List<ServerGroupUiModel> = mapNotNull { item ->
    val groupId = item.group.id
    val groupName = item.group.name.trim()
    if (groupId <= 0 || groupName.isBlank()) {
        null
    } else {
        ServerGroupUiModel(
            id = groupId,
            name = groupName,
            serverIds = item.servers.toSet()
        )
    }
}

private fun ServerDto.parsePublicNote(gson: Gson): PublicNoteDto {
    val raw = publicNote?.trim().orEmpty()
    if (raw.isBlank()) return PublicNoteDto()
    return runCatching { gson.fromJson(raw, PublicNoteDto::class.java) }.getOrElse { PublicNoteDto() }
}

private fun ServerDto.isServerOnline(now: Instant): Boolean {
    val activeAt = parseLastActiveInstant() ?: return false
    val driftSeconds = Duration.between(activeAt, now).seconds.absoluteValue
    return driftSeconds <= ONLINE_THRESHOLD_SECONDS
}

private fun ServerDto.parseLastActiveInstant(): Instant? {
    if (lastActive.isBlank() || lastActive.startsWith("0001-01-01")) return null
    return runCatching { OffsetDateTime.parse(lastActive).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(lastActive, DateTimeFormatter.ISO_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
        .getOrNull()
}

private fun MutableList<String>.addIfNotBlank(label: String, value: String?) {
    if (!value.isNullOrBlank()) add("$label $value")
}

private fun formatUsage(used: Long?, total: Long?): String? {
    if (used == null || total == null || total <= 0) return null
    val percent = used.toDouble() / total.toDouble() * 100.0
    return "${formatBytes(used)} / ${formatBytes(total)} (${formatPercent(percent)}%)"
}

private fun formatUsagePercent(used: Long?, total: Long?): String? {
    if (used == null || total == null || total <= 0) return null
    val percent = used.toDouble() / total.toDouble() * 100.0
    return "${formatPercent(percent)}%"
}

private fun computePercent(used: Long?, total: Long?): Float? {
    if (used == null || total == null || total <= 0) return null
    return (used.toDouble() / total.toDouble() * 100.0).toFloat()
}

internal fun formatBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var display = value.toDouble().absoluteValue
    var unitIndex = 0
    while (display >= 1024 && unitIndex < units.lastIndex) {
        display /= 1024.0
        unitIndex += 1
    }
    val sign = if (value < 0) "-" else ""
    val pattern = if (display >= 100 || unitIndex == 0) "%.0f" else "%.1f"
    return sign + pattern.format(Locale.US, display) + units[unitIndex]
}

private fun formatPercent(value: Double): String = "%.1f".format(Locale.US, value)

private fun formatLoad(value: Double): String = "%.2f".format(Locale.US, value)

private fun formatDuration(seconds: Long, strings: AppStrings): String {
    val duration = Duration.ofSeconds(seconds)
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return strings.duration(days, hours, minutes)
}

private fun formatDate(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
    }.getOrDefault(value)
}

private const val ONLINE_THRESHOLD_SECONDS = 30L

package ru.tipowk.tunvpn.model

import com.benasher44.uuid.uuid4
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val id: String = uuid4().toString(),
    val name: String = "",
    val address: String = "",
    val port: Int = 443,
    val uuid: String = "",
    val flow: Flow = Flow.NONE,
    val encryption: String = "none",
    val network: TransportType = TransportType.TCP,
    val security: SecurityType = SecurityType.NONE,
    val sni: String = "",
    val allowInsecure: Boolean = false,
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val wsPath: String = "",
    val wsHost: String = "",
    val grpcServiceName: String = "",
)

@Serializable
enum class TransportType(val label: String) {
    TCP("TCP"),
    WS("WebSocket"),
    GRPC("gRPC"),
}

@Serializable
enum class SecurityType(val label: String) {
    NONE("None"),
    TLS("TLS"),
    REALITY("Reality"),
}

@Serializable
enum class Flow(val value: String, val label: String) {
    NONE("", "None"),
    XTLS_RPRX_VISION("xtls-rprx-vision", "xtls-rprx-vision"),
}

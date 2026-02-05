package ru.tipowk.tunvpn.data

import ru.tipowk.tunvpn.model.Flow
import ru.tipowk.tunvpn.model.SecurityType
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TransportType

object VlessUriParser {

    /**
     * Parses a vless:// URI into a ServerConfig.
     *
     * Format: vless://UUID@address:port?type=tcp&security=reality&flow=xtls-rprx-vision&sni=example.com&pbk=KEY&sid=ID&fp=chrome#Remark
     */
    fun parse(uri: String): ServerConfig? {
        if (!uri.startsWith("vless://")) return null

        val withoutScheme = uri.removePrefix("vless://")

        // Split remark (fragment) from the rest
        val fragmentIndex = withoutScheme.lastIndexOf('#')
        val remark = if (fragmentIndex >= 0) {
            decodePercent(withoutScheme.substring(fragmentIndex + 1))
        } else ""
        val mainPart = if (fragmentIndex >= 0) {
            withoutScheme.substring(0, fragmentIndex)
        } else withoutScheme

        // Split userinfo from host+query
        val atIndex = mainPart.indexOf('@')
        if (atIndex < 0) return null
        val uuid = mainPart.substring(0, atIndex)
        val hostAndQuery = mainPart.substring(atIndex + 1)

        // Split host:port from query
        val queryIndex = hostAndQuery.indexOf('?')
        val hostPort = if (queryIndex >= 0) hostAndQuery.substring(0, queryIndex) else hostAndQuery
        val queryString = if (queryIndex >= 0) hostAndQuery.substring(queryIndex + 1) else ""

        // Parse address and port (handle IPv6 brackets)
        val address: String
        val port: Int
        if (hostPort.startsWith("[")) {
            // IPv6: [::1]:443
            val closeBracket = hostPort.indexOf(']')
            if (closeBracket < 0) return null
            address = hostPort.substring(1, closeBracket)
            val portStr = hostPort.substring(closeBracket + 1).removePrefix(":")
            port = portStr.toIntOrNull() ?: 443
        } else {
            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon < 0) return null
            address = hostPort.substring(0, lastColon)
            port = hostPort.substring(lastColon + 1).toIntOrNull() ?: 443
        }

        // Parse query parameters
        val params = parseQueryParams(queryString)

        val network = when (params["type"]?.lowercase()) {
            "ws", "websocket" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            else -> TransportType.TCP
        }

        val security = when (params["security"]?.lowercase()) {
            "tls" -> SecurityType.TLS
            "reality" -> SecurityType.REALITY
            else -> SecurityType.NONE
        }

        val flow = when (params["flow"]?.lowercase()) {
            "xtls-rprx-vision" -> Flow.XTLS_RPRX_VISION
            else -> Flow.NONE
        }

        return ServerConfig(
            name = remark.ifEmpty { "$address:$port" },
            address = address,
            port = port,
            uuid = uuid,
            flow = flow,
            network = network,
            security = security,
            sni = params["sni"] ?: "",
            allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true",
            fingerprint = params["fp"] ?: "chrome",
            publicKey = params["pbk"] ?: "",
            shortId = params["sid"] ?: "",
            spiderX = params["spx"] ?: "",
            wsPath = params["path"]?.let { decodePercent(it) } ?: "",
            wsHost = params["host"] ?: "",
            grpcServiceName = params["serviceName"] ?: "",
        )
    }

    /**
     * Converts a ServerConfig back to a vless:// URI.
     */
    fun toUri(config: ServerConfig): String {
        val params = mutableListOf<String>()

        params.add("type=${config.network.name.lowercase()}")

        if (config.security != SecurityType.NONE) {
            params.add("security=${config.security.name.lowercase()}")
        }

        if (config.flow != Flow.NONE) {
            params.add("flow=${config.flow.value}")
        }

        if (config.sni.isNotEmpty()) params.add("sni=${config.sni}")
        if (config.fingerprint.isNotEmpty()) params.add("fp=${config.fingerprint}")

        // Security-specific params
        if (config.security == SecurityType.REALITY) {
            if (config.publicKey.isNotEmpty()) params.add("pbk=${config.publicKey}")
            if (config.shortId.isNotEmpty()) params.add("sid=${config.shortId}")
            if (config.spiderX.isNotEmpty()) params.add("spx=${encodePercent(config.spiderX)}")
        }

        if (config.security == SecurityType.TLS && config.allowInsecure) {
            params.add("allowInsecure=1")
        }

        // Transport-specific params
        when (config.network) {
            TransportType.WS -> {
                if (config.wsPath.isNotEmpty()) params.add("path=${encodePercent(config.wsPath)}")
                if (config.wsHost.isNotEmpty()) params.add("host=${config.wsHost}")
            }
            TransportType.GRPC -> {
                if (config.grpcServiceName.isNotEmpty()) params.add("serviceName=${config.grpcServiceName}")
            }
            TransportType.TCP -> {}
        }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = if (config.name.isNotEmpty()) "#${encodePercent(config.name)}" else ""

        return "vless://${config.uuid}@${config.address}:${config.port}${query}${fragment}"
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").associate { param ->
            val eqIndex = param.indexOf('=')
            if (eqIndex >= 0) {
                param.substring(0, eqIndex) to decodePercent(param.substring(eqIndex + 1))
            } else {
                param to ""
            }
        }
    }

    private fun decodePercent(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(value[i])
            i++
        }
        return sb.toString()
    }

    private fun encodePercent(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
            if (c.isLetterOrDigit() || c in "-_.~") {
                sb.append(c)
            } else {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    sb.append('%')
                    sb.append(
                        (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
                    )
                }
            }
        }
        return sb.toString()
    }
}

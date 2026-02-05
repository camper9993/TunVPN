package ru.tipowk.tunvpn.vpn

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.tipowk.tunvpn.model.Flow
import ru.tipowk.tunvpn.model.SecurityType
import ru.tipowk.tunvpn.model.ServerConfig
import ru.tipowk.tunvpn.model.TransportType

/**
 * Builds Xray-core JSON configuration from a ServerConfig.
 *
 * Architecture:
 *   App traffic → TUN → tun2socks → SOCKS5 (10808) → Xray-core → VLESS server
 *
 * Xray runs two local inbounds:
 *   - SOCKS5 on 127.0.0.1:10808 (used by tun2socks)
 *   - HTTP on 127.0.0.1:10809 (for manual proxy mode)
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    fun build(config: ServerConfig): String {
        val json = buildJsonObject {
            // Log - use debug level to see what's happening
            put("log", buildJsonObject {
                put("loglevel", "debug")
            })

            // Stats (needed for traffic monitoring)
            put("stats", buildJsonObject {})

            // Policy (enable stats collection)
            put("policy", buildJsonObject {
                put("levels", buildJsonObject {
                    put("0", buildJsonObject {
                        put("statsUserUplink", true)
                        put("statsUserDownlink", true)
                    })
                })
                put("system", buildJsonObject {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                })
            })

            // Inbounds
            put("inbounds", buildJsonArray {
                // SOCKS5 inbound — used by tun2socks
                add(buildJsonObject {
                    put("tag", "socks-in")
                    put("port", SOCKS_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "noauth")
                        put("udp", true)
                    })
                    put("sniffing", buildJsonObject {
                        put("enabled", true)
                        put("destOverride", buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("http"))
                            add(kotlinx.serialization.json.JsonPrimitive("tls"))
                            add(kotlinx.serialization.json.JsonPrimitive("quic"))
                            add(kotlinx.serialization.json.JsonPrimitive("fakedns"))
                        })
                        put("metadataOnly", false)
                        put("routeOnly", false)
                    })
                })

                // HTTP inbound — for manual proxy mode
                add(buildJsonObject {
                    put("tag", "http-in")
                    put("port", HTTP_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "http")
                    put("settings", buildJsonObject {})
                })
            })

            // Outbounds
            put("outbounds", buildJsonArray {
                // Proxy outbound — VLESS
                add(buildVlessOutbound(config))

                // Direct outbound
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                })

                // Block outbound
                add(buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                })
            })

            // DNS configuration - Xray will resolve domains through proxy
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    // Primary: Cloudflare DNS through proxy outbound
                    add(buildJsonObject {
                        put("address", "1.1.1.1")
                        put("port", 53)
                        put("domains", buildJsonArray {})  // All domains
                    })
                    // Fallback: Google DNS
                    add(kotlinx.serialization.json.JsonPrimitive("8.8.8.8"))
                })
                put("queryStrategy", "UseIP")
                put("disableCache", false)
                put("disableFallback", false)
            })

            // Routing
            put("routing", buildJsonObject {
                put("domainStrategy", "AsIs")
                put("rules", buildJsonArray {
                    // DNS queries (port 53) should go through proxy
                    add(buildJsonObject {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "53")
                    })
                    // Bypass localhost
                    add(buildJsonObject {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("ip", buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("127.0.0.0/8"))
                        })
                    })
                    // Everything else goes through proxy
                    add(buildJsonObject {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                })
            })
        }
        return json.toString()
    }

    private fun buildVlessOutbound(config: ServerConfig): JsonObject {
        return buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", buildJsonObject {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", config.address)
                        put("port", config.port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", config.uuid)
                                put("encryption", config.encryption)
                                if (config.flow != Flow.NONE) {
                                    put("flow", config.flow.value)
                                }
                            })
                        })
                    })
                })
            })
            put("streamSettings", buildStreamSettings(config))
        }
    }

    private fun buildStreamSettings(config: ServerConfig): JsonObject {
        return buildJsonObject {
            // Network (transport)
            put("network", when (config.network) {
                TransportType.TCP -> "tcp"
                TransportType.WS -> "ws"
                TransportType.GRPC -> "grpc"
            })

            // Security
            when (config.security) {
                SecurityType.TLS -> {
                    put("security", "tls")
                    put("tlsSettings", buildJsonObject {
                        if (config.sni.isNotEmpty()) {
                            put("serverName", config.sni)
                        }
                        put("allowInsecure", config.allowInsecure)
                        if (config.fingerprint.isNotEmpty()) {
                            put("fingerprint", config.fingerprint)
                        }
                    })
                }
                SecurityType.REALITY -> {
                    put("security", "reality")
                    put("realitySettings", buildJsonObject {
                        if (config.sni.isNotEmpty()) {
                            put("serverName", config.sni)
                        }
                        if (config.fingerprint.isNotEmpty()) {
                            put("fingerprint", config.fingerprint)
                        }
                        if (config.publicKey.isNotEmpty()) {
                            put("publicKey", config.publicKey)
                        }
                        if (config.shortId.isNotEmpty()) {
                            put("shortId", config.shortId)
                        }
                        if (config.spiderX.isNotEmpty()) {
                            put("spiderX", config.spiderX)
                        }
                    })
                }
                SecurityType.NONE -> {
                    put("security", "none")
                }
            }

            // Transport-specific settings
            when (config.network) {
                TransportType.WS -> {
                    put("wsSettings", buildJsonObject {
                        if (config.wsPath.isNotEmpty()) {
                            put("path", config.wsPath)
                        }
                        if (config.wsHost.isNotEmpty()) {
                            put("headers", buildJsonObject {
                                put("Host", config.wsHost)
                            })
                        }
                    })
                }
                TransportType.GRPC -> {
                    put("grpcSettings", buildJsonObject {
                        if (config.grpcServiceName.isNotEmpty()) {
                            put("serviceName", config.grpcServiceName)
                        }
                    })
                }
                TransportType.TCP -> {
                    // No additional settings needed for TCP
                }
            }
        }
    }
}

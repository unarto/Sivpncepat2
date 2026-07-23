package com.sivpn.cepat.parser

data class ProxyConfig(
    val host: String = "",
    val port: String = "8080"
)

object ProxyParser {

    /**
     * Parses full proxy string formatted as `host:port`
     */
    fun parseFullInput(input: String, defaultPort: String = "8080"): ProxyConfig {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ProxyConfig("", defaultPort)
        }

        return if (trimmed.contains(":")) {
            val parts = trimmed.split(":", limit = 2)
            val host = parts[0].trim()
            val port = parts.getOrNull(1)?.trim() ?: defaultPort
            ProxyConfig(host, if (port.isNotEmpty()) port else defaultPort)
        } else {
            ProxyConfig(trimmed, defaultPort)
        }
    }

    fun formatFullInput(config: ProxyConfig): String {
        return if (config.host.isEmpty()) "" else "${config.host}:${config.port}"
    }
}

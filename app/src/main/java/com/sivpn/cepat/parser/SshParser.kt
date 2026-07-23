package com.sivpn.cepat.parser

data class SshCredentials(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = ""
)

object SshParser {

    /**
     * Parses full SSH string formatted as `host:port@username:password` or `host:port`
     */
    fun parseFullInput(input: String, defaultPort: String = "22"): SshCredentials {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return SshCredentials()
        }

        return try {
            if (trimmed.contains("@")) {
                val parts = trimmed.split("@")
                val hostPortPart = parts[0].trim()
                val userPassPart = parts.getOrNull(1)?.trim() ?: ""

                val (host, port) = parseHostPort(hostPortPart, defaultPort)

                var username = ""
                var password = ""
                if (userPassPart.contains(":")) {
                    val userPassParts = userPassPart.split(":", limit = 2)
                    username = userPassParts[0].trim()
                    password = userPassParts.getOrNull(1)?.trim() ?: ""
                } else {
                    username = userPassPart
                }

                SshCredentials(
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
            } else {
                val (host, port) = parseHostPort(trimmed, defaultPort)
                SshCredentials(host = host, port = port)
            }
        } catch (e: Exception) {
            SshCredentials(host = trimmed)
        }
    }

    private fun parseHostPort(hostPort: String, defaultPort: String): Pair<String, String> {
        return if (hostPort.contains(":")) {
            val parts = hostPort.split(":", limit = 2)
            val h = parts[0].trim()
            val p = parts.getOrNull(1)?.trim() ?: defaultPort
            Pair(h, if (p.isNotEmpty()) p else defaultPort)
        } else {
            Pair(hostPort, defaultPort)
        }
    }

    fun formatFullInput(creds: SshCredentials): String {
        return if (creds.host.isEmpty()) "" else "${creds.host}:${creds.port}@${creds.username}:${creds.password}"
    }
}

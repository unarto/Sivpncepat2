package com.sivpn.cepat.vpn

object PayloadFormatter {
    fun formatPayload(payload: String, sshHost: String, sshPort: Int): String {
        return payload
            .replace("[host_port]", "$sshHost:$sshPort")
            .replace("[host]", sshHost)
            .replace("[port]", sshPort.toString())
            .replace("[protocol]", "HTTP/1.1")
            .replace("[cr]", "\r")
            .replace("[lf]", "\n")
            .replace("[crlf]", "\r\n")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
    }
}

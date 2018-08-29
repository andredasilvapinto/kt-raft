package me.andresp.http

import java.net.Inet4Address
import java.net.NetworkInterface

val LOCAL_IP: String = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .first { !it.isLoopbackAddress && it is Inet4Address }
        .hostAddress

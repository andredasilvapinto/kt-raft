package me.andresp.config

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.intType
import com.natpryce.konfig.stringType

object config : PropertyGroup() {
    val httpPort by intType
    val numberNodes by intType
    val target by stringType
    val logPath by stringType
}

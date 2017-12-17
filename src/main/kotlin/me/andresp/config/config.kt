package me.andresp.config

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.intType

object config : PropertyGroup() {
    val httpPort by intType
    val numberNodes by intType
}

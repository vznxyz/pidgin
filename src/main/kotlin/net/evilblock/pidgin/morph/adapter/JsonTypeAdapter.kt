package net.evilblock.pidgin.morph.adapter

import com.google.gson.JsonElement

interface JsonTypeAdapter<T : Any> {

    fun toJson(src: T): JsonElement

    fun toType(element: JsonElement): T

}

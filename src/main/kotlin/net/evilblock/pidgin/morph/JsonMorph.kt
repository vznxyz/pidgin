package net.evilblock.pidgin.morph

import net.evilblock.pidgin.morph.adapter.JsonTypeAdapter
import net.evilblock.pidgin.morph.adapter.impl.UUIDTypeAdapter
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.HashMap
import java.util.UUID

/**
 * JsonMorph allows you to easily serialize Java types into
 * JSON by using purely reflection or serialization through
 * type adapters.
 */
class JsonMorph {

    private val typeAdapters: MutableMap<Class<*>, JsonTypeAdapter<Any>>

    init {
        this.typeAdapters = HashMap(DEFAULT_ADAPTERS)
    }

    fun <T : Any> registerTypeAdapter(typeAdapter: JsonTypeAdapter<T>, type: Class<T>): JsonMorph {
        this.typeAdapters[type] = typeAdapter as JsonTypeAdapter<Any>
        return this
    }

    fun <T : Any> fromJson(json: JsonObject, typeOfSrc: Type): T? {
        return null
    }

    fun <T : Any> fromObject(src: T): JsonElement {
        if (src is JsonElement) {
            return src
        } else if (typeAdapters.containsKey(src::class.java)) {
            return typeAdapters[src::class.java]!!.toJson(src)
        } else if (isSupportedType(src::class.java)) {
            return serializeType(src)
        } else if (src::class.java.isArray) {
            val array = JsonArray()

            for (i in 0 until Array.getLength(src)) {
                array.add(fromObject(Array.get(src, i)))
            }

            return array
        } else if (Map::class.java.isAssignableFrom(src.javaClass)) {
            val obj = JsonObject()

            for (entry in (src as Map<*, *>).entries) {
                if (entry.value != null) {
                    obj.add(entry.key.toString(), fromObject(entry.value!!))
                }
            }

            return obj
        } else if (Collection::class.java.isAssignableFrom(src.javaClass)) {
            val array = JsonArray()

            for (obj in src as Collection<*>) {
                if (obj != null) {
                    array.add(fromObject(obj))
                }
            }

            return array
        } else {
            val obj = JsonObject()

            for (field in src::class.java.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) {
                    continue
                }

                if (!field.isAccessible) {
                    field.isAccessible = true
                }

                try {
                    if (typeAdapters.containsKey(field.type)) {
                        // JsonObject#add is null-safe
                        obj.add(field.name, typeAdapters[field.type]!!.toJson(field.get(src)))
                    } else if (isSupportedType(field.type)) {
                        obj.add(field.name, serializeType(field.get(src)))
                    } else {
                        obj.add(field.name, fromObject(field.get(src)))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

            return obj
        }
    }

    companion object {

        private val JSON_PRIMITIVE_EMPTY_CONSTRUCTOR: Constructor<*>
        private val DEFAULT_ADAPTERS: MutableMap<Class<*>, JsonTypeAdapter<Any>>

        init {
            try {
                JSON_PRIMITIVE_EMPTY_CONSTRUCTOR = JsonPrimitive::class.java.getDeclaredConstructor(Any::class.java)
                JSON_PRIMITIVE_EMPTY_CONSTRUCTOR.isAccessible = true

                DEFAULT_ADAPTERS = HashMap()
                DEFAULT_ADAPTERS[UUID::class.java] = UUIDTypeAdapter() as JsonTypeAdapter<Any>
            } catch (e: Exception) {
                throw RuntimeException("Could not process static block", e)
            }
        }

        private fun <T> isSupportedType(klass: Class<T>): Boolean {
            return klass.isPrimitive ||
                    klass == String::class.java ||
                    klass.name == Boolean::class.javaObjectType.name ||
                    klass == Char::class.java ||
                    klass.name == Char::class.javaObjectType.name ||
                    klass == Boolean::class.java ||
                    klass.name == Boolean::class.javaObjectType.name ||
                    Number::class.java.isAssignableFrom(klass)
        }

        private fun serializeType(`object`: Any?): JsonElement {
            return if (`object` == null) {
                JsonNull.INSTANCE
            } else {
                try {
                    JSON_PRIMITIVE_EMPTY_CONSTRUCTOR.newInstance(`object`) as JsonElement
                } catch (e: Exception) {
                    throw RuntimeException("Could not instantiate a new JsonPrimitive instance")
                }

            }
        }
    }

}

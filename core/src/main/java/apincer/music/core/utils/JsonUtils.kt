package apincer.music.core.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.Reader
import java.lang.reflect.Type

/**
 * Kotlin-native JSON helper offering static utilities for Java callers.
 */
object JsonUtils {

    @JvmStatic
    val jsonEngine: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    @JvmStatic
    fun <T : Any> parse(jsonString: String, clazz: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        val serializer = try {
            serializer(clazz) as KSerializer<T>
        } catch (e: Exception) {
            val companionField = clazz.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
            val method = companion.javaClass.getMethod("serializer")
            method.invoke(companion) as KSerializer<T>
        }
        return jsonEngine.decodeFromString(serializer, jsonString)
    }

    @JvmStatic
    fun <T : Any> parse(inputStream: InputStream, clazz: Class<T>): T {
        val text = inputStream.bufferedReader().use { it.readText() }
        return parse(text, clazz)
    }

    @JvmStatic
    fun <T : Any> parse(reader: Reader, clazz: Class<T>): T {
        val text = reader.readText()
        return parse(text, clazz)
    }

    @JvmStatic
    fun parsePlaylistCollection(jsonString: String): apincer.music.core.model.PlaylistCollection {
        return jsonEngine.decodeFromString(jsonString)
    }

    @JvmStatic
    fun parsePlaylistCollection(reader: Reader): apincer.music.core.model.PlaylistCollection {
        val text = reader.readText()
        return parsePlaylistCollection(text)
    }

    @JvmStatic
    fun <T : Any> parse(jsonString: String, type: Type): T {
        @Suppress("UNCHECKED_CAST")
        val serializer = serializer(type) as KSerializer<T>
        return jsonEngine.decodeFromString(serializer, jsonString)
    }

    @JvmStatic
    fun parseClientProfileList(inputStream: InputStream): List<apincer.music.core.server.ClientProfile> {
        val text = inputStream.bufferedReader().use { it.readText() }
        return jsonEngine.decodeFromString(text)
    }

    @JvmStatic
    fun <T : Any> stringify(obj: T, clazz: Class<T>): String {
        @Suppress("UNCHECKED_CAST")
        val serializer = serializer(clazz) as KSerializer<T>
        return jsonEngine.encodeToString(serializer, obj)
    }

    @JvmStatic
    fun toJson(map: Map<String, Any?>): String {
        return wrapJsonValue(map).toString()
    }

    private fun wrapJsonValue(value: Any?): Any {
        return when (value) {
            null -> org.json.JSONObject.NULL
            is Map<*, *> -> {
                val jsonObj = org.json.JSONObject()
                for ((k, v) in value) {
                    if (k != null) {
                        jsonObj.put(k.toString(), wrapJsonValue(v))
                    }
                }
                jsonObj
            }
            is Collection<*> -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(wrapJsonValue(item))
                }
                jsonArr
            }
            is Array<*> -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(wrapJsonValue(item))
                }
                jsonArr
            }
            is FloatArray -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(item.toDouble())
                }
                jsonArr
            }
            is DoubleArray -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(item)
                }
                jsonArr
            }
            is IntArray -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(item)
                }
                jsonArr
            }
            is LongArray -> {
                val jsonArr = org.json.JSONArray()
                for (item in value) {
                    jsonArr.put(item)
                }
                jsonArr
            }
            else -> value
        }
    }

    @JvmStatic
    fun toMap(jsonString: String): Map<String, Any?> {
        val obj = org.json.JSONObject(jsonString)
        return jsonObjectToMap(obj)
    }

    private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            map[key] = unwrapJsonValue(value)
        }
        return map
    }

    private fun unwrapJsonValue(value: Any?): Any? {
        return when (value) {
            is org.json.JSONObject -> jsonObjectToMap(value)
            is org.json.JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    list.add(unwrapJsonValue(value.get(i)))
                }
                list
            }
            org.json.JSONObject.NULL -> null
            else -> value
        }
    }
}

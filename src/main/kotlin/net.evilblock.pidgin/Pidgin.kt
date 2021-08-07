package net.evilblock.pidgin

import net.evilblock.pidgin.message.Message
import net.evilblock.pidgin.message.handler.IncomingMessageHandler
import net.evilblock.pidgin.message.handler.MessageExceptionHandler
import net.evilblock.pidgin.message.listener.MessageListener
import net.evilblock.pidgin.message.listener.MessageListenerData
import com.google.gson.*
import java.util.concurrent.ForkJoinPool
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import java.lang.IllegalStateException
import java.util.logging.Logger

/**
 * A Jedis Pub/Sub implementation.
 */
class Pidgin(
	private val channel: String,
	private val jedisPool: JedisPool,
	private val gson: Gson,
	private val options: PidginOptions
) {

	private var jedisPubSub: JedisPubSub? = null
	private val listeners: MutableMap<String, MutableList<MessageListenerData>> = hashMapOf()

	var debug: Boolean = false

	init {
		setupPubSub()
	}

	fun close() {
		if (jedisPubSub != null && jedisPubSub!!.isSubscribed) {
			jedisPubSub!!.unsubscribe()
		}

		jedisPool.close()
	}

	@JvmOverloads
	fun sendMessage(message: Message, exceptionHandler: MessageExceptionHandler? = null) {
		try {
			jedisPool.resource.use { jedis -> jedis.publish(channel, message.id + ";" + gson.toJsonTree(message.data).toString()) }
		} catch (e: Exception) {
			exceptionHandler?.onException(e)
		}
	}

	fun registerListener(messageListener: MessageListener) {
		for (method in messageListener::class.java.declaredMethods) {
			if (method.getDeclaredAnnotation(IncomingMessageHandler::class.java) != null && method.parameters.isNotEmpty()) {
				if (!JsonObject::class.java.isAssignableFrom(method.parameters[0].type)) {
					throw IllegalStateException("First parameter should be of JsonObject type")
				}

				val messageId = method.getDeclaredAnnotation(IncomingMessageHandler::class.java).value
				listeners.putIfAbsent(messageId, arrayListOf())
				listeners[messageId]!!.add(MessageListenerData(messageListener, method, messageId))
			}
		}
	}

	private fun setupPubSub() {
		jedisPubSub = object : JedisPubSub() {
			override fun onMessage(channel: String, message: String) {
				if (channel.equals(this@Pidgin.channel, ignoreCase = true)) {
					try {
						val breakAt = message.indexOf(';')
						val messageId = message.substring(0, breakAt)

						if (listeners.containsKey(messageId)) {
							val messageData = gson.fromJson(message.substring(breakAt + 1, message.length), JsonObject::class.java)

							for (listener in listeners[messageId]!!) {
								listener.method.invoke(listener.instance, messageData)
							}
						}
					} catch (e: JsonParseException) {
						Logger.getGlobal().severe("[Pidgin] Failed to parse message into JSON")
						e.printStackTrace()
					} catch (e: Exception) {
						Logger.getGlobal().severe("[Pidgin] Failed to handle message")
						e.printStackTrace()
					}
				}
			}
		}

		if (options.async) {
			ForkJoinPool.commonPool().execute {
				jedisPool.resource.use { jedis -> jedis.subscribe(jedisPubSub!!, channel) }
			}
		} else {
			jedisPool.resource.use { jedis -> jedis.subscribe(jedisPubSub!!, channel) }
		}
	}

}
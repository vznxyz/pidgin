# Pidgin
A Redis PubSub implementation that allows for bukkit-fashion handling of messages

## Creating a Pidgin instance
```
val jedisPool = JedisPool("127.0.0.1", 6379)
val pidgin = Pidgin("Stark:PROXY", jedisPool, PidginOptions(async = true))
```

## Sending a Message
```
val data = mapOf(
        "server": {
            "id": "Test",
            "port": 25565
        },
        "updates": {
            "slots": 500
        }
)

val message = Message("SERVER_UPDATE", data))

pidgin.sendMessage(message)
```

## Registering a MessageListener
```
object ProxyMessageListener : MessageListener {
    @IncomingMessageHandler("SERVER_UPDATE")
    fun onServerUpdate(data: JsonObject) {
        ...
    }
}
```

```
val pidgin = ...
pidgin.registerListener(ProxyMessageListener)
```

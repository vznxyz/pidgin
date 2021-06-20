package net.evilblock.pidgin.message

class Message(var id: String, var data: Map<String, Any?>) {

    constructor(id: String) : this(id, hashMapOf())

}

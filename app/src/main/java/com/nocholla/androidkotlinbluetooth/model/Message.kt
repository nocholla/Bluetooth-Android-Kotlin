package com.nocholla.androidkotlinbluetooth.model

class Message {
    var id: Int? = null
    var message: String? = null
    var senderName: String? = null

    constructor(id: Int, message: String, senderName: String){
        this.id = id
        this.message = message
        this.senderName = senderName
    }
}
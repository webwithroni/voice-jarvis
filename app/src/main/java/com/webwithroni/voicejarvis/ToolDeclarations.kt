package com.webwithroni.voicejarvis

import org.json.JSONArray
import org.json.JSONObject

object ToolDeclarations {

    private fun fn(name: String, description: String, params: JSONObject): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", params)
        }
    }

    private fun schema(props: JSONObject, required: List<String>): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", props)
            put("required", JSONArray(required))
        }
    }

    private fun strProp(desc: String) = JSONObject().put("type", "STRING").put("description", desc)
    private fun intProp(desc: String) = JSONObject().put("type", "INTEGER").put("description", desc)
    private fun boolProp(desc: String) = JSONObject().put("type", "BOOLEAN").put("description", desc)

    fun all(): JSONArray = JSONArray().apply {
        put(fn("call_contact", "Call a saved contact by name, or a raw phone number.",
            schema(JSONObject().put("name_or_number", strProp("Contact name or phone number")), listOf("name_or_number"))))

        put(fn("send_whatsapp", "Open a WhatsApp chat with a contact and pre-fill a message. The user still taps Send themselves.",
            schema(JSONObject()
                .put("name_or_number", strProp("Contact name or phone number"))
                .put("message", strProp("Message text to pre-fill")),
                listOf("name_or_number", "message"))))

        put(fn("send_sms", "Open the SMS app with a contact and pre-fill a message. The user still taps Send themselves.",
            schema(JSONObject()
                .put("name_or_number", strProp("Contact name or phone number"))
                .put("message", strProp("Message text to pre-fill")),
                listOf("name_or_number", "message"))))

        put(fn("open_app", "Open an installed app by its name.",
            schema(JSONObject().put("app_name", strProp("Name of the app, e.g. WhatsApp, YouTube")), listOf("app_name"))))

        put(fn("toggle_flashlight", "Turn the phone's flashlight/torch on or off.",
            schema(JSONObject().put("on", boolProp("true to turn on, false to turn off")), listOf("on"))))

        put(fn("set_alarm", "Set an alarm at a given time.",
            schema(JSONObject()
                .put("hour", intProp("Hour in 24-hour format, 0-23"))
                .put("minute", intProp("Minute, 0-59"))
                .put("label", strProp("Optional label for the alarm")),
                listOf("hour", "minute"))))

        put(fn("set_timer", "Start a countdown timer.",
            schema(JSONObject()
                .put("seconds", intProp("Duration in seconds"))
                .put("label", strProp("Optional label for the timer")),
                listOf("seconds"))))

        put(fn("get_battery", "Get the current battery level and charging status.",
            schema(JSONObject(), listOf())))
    }
}

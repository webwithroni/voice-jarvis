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

        put(fn("search_web", "Search the web for real-time or current information: news, prices, scores, weather, or any fact that may have changed recently.",
            schema(JSONObject().put("query", strProp("The search query")), listOf("query"))))

        put(fn(
            "deep_research",
            "Perform multi-source research for questions that need a deeper current analysis, multiple sources, comparisons, recent developments, or important points. Use this instead of search_web when the user explicitly asks for research, latest AI news with important points, comparisons, a detailed analysis, or a sourced overview.",
            schema(
                JSONObject().put(
                    "query",
                    strProp("The complete research question")
                ),
                listOf("query")
            )
        ))

        put(fn("media_control", "Control whatever media is currently playing on the phone.",
            schema(JSONObject().put("action", strProp("One of: play_pause, next, previous, stop")), listOf("action"))))

        put(fn("set_volume", "Set the media volume as a percentage.",
            schema(JSONObject().put("percent", intProp("Volume level 0-100")), listOf("percent"))))

        put(fn("open_browser", "Open the default web browser, optionally at a specific URL.",
            schema(JSONObject().put("url", strProp("Optional URL to open; leave empty for homepage")), listOf())))

        put(fn("search_google", "Search Google for a query and show the results in the browser.",
            schema(JSONObject().put("query", strProp("The search query")), listOf("query"))))

        put(fn("navigate_to", "Start turn-by-turn navigation in Google Maps to a destination.",
            schema(JSONObject().put("destination", strProp("Place name or address to navigate to")), listOf("destination"))))

        put(fn("lookup_contact", "Find a contact's phone number without calling or messaging them.",
            schema(JSONObject().put("name", strProp("Contact name to look up")), listOf("name"))))

        put(fn("set_clipboard", "Copy text to the device clipboard.",
            schema(JSONObject().put("text", strProp("Text to copy")), listOf("text"))))

        put(fn("get_location", "Get the user's current address and coordinates.",
            schema(JSONObject(), listOf())))

        put(fn("read_screen", "Read the current screen contents as a numbered list of elements. Use this first when you need to control an app that has no dedicated tool.",
            schema(JSONObject(), listOf())))

        put(fn("tap_element", "Tap a screen element by its numeric id from the most recent read_screen call.",
            schema(JSONObject().put("id", intProp("The element id from read_screen")), listOf("id"))))

        put(fn("type_text", "Type text into a screen element by its numeric id from the most recent read_screen call.",
            schema(JSONObject()
                .put("id", intProp("The element id from read_screen"))
                .put("text", strProp("Text to type")),
                listOf("id", "text"))))

        put(fn("scroll_screen", "Scroll the current screen up or down.",
            schema(JSONObject().put("direction", strProp("'up' or 'down'")), listOf("direction"))))

        put(fn("go_back", "Press the Android back button.", schema(JSONObject(), listOf())))
        put(fn("go_home", "Go to the Android home screen.", schema(JSONObject(), listOf())))

        put(fn("send_last_message", "Tap the Send button to actually send the message that was just drafted in WhatsApp or SMS. Only call this after the user explicitly confirms they want it sent.",
            schema(JSONObject(), listOf())))

        put(fn("answer_call", "Answer the currently ringing incoming call.",
            schema(JSONObject(), listOf())))

        put(fn("end_call", "End or decline the current call.",
            schema(JSONObject(), listOf())))

        put(fn("open_accessibility_settings", "Open Android's Accessibility settings so the user can enable Jarvis's screen-control permission. Only use this if a screen-automation action fails because the permission isn't granted.",
            schema(JSONObject(), listOf())))
    }
}

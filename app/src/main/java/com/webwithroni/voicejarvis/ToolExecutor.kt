package com.webwithroni.voicejarvis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class ToolExecutor(private val context: Context) {

    fun execute(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "send_whatsapp" -> sendWhatsapp(args.optString("name_or_number"), args.optString("message"))
                "open_app" -> openApp(args.optString("app_name"))
                "toggle_flashlight" -> toggleFlashlight(args.optBoolean("on", true))
                "set_alarm" -> setAlarm(args.optInt("hour"), args.optInt("minute"), args.optString("label"))
                "set_timer" -> setTimer(args.optInt("seconds"), args.optString("label"))
                "get_battery" -> getBattery()

                "search_web" ->
                    searchWeb(
                        args.optString("query")
                    )

                "deep_research" ->
                    ResearchRouter.research(
                        args.optString("query")
                    )

                "media_control" ->
                    mediaControl(
                        args.optString("action")
                    )
                "set_volume" -> setVolume(args.optInt("percent"))
                "open_browser" -> openBrowser(args.optString("url"))
                "search_google" -> searchGoogle(args.optString("query"))
                "navigate_to" -> navigateTo(args.optString("destination"))
                "lookup_contact" -> lookupContact(args.optString("name"))
                "set_clipboard" -> setClipboard(args.optString("text"))
                "get_location" -> getLocation()
                "read_screen" -> readScreen()
                "tap_element" -> tapElement(args.optInt("id"))
                "type_text" -> typeText(args.optInt("id"), args.optString("text"))
                "scroll_screen" -> scrollScreen(args.optString("direction"))
                "go_back" -> goBack()
                "go_home" -> goHome()
                "open_accessibility_settings" -> openAccessibilitySettings()
                "send_last_message" -> sendLastMessage()
                "answer_call" -> answerCall()
                "end_call" -> endCall()
                else -> result(false, "Unknown tool: $name")
            }
        } catch (e: Exception) {
            result(false, "Error: ${e.message}")
        }
    }

    private fun result(ok: Boolean, message: String) = JSONObject().put("success", ok).put("message", message)

    private fun resolveNumber(nameOrNumber: String): String? {
        if (nameOrNumber.any { it.isDigit() } && nameOrNumber.count { it.isDigit() } >= 6) return nameOrNumber
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameOrNumber%"), null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(numIdx)
            }
        }
        return null
    }

    private fun sendWhatsapp(nameOrNumber: String, message: String): JSONObject {
        val number = resolveNumber(nameOrNumber) ?: return result(false, "Could not find a contact matching '$nameOrNumber'")
        val cleanNumber = number.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            context.startActivity(intent)
            result(true, "WhatsApp message drafted for $nameOrNumber. Ask the user to confirm before sending.")
        } catch (e: Exception) {
            result(false, "WhatsApp is not installed")
        }
    }

    private fun openApp(appName: String): JSONObject {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val match = resolves.firstOrNull { it.loadLabel(pm).toString().contains(appName, ignoreCase = true) }
            ?: return result(false, "App '$appName' not found on this device")
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return result(false, "Could not launch '$appName'")
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(launch)
        return result(true, "Opened ${match.loadLabel(pm)}")
    }

    private fun toggleFlashlight(on: Boolean): JSONObject {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return result(false, "No camera found")
        cameraManager.setTorchMode(cameraId, on)
        return result(true, if (on) "Flashlight on" else "Flashlight off")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): JSONObject {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return result(true, "Alarm set for $hour:${minute.toString().padStart(2, '0')}")
    }

    private fun setTimer(seconds: Int, label: String): JSONObject {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return result(true, "Timer set for $seconds seconds")
    }

    private fun getBattery(): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        return result(true, "Battery is at $level percent, ${if (isCharging) "charging" else "not charging"}")
    }

    private fun searchWeb(query: String): JSONObject {
        if (BuildConfig.TAVILY_API_KEY.isBlank()) return result(false, "Web search is not configured")
        return try {
            val conn = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 12000

            val body = JSONObject().apply {

                put(
                    "query",
                    query
                )

                put(
                    "search_depth",
                    "fast"
                )

                put(
                    "max_results",
                    5
                )

                put(
                    "include_answer",
                    false
                )

                put(
                    "auto_parameters",
                    false
                )
            }

            conn.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.TAVILY_API_KEY}"
            )
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) return result(false, "Search failed (HTTP " + conn.responseCode + ")")

            val responseJson = JSONObject(conn.inputStream.bufferedReader().readText())
            val results = responseJson.optJSONArray("results") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until minOf(results.length(), 3)) {
                val r = results.getJSONObject(i)
                val title = r.optString("title")
                val url = r.optString("url")
                val snippet =
                    r.optString("content")
                        .replace(Regex("\\s+"), " ")
                        .take(420)

                lines.add(
                    "- " +
                        title +
                        ": " +
                        snippet +
                        if (url.isNotBlank()) {
                            " [" + url + "]"
                        } else {
                            ""
                        }
                )
            }
            if (lines.isEmpty()) result(false, "No results found for '" + query + "'")
            else result(true, lines.joinToString(System.lineSeparator()))
        } catch (e: Exception) {
            result(false, "Search error: " + e.message)
        }
    }

    private fun mediaControl(action: String): JSONObject {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (action) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return result(false, "Unknown media action '$action'")
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return result(true, "Media: $action")
    }

    private fun setVolume(percent: Int): JSONObject {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return result(true, "Volume set to $percent percent")
    }

    private fun openBrowser(url: String): JSONObject {
        val target = if (url.isBlank()) "https://www.google.com"
            else if (!url.startsWith("http")) "https://$url" else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return result(true, "Opened browser")
    }

    private fun searchGoogle(query: String): JSONObject {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return result(true, "Searching Google for '$query'")
    }

    private fun navigateTo(destination: String): JSONObject {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            result(true, "Navigating to $destination")
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(fallback)
            result(true, "Opened map for $destination")
        }
    }

    private fun lookupContact(name: String): JSONObject {
        val number = resolveNumber(name) ?: return result(false, "Could not find a contact matching '$name'")
        return result(true, "$name's number is $number")
    }

    private fun setClipboard(text: String): JSONObject {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Jarvis", text))
        return result(true, "Copied to clipboard")
    }

    private fun getLocation(): JSONObject {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return result(false, "Location permission not granted")

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var location: android.location.Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) { location = loc; break }
            } catch (e: Exception) { }
        }
        if (location == null) return result(false, "Could not determine current location")

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val addressLine = addresses?.firstOrNull()?.getAddressLine(0)
            if (!addressLine.isNullOrBlank()) result(true, addressLine)
            else result(true, "Lat ${location.latitude}, Lng ${location.longitude}")
        } catch (e: Exception) {
            result(true, "Lat ${location.latitude}, Lng ${location.longitude}")
        }
    }

    private fun accessibilityService() = VoiceJarvisAccessibilityService.instance

    private fun readScreen(): JSONObject {
        val svc = accessibilityService()
            ?: return result(false, "Screen automation permission not enabled yet — opening settings.").also { openAccessibilitySettings() }
        return result(true, svc.readScreen())
    }

    private fun tapElement(id: Int): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.tapElement(id)) result(true, "Tapped element $id") else result(false, "Could not tap element $id")
    }

    private fun typeText(id: Int, text: String): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.typeText(id, text)) result(true, "Typed text into element $id") else result(false, "Could not type into element $id")
    }

    private fun scrollScreen(direction: String): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.scroll(direction)) result(true, "Scrolled $direction") else result(false, "Nothing scrollable found")
    }

    private fun goBack(): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return result(svc.goBack(), "Pressed back")
    }

    private fun goHome(): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return result(svc.goHome(), "Went to home screen")
    }

    private fun openAccessibilitySettings(): JSONObject {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return result(true, "Opened Accessibility settings — please enable Voice Jarvis there.")
    }

    private fun sendLastMessage(): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.tapByTextMatch(listOf("Send"))) result(true, "Message sent")
        else result(false, "Could not find a Send button on screen")
    }

    private fun answerCall(): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.tapByTextMatch(listOf("Answer", "Accept", "Answer call"))) result(true, "Call answered")
        else result(false, "Could not find an answer button — the call may have already ended")
    }

    private fun endCall(): JSONObject {
        val svc = accessibilityService() ?: return result(false, "Screen automation permission not enabled")
        return if (svc.tapByTextMatch(listOf("End call", "Hang up", "Decline", "End"))) result(true, "Call ended")
        else result(false, "Could not find an end call button")
    }
}

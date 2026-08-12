package com.webwithroni.voicejarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ToolExecutor(private val context: Context) {

    fun execute(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "call_contact" -> callContact(args.optString("name_or_number"))
                "send_whatsapp" -> sendWhatsapp(args.optString("name_or_number"), args.optString("message"))
                "send_sms" -> sendSms(args.optString("name_or_number"), args.optString("message"))
                "open_app" -> openApp(args.optString("app_name"))
                "toggle_flashlight" -> toggleFlashlight(args.optBoolean("on", true))
                "set_alarm" -> setAlarm(args.optInt("hour"), args.optInt("minute"), args.optString("label"))
                "set_timer" -> setTimer(args.optInt("seconds"), args.optString("label"))
                "get_battery" -> getBattery()
                "search_web" -> searchWeb(args.optString("query"))
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

    private fun callContact(nameOrNumber: String): JSONObject {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return result(false, "Call permission not granted")
        val number = resolveNumber(nameOrNumber) ?: return result(false, "Could not find a contact matching '$nameOrNumber'")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return result(true, "Calling $nameOrNumber")
    }

    private fun sendWhatsapp(nameOrNumber: String, message: String): JSONObject {
        val number = resolveNumber(nameOrNumber) ?: return result(false, "Could not find a contact matching '$nameOrNumber'")
        val cleanNumber = number.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            context.startActivity(intent)
            result(true, "Opened WhatsApp chat with $nameOrNumber, message ready to send")
        } catch (e: Exception) {
            result(false, "WhatsApp is not installed")
        }
    }

    private fun sendSms(nameOrNumber: String, message: String): JSONObject {
        val number = resolveNumber(nameOrNumber) ?: return result(false, "Could not find a contact matching '$nameOrNumber'")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return result(true, "Opened SMS to $nameOrNumber, message ready to send")
    }

    private fun openApp(appName: String): JSONObject {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) }
            ?: return result(false, "App '$appName' not found")
        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return result(false, "Could not launch '$appName'")
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(launchIntent)
        return result(true, "Opened ${pm.getApplicationLabel(match)}")
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
                put("api_key", BuildConfig.TAVILY_API_KEY)
                put("query", query)
                put("max_results", 3)
                put("search_depth", "basic")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) return result(false, "Search failed (HTTP " + conn.responseCode + ")")

            val responseJson = JSONObject(conn.inputStream.bufferedReader().readText())
            val results = responseJson.optJSONArray("results") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until minOf(results.length(), 3)) {
                val r = results.getJSONObject(i)
                val title = r.optString("title")
                val snippet = r.optString("content").take(200)
                lines.add("- " + title + ": " + snippet)
            }
            if (lines.isEmpty()) result(false, "No results found for '" + query + "'")
            else result(true, lines.joinToString(System.lineSeparator()))
        } catch (e: Exception) {
            result(false, "Search error: " + e.message)
        }
    }

    private fun getBattery(): JSONObject {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        return result(true, "Battery is at $level percent, ${if (isCharging) "charging" else "not charging"}")
    }
}

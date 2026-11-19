package com.yalkahaleh.masroof.capture

import com.yalkahaleh.masroof.data.CapturedNotification
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExport {
    fun toJson(items: List<CapturedNotification>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("time", iso.format(Date(it.postedAt)))
                    .put("package", it.packageName)
                    .put("app", it.appLabel)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("bigText", it.bigText)
                    .put("subText", it.subText)
                    .put("category", it.category)
            )
        }
        return JSONObject().put("count", items.size).put("notifications", array).toString(2)
    }
}

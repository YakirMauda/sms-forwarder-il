package com.qrcommunication.smsforwarder.domain.rules

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON (de)serialization of condition groups and targets, stored in TEXT columns of
 * `forwarding_rules`. Unknown operators/channels are skipped instead of crashing, so an
 * older app version can read data written by a newer one.
 */
object RuleJson {

    fun encodeGroup(group: ConditionGroup): String = JSONObject()
        .put("logic", group.logic.name)
        .put(
            "conditions",
            JSONArray().apply {
                group.conditions.forEach { put(JSONObject().put("op", it.operator.name).put("value", it.value)) }
            },
        )
        .toString()

    fun decodeGroup(json: String?): ConditionGroup? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val logic = GroupLogic.entries.firstOrNull { it.name == obj.optString("logic") } ?: GroupLogic.AND
            val arr = obj.optJSONArray("conditions") ?: JSONArray()
            val conditions = (0 until arr.length()).mapNotNull { i ->
                val c = arr.getJSONObject(i)
                MatchOperator.fromName(c.optString("op"))?.let { Condition(it, c.optString("value")) }
            }
            ConditionGroup(logic, conditions)
        }.getOrNull()
    }

    fun encodeTargets(targets: List<Target>): String = JSONArray().apply {
        targets.forEach { put(JSONObject().put("channel", it.channel.name).put("address", it.address)) }
    }.toString()

    fun decodeTargets(json: String?): List<Target>? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val t = arr.getJSONObject(i)
                Channel.entries.firstOrNull { it.name == t.optString("channel") }
                    ?.let { Target(it, t.optString("address")) }
            }
        }.getOrNull()
    }
}

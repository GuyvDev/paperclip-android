package com.bu.paperclip.ui.screens

import com.bu.paperclip.data.Interaction
import com.bu.paperclip.data.IssueComment
import com.bu.paperclip.net.Api
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.util.UUID

/**
 * Paperclip's zod validators treat many optional fields as non-nullable (`interrupt`, `reason`,
 * `attachmentIds`…), so an explicit `null` is a 400. These builders omit absent values instead.
 */
internal fun issueBody(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    pairs.mapNotNull { (k, v) ->
        val el: JsonElement = when (v) {
            null -> return@mapNotNull null
            is JsonElement -> if (v is JsonNull) return@mapNotNull null else v
            is String -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Collection<*> -> buildJsonArray { v.forEach { x -> add(JsonPrimitive(x.toString())) } }
            else -> JsonPrimitive(v.toString())
        }
        k to el
    }.toMap(),
)

internal suspend fun issueAddComment(issueId: String, body: String, interrupt: Boolean, attachmentIds: List<String>): IssueComment =
    Api.post(
        "/issues/$issueId/comments",
        issueBody(
            "body" to body,
            "clientRequestId" to UUID.randomUUID().toString(),
            "interrupt" to (if (interrupt) true else null),
            "attachmentIds" to attachmentIds.takeIf { it.isNotEmpty() },
        ),
    )

private fun issueInteractionPath(issueId: String, interactionId: String, action: String) = "/issues/$issueId/interactions/$interactionId/$action"

internal suspend fun issueRejectInteraction(issueId: String, interactionId: String, reason: String?): Interaction =
    Api.post(issueInteractionPath(issueId, interactionId, "reject"), issueBody("reason" to reason?.trim()?.ifEmpty { null }))

internal suspend fun issueSkipInteraction(issueId: String, interactionId: String): Interaction =
    Api.post(issueInteractionPath(issueId, interactionId, "skip"), issueBody())

internal suspend fun issueCancelInteraction(issueId: String, interactionId: String): Interaction =
    Api.post(issueInteractionPath(issueId, interactionId, "cancel"), issueBody())

internal suspend fun issueAcceptInteraction(issueId: String, interactionId: String, body: JsonObject = issueBody()): Interaction =
    Api.post(issueInteractionPath(issueId, interactionId, "accept"), body)

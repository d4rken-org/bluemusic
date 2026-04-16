package testhelpers.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
}

fun String.toComparableJson(): String {
    val element = Json.parseToJsonElement(this)
    return prettyJson.encodeToString(JsonElement.serializer(), element)
}

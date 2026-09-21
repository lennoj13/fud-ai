package com.apoorvdarshan.calorietracker.services.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolRegistryTest {

    @Test
    fun getToolsListJson_returnsExpectedTools() {
        val tools = McpToolRegistry.getToolsListJson()
        assertTrue("Debe registrar al menos 9 herramientas MCP", tools.length() >= 9)

        val toolNames = mutableSetOf<String>()
        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            val name = tool.getString("name")
            toolNames.add(name)

            assertNotNull("Herramienta $name debe tener descripción", tool.getString("description"))
            val schema = tool.getJSONObject("inputSchema")
            assertEquals("object", schema.getString("type"))
            assertNotNull("Herramienta $name debe tener properties", schema.getJSONObject("properties"))
        }

        val requiredTools = listOf(
            "get_today_summary",
            "get_food_entries",
            "log_food_entry",
            "delete_food_entry",
            "get_weight_history",
            "log_weight",
            "log_water",
            "get_user_profile",
            "control_fasting"
        )

        for (required in requiredTools) {
            assertTrue("Debe incluir la herramienta $required", toolNames.contains(required))
        }
    }

    @Test
    fun logFoodEntry_hasCorrectRequiredParameters() {
        val tools = McpToolRegistry.getToolsListJson()
        var logFoodTool: org.json.JSONObject? = null
        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            if (tool.getString("name") == "log_food_entry") {
                logFoodTool = tool
                break
            }
        }

        assertNotNull("log_food_entry debe existir", logFoodTool)
        val schema = logFoodTool!!.getJSONObject("inputSchema")
        val requiredArray = schema.getJSONArray("required")

        val requiredSet = mutableSetOf<String>()
        for (i in 0 until requiredArray.length()) {
            requiredSet.add(requiredArray.getString(i))
        }

        assertTrue("log_food_entry debe requerir 'name'", requiredSet.contains("name"))
        assertTrue("log_food_entry debe requerir 'calories'", requiredSet.contains("calories"))
        assertTrue("log_food_entry debe requerir 'protein'", requiredSet.contains("protein"))
        assertTrue("log_food_entry debe requerir 'carbs'", requiredSet.contains("carbs"))
        assertTrue("log_food_entry debe requerir 'fat'", requiredSet.contains("fat"))
    }
}

package com.apoorvdarshan.calorietracker.services.mcp

import com.apoorvdarshan.calorietracker.data.FastingRepository
import com.apoorvdarshan.calorietracker.data.FoodRepository
import com.apoorvdarshan.calorietracker.data.PreferencesStore
import com.apoorvdarshan.calorietracker.data.ProfileRepository
import com.apoorvdarshan.calorietracker.data.WaterRepository
import com.apoorvdarshan.calorietracker.data.WeightRepository
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.FoodSource
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.WaterEntry
import com.apoorvdarshan.calorietracker.models.WeightEntry
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Handles execution of read and write MCP tools against Fud AI repositories.
 */
class McpToolExecutor(
    private val foodRepository: FoodRepository,
    private val weightRepository: WeightRepository,
    private val waterRepository: WaterRepository,
    private val fastingRepository: FastingRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: PreferencesStore
) {

    suspend fun executeTool(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "get_today_summary" -> executeGetTodaySummary(args)
                "get_food_entries" -> executeGetFoodEntries(args)
                "log_food_entry" -> executeLogFoodEntry(args)
                "delete_food_entry" -> executeDeleteFoodEntry(args)
                "get_weight_history" -> executeGetWeightHistory(args)
                "log_weight" -> executeLogWeight(args)
                "log_water" -> executeLogWater(args)
                "get_user_profile" -> executeGetUserProfile()
                "control_fasting" -> executeControlFasting(args)
                else -> errorResult("Herramienta desconocida: '$name'")
            }
        } catch (e: Exception) {
            errorResult("Error al ejecutar $name: ${e.message}")
        }
    }

    // 1. get_today_summary
    private suspend fun executeGetTodaySummary(args: JSONObject): JSONObject {
        val targetDateStr = args.optString("date").takeIf { it.isNotBlank() }
        val targetDate = if (targetDateStr != null) {
            LocalDate.parse(targetDateStr)
        } else {
            LocalDate.now()
        }

        val entries = foodRepository.entries.first().filter {
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == targetDate
        }

        val totalCalories = entries.sumOf { it.calories }
        val totalProtein = round1(entries.sumOf { it.protein })
        val totalCarbs = round1(entries.sumOf { it.carbs })
        val totalFat = round1(entries.sumOf { it.fat })

        val profile = profileRepository.current()
        val calorieGoal = profile?.effectiveCalories ?: 2000
        val proteinGoal = profile?.effectiveProtein ?: 140
        val carbsGoal = profile?.effectiveCarbs ?: 200
        val fatGoal = profile?.effectiveFat ?: 65
        val caloriesRemaining = calorieGoal - totalCalories

        val waterEntries = waterRepository.entries.first().filter {
            it.date.atZone(ZoneId.systemDefault()).toLocalDate() == targetDate
        }
        val totalWaterMl = waterEntries.sumOf { it.milliliters }
        val waterGoalMl = prefs.waterDailyGoalMl.first()

        val activeFasting = fastingRepository.active()

        val mealsList = JSONArray()
        entries.sortedBy { it.timestamp }.forEach { entry ->
            mealsList.put(
                JSONObject().apply {
                    put("id", entry.id.toString())
                    put("name", entry.name)
                    put("calories", entry.calories)
                    put("protein", entry.protein)
                    put("carbs", entry.carbs)
                    put("fat", entry.fat)
                    put("meal_type", entry.mealType.name.lowercase())
                    put("time", entry.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            )
        }

        val result = JSONObject().apply {
            put("date", targetDate.toString())
            put("calories_consumed", totalCalories)
            put("calorie_goal", calorieGoal)
            put("calories_remaining", caloriesRemaining)
            put("protein_consumed_g", totalProtein)
            put("protein_goal_g", proteinGoal)
            put("carbs_consumed_g", totalCarbs)
            put("carbs_goal_g", carbsGoal)
            put("fat_consumed_g", totalFat)
            put("fat_goal_g", fatGoal)
            put("water_consumed_ml", totalWaterMl)
            put("water_goal_ml", waterGoalMl)
            put("fasting_active", activeFasting != null)
            activeFasting?.let {
                put("fasting_started_at", it.startedAt.toString())
                put("fasting_goal_hours", it.goalMinutes / 60)
            }
            put("meals_count", entries.size)
            put("meals", mealsList)
        }

        return successResult(result)
    }

    // 2. get_food_entries
    private suspend fun executeGetFoodEntries(args: JSONObject): JSONObject {
        val fromStr = args.optString("from").takeIf { it.isNotBlank() }
        val toStr = args.optString("to").takeIf { it.isNotBlank() }
        val mealTypeStr = args.optString("meal_type").takeIf { it.isNotBlank() }?.uppercase()
        val query = args.optString("query").takeIf { it.isNotBlank() }?.lowercase()
        val limit = (args.opt("limit") as? Number)?.toInt()?.coerceIn(1, 200) ?: 50

        val fromDate = fromStr?.let { LocalDate.parse(it) }
        val toDate = toStr?.let { LocalDate.parse(it) }

        var list = foodRepository.entries.first()

        if (fromDate != null) {
            list = list.filter { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() >= fromDate }
        }
        if (toDate != null) {
            list = list.filter { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() <= toDate }
        }
        if (mealTypeStr != null) {
            list = list.filter { it.mealType.name == mealTypeStr }
        }
        if (query != null) {
            list = list.filter { it.name.lowercase().contains(query) }
        }

        val sorted = list.sortedByDescending { it.timestamp }.take(limit)

        val array = JSONArray()
        sorted.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id.toString())
                    put("name", entry.name)
                    put("calories", entry.calories)
                    put("protein", entry.protein)
                    put("carbs", entry.carbs)
                    put("fat", entry.fat)
                    put("meal_type", entry.mealType.name.lowercase())
                    put("timestamp", entry.timestamp.toString())
                    entry.servingSizeGrams?.let { put("serving_size_grams", it) }
                    entry.customNote?.let { put("notes", it) }
                }
            )
        }

        val result = JSONObject().apply {
            put("count", sorted.size)
            put("entries", array)
        }
        return successResult(result)
    }

    // 3. log_food_entry (ESCRITURA)
    private suspend fun executeLogFoodEntry(args: JSONObject): JSONObject {
        val name = args.getString("name").trim()
        require(name.isNotEmpty()) { "El nombre del alimento no puede estar vacío" }
        val calories = args.getInt("calories")
        val protein = args.getDouble("protein")
        val carbs = args.getDouble("carbs")
        val fat = args.getDouble("fat")

        val mealType = args.optString("meal_type").takeIf { it.isNotBlank() }?.let {
            runCatching { MealType.valueOf(it.uppercase()) }.getOrNull()
        } ?: MealType.currentMeal

        val servingSizeGrams = if (args.has("serving_size_grams") && !args.isNull("serving_size_grams")) {
            args.getDouble("serving_size_grams")
        } else null

        val customNote = args.optString("notes").takeIf { it.isNotBlank() }

        val timestamp = args.optString("date_time").takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: Instant.now()

        val newEntry = FoodEntry(
            id = UUID.randomUUID(),
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            timestamp = timestamp,
            source = FoodSource.TEXT_INPUT,
            mealType = mealType,
            servingSizeGrams = servingSizeGrams,
            customNote = customNote
        )

        val added = foodRepository.addEntry(newEntry)
        if (!added) {
            return errorResult("No se pudo registrar la comida porque actualmente hay un ayuno activo en curso. Finaliza el ayuno primero.")
        }

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Comida '$name' registrada con éxito en el diario.")
            put("id", newEntry.id.toString())
            put("calories", calories)
            put("macros", "${protein}g P / ${carbs}g C / ${fat}g G")
            put("meal_type", mealType.name.lowercase())
            put("timestamp", timestamp.toString())
        }
        return successResult(result)
    }

    // 4. delete_food_entry (ESCRITURA)
    private suspend fun executeDeleteFoodEntry(args: JSONObject): JSONObject {
        val idStr = args.getString("id")
        val uuid = UUID.fromString(idStr)
        foodRepository.deleteEntry(uuid)

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Entrada $idStr eliminada correctamente del diario.")
        }
        return successResult(result)
    }

    // 5. get_weight_history
    private suspend fun executeGetWeightHistory(args: JSONObject): JSONObject {
        val limit = (args.opt("limit") as? Number)?.toInt()?.coerceIn(1, 365) ?: 30
        val entries = weightRepository.entries.first().sortedByDescending { it.date }.take(limit)
        val profile = profileRepository.current()

        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id.toString())
                    put("date", entry.date.toString())
                    put("weight_kg", round1(entry.weightKg))
                    put("weight_lbs", round1(entry.weightKg * 2.20462))
                }
            )
        }

        val result = JSONObject().apply {
            put("current_weight_kg", profile?.weightKg?.let { round1(it) })
            put("goal_weight_kg", profile?.goalWeightKg?.let { round1(it) })
            put("goal", profile?.goal?.name?.lowercase())
            put("history_count", entries.size)
            put("history", array)
        }
        return successResult(result)
    }

    // 6. log_weight (ESCRITURA)
    private suspend fun executeLogWeight(args: JSONObject): JSONObject {
        val weightKg = args.getDouble("weight_kg")
        val date = args.optString("date").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
        } ?: Instant.now()

        val newEntry = WeightEntry(
            id = UUID.randomUUID(),
            weightKg = weightKg,
            date = date
        )

        val event = weightRepository.addEntry(newEntry)

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Peso de ${round1(weightKg)} kg registrado con éxito.")
            put("id", newEntry.id.toString())
            put("weight_kg", round1(weightKg))
            put("weight_lbs", round1(weightKg * 2.20462))
            put("goal_reached", event != null)
        }
        return successResult(result)
    }

    // 7. log_water (ESCRITURA)
    private suspend fun executeLogWater(args: JSONObject): JSONObject {
        val milliliters = args.getInt("milliliters")
        require(milliliters > 0) { "La cantidad de agua debe ser mayor a 0 ml" }

        val newEntry = WaterEntry(
            id = UUID.randomUUID(),
            date = Instant.now(),
            milliliters = milliliters
        )
        waterRepository.add(newEntry)

        val todayWater = waterRepository.entries.first().filter {
            it.date.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        }.sumOf { it.milliliters }

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Registrados ${milliliters} ml de agua con éxito.")
            put("today_total_water_ml", todayWater)
        }
        return successResult(result)
    }

    // 8. get_user_profile
    private suspend fun executeGetUserProfile(): JSONObject {
        val profile = profileRepository.current()
        if (profile == null) {
            return errorResult("No se ha configurado un perfil en Fud AI todavía.")
        }

        val result = JSONObject().apply {
            put("name", profile.displayName)
            put("age", profile.age)
            put("gender", profile.gender.name.lowercase())
            put("height_cm", profile.heightCm)
            put("weight_kg", round1(profile.weightKg))
            put("goal", profile.goal.name.lowercase())
            put("activity_level", profile.activityLevel.name.lowercase())
            put("bmr", round1(profile.bmr))
            put("tdee", round1(profile.tdee))
            put("daily_calorie_target", profile.effectiveCalories)
            put("protein_target_g", profile.effectiveProtein)
            put("carbs_target_g", profile.effectiveCarbs)
            put("fat_target_g", profile.effectiveFat)
        }
        return successResult(result)
    }

    // 9. control_fasting
    private suspend fun executeControlFasting(args: JSONObject): JSONObject {
        val action = args.getString("action").lowercase()
        return when (action) {
            "status" -> {
                val active = fastingRepository.active()
                val result = JSONObject().apply {
                    put("is_active", active != null)
                    active?.let {
                        put("started_at", it.startedAt.toString())
                        put("goal_minutes", it.goalMinutes)
                        put("duration_minutes", it.durationSeconds(Instant.now()) / 60)
                    }
                }
                successResult(result)
            }
            "start" -> {
                val goalMinutes = (args.opt("goal_minutes") as? Number)?.toInt() ?: 960 // 16h
                val started = fastingRepository.start(goalMinutes)
                if (started == null) {
                    errorResult("Ya hay un ayuno en curso o se superpone con una sesión existente.")
                } else {
                    val result = JSONObject().apply {
                        put("status", "success")
                        put("message", "Ayuno iniciado con meta de ${goalMinutes / 60} horas.")
                        put("started_at", started.startedAt.toString())
                    }
                    successResult(result)
                }
            }
            "end" -> {
                val ended = fastingRepository.endActive()
                if (ended == null) {
                    errorResult("No hay ningún ayuno activo para finalizar.")
                } else {
                    val result = JSONObject().apply {
                        put("status", "success")
                        put("message", "Ayuno completado exitosamente.")
                        put("duration_hours", round1((ended.durationSeconds(Instant.now()) / 3600.0)))
                    }
                    successResult(result)
                }
            }
            "cancel" -> {
                fastingRepository.cancelActive()
                val result = JSONObject().apply {
                    put("status", "success")
                    put("message", "Ayuno activo cancelado.")
                }
                successResult(result)
            }
            else -> errorResult("Acción de ayuno inválida: '$action'. Usa: status, start, end, cancel.")
        }
    }

    private fun successResult(data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("content", JSONArray().put(JSONObject().apply {
                put("type", "text")
                put("text", data.toString(2))
            }))
            put("isError", false)
            put("structuredData", data)
        }
    }

    private fun errorResult(errorMessage: String): JSONObject {
        return JSONObject().apply {
            put("content", JSONArray().put(JSONObject().apply {
                put("type", "text")
                put("text", errorMessage)
            }))
            put("isError", true)
        }
    }

    private fun round1(value: Double): Double = String.format(Locale.US, "%.1f", value).toDouble()
}

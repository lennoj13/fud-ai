package com.apoorvdarshan.calorietracker.services.mcp

import android.util.Base64
import com.apoorvdarshan.calorietracker.data.FastingRepository
import com.apoorvdarshan.calorietracker.data.FoodRepository
import com.apoorvdarshan.calorietracker.data.PreferencesStore
import com.apoorvdarshan.calorietracker.data.ProfileRepository
import com.apoorvdarshan.calorietracker.data.WaterRepository
import com.apoorvdarshan.calorietracker.data.WeightRepository
import com.apoorvdarshan.calorietracker.data.WorkoutRepository
import com.apoorvdarshan.calorietracker.models.AutoBalanceMacro
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.FoodSource
import com.apoorvdarshan.calorietracker.models.MealIngredient
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.WaterEntry
import com.apoorvdarshan.calorietracker.models.WeightEntry
import com.apoorvdarshan.calorietracker.models.WeightGoal
import com.apoorvdarshan.calorietracker.models.WorkoutDate
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.services.FoodImageStore
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
    private val workoutRepository: WorkoutRepository,
    private val imageStore: FoodImageStore,
    private val prefs: PreferencesStore
) {

    suspend fun executeTool(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "get_today_summary" -> executeGetTodaySummary(args)
                "get_food_entries" -> executeGetFoodEntries(args)
                "log_food_entry" -> executeLogFoodEntry(args)
                "update_food_entry" -> executeUpdateFoodEntry(args)
                "delete_food_entry" -> executeDeleteFoodEntry(args)
                "get_user_profile" -> executeGetUserProfile()
                "update_user_goals" -> executeUpdateUserGoals(args)
                "get_weight_history" -> executeGetWeightHistory(args)
                "log_weight" -> executeLogWeight(args)
                "get_water_history" -> executeGetWaterHistory(args)
                "log_water" -> executeLogWater(args)
                "control_fasting" -> executeControlFasting(args)
                "get_workout_history" -> executeGetWorkoutHistory(args)
                "log_workout_session" -> executeLogWorkoutSession(args)
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

        val todayKey = WorkoutDate.key(targetDate)
        val completedSessions = workoutRepository.completedSessions.first()
        val todayWorkout = completedSessions.firstOrNull { it.diaryDateKey == todayKey && it.caloriesBurned != null }
        val workoutCaloriesBurned = todayWorkout?.caloriesBurned ?: 0

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
                    entry.servingSizeGrams?.let { put("serving_size_grams", it) }
                    entry.customNote?.let { put("notes", it) }
                    if (entry.ingredients.isNotEmpty()) {
                        val ingArr = JSONArray()
                        entry.ingredients.forEach { ing ->
                            ingArr.put(JSONObject().apply {
                                put("name", ing.name)
                                put("grams", ing.grams)
                                put("calories", ing.calories)
                                put("protein", ing.protein)
                                put("carbs", ing.carbs)
                                put("fat", ing.fat)
                            })
                        }
                        put("ingredients", ingArr)
                    }
                }
            )
        }

        val result = JSONObject().apply {
            put("date", targetDate.toString())
            put("calories_consumed", totalCalories)
            put("calorie_goal", calorieGoal)
            put("calories_remaining", caloriesRemaining)
            put("calories_burned_exercise", workoutCaloriesBurned)
            put("net_calories", totalCalories - workoutCaloriesBurned)
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
                    entry.imageFilename?.let { put("has_image", true) }
                    entry.fiber?.let { put("fiber", it) }
                    entry.sugar?.let { put("sugar", it) }
                    entry.sodium?.let { put("sodium", it) }
                    entry.potassium?.let { put("potassium", it) }
                    if (entry.ingredients.isNotEmpty()) {
                        val ingArr = JSONArray()
                        entry.ingredients.forEach { ing ->
                            ingArr.put(JSONObject().apply {
                                put("name", ing.name)
                                put("grams", ing.grams)
                                put("calories", ing.calories)
                                put("protein", ing.protein)
                                put("carbs", ing.carbs)
                                put("fat", ing.fat)
                            })
                        }
                        put("ingredients", ingArr)
                    }
                }
            )
        }

        val result = JSONObject().apply {
            put("count", sorted.size)
            put("entries", array)
        }
        return successResult(result)
    }

    // 3. log_food_entry (ESCRITURA CON FOTO E INGREDIENTES)
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

        val newEntryId = UUID.randomUUID()

        // Handle base64 image if passed
        var savedImageFilename: String? = null
        val imageBase64 = args.optString("image_base64").takeIf { it.isNotBlank() }
        if (imageBase64 != null) {
            try {
                val cleanBase64 = if (imageBase64.contains(",")) imageBase64.substringAfter(",") else imageBase64
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                savedImageFilename = imageStore.storeBytes(imageBytes, newEntryId)
            } catch (_: Exception) {}
        }

        // Handle ingredients breakdown
        val ingredientsList = mutableListOf<MealIngredient>()
        val ingredientsJson = args.optJSONArray("ingredients")
        if (ingredientsJson != null) {
            for (i in 0 until ingredientsJson.length()) {
                val item = ingredientsJson.optJSONObject(i) ?: continue
                val ingName = item.optString("name").trim()
                if (ingName.isNotEmpty()) {
                    ingredientsList.add(
                        MealIngredient(
                            name = ingName,
                            grams = item.optDouble("grams", 0.0),
                            calories = item.optInt("calories", 0),
                            protein = item.optDouble("protein", 0.0),
                            carbs = item.optDouble("carbs", 0.0),
                            fat = item.optDouble("fat", 0.0)
                        )
                    )
                }
            }
        }

        val fiber = args.optDoubleOrNull("fiber")
        val sugar = args.optDoubleOrNull("sugar")
        val sodium = args.optDoubleOrNull("sodium")
        val potassium = args.optDoubleOrNull("potassium")
        val saturatedFat = args.optDoubleOrNull("saturated_fat")
        val cholesterol = args.optDoubleOrNull("cholesterol")

        val newEntry = FoodEntry(
            id = newEntryId,
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            timestamp = timestamp,
            source = if (imageBase64 != null) FoodSource.SNAP_FOOD else FoodSource.TEXT_INPUT,
            mealType = mealType,
            servingSizeGrams = servingSizeGrams,
            customNote = customNote,
            imageFilename = savedImageFilename,
            ingredients = ingredientsList,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium,
            potassium = potassium,
            saturatedFat = saturatedFat,
            cholesterol = cholesterol
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
            put("has_image", savedImageFilename != null)
            put("ingredients_count", ingredientsList.size)
        }
        return successResult(result)
    }

    // 4. update_food_entry
    private suspend fun executeUpdateFoodEntry(args: JSONObject): JSONObject {
        val idStr = args.getString("id")
        val uuid = runCatching { UUID.fromString(idStr) }.getOrNull()
            ?: return errorResult("ID de comida inválido: '$idStr'")

        val entries = foodRepository.entries.first()
        val existing = entries.firstOrNull { it.id == uuid }
            ?: return errorResult("No se encontró ninguna comida con el ID $idStr")

        val updatedName = args.optString("name").takeIf { it.isNotBlank() } ?: existing.name
        val updatedCalories = if (args.has("calories") && !args.isNull("calories")) args.getInt("calories") else existing.calories
        val updatedProtein = if (args.has("protein") && !args.isNull("protein")) args.getDouble("protein") else existing.protein
        val updatedCarbs = if (args.has("carbs") && !args.isNull("carbs")) args.getDouble("carbs") else existing.carbs
        val updatedFat = if (args.has("fat") && !args.isNull("fat")) args.getDouble("fat") else existing.fat
        val updatedServing = if (args.has("serving_size_grams") && !args.isNull("serving_size_grams")) args.getDouble("serving_size_grams") else existing.servingSizeGrams
        val updatedNotes = if (args.has("notes")) args.optString("notes") else existing.customNote

        val updatedEntry = existing.copy(
            name = updatedName,
            calories = updatedCalories,
            protein = updatedProtein,
            carbs = updatedCarbs,
            fat = updatedFat,
            servingSizeGrams = updatedServing,
            customNote = updatedNotes
        )

        foodRepository.updateEntry(updatedEntry)

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Comida '$updatedName' actualizada exitosamente.")
            put("id", idStr)
            put("calories", updatedCalories)
            put("macros", "${updatedProtein}g P / ${updatedCarbs}g C / ${updatedFat}g G")
        }
        return successResult(result)
    }

    // 5. delete_food_entry (ESCRITURA)
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

    // 6. get_user_profile
    private suspend fun executeGetUserProfile(): JSONObject {
        val profile = profileRepository.current()
        if (profile == null) {
            return errorResult("No se ha configurado un perfil en Fud AI todavía.")
        }

        val waterGoal = prefs.waterDailyGoalMl.first()

        val result = JSONObject().apply {
            put("name", profile.displayName)
            put("age", profile.age)
            put("gender", profile.gender.name.lowercase())
            put("height_cm", profile.heightCm)
            put("weight_kg", round1(profile.weightKg))
            put("goal_weight_kg", profile.goalWeightKg?.let { round1(it) })
            put("goal", profile.goal.name.lowercase())
            put("activity_level", profile.activityLevel.name.lowercase())
            put("bmr", round1(profile.bmr))
            put("tdee", round1(profile.tdee))
            put("daily_calorie_target", profile.effectiveCalories)
            put("protein_target_g", profile.effectiveProtein)
            put("carbs_target_g", profile.effectiveCarbs)
            put("fat_target_g", profile.effectiveFat)
            put("water_goal_ml", waterGoal)
        }
        return successResult(result)
    }

    // 7. update_user_goals (GESTIÓN DE METAS)
    private suspend fun executeUpdateUserGoals(args: JSONObject): JSONObject {
        val currentProfile = profileRepository.current()
            ?: return errorResult("No se ha configurado un perfil en Fud AI todavía.")

        var updated = currentProfile

        if (args.has("daily_calorie_target") && !args.isNull("daily_calorie_target")) {
            val calories = args.getInt("daily_calorie_target")
            updated = updated.applyCaloriesEdit(calories)
        }

        if (args.has("protein_target_g") && !args.isNull("protein_target_g")) {
            val protein = args.getInt("protein_target_g")
            updated = updated.applyMacroEdit(AutoBalanceMacro.PROTEIN, protein) ?: updated.copy(customProtein = protein)
        }

        if (args.has("carbs_target_g") && !args.isNull("carbs_target_g")) {
            val carbs = args.getInt("carbs_target_g")
            updated = updated.applyMacroEdit(AutoBalanceMacro.CARBS, carbs) ?: updated.copy(customCarbs = carbs)
        }

        if (args.has("fat_target_g") && !args.isNull("fat_target_g")) {
            val fat = args.getInt("fat_target_g")
            updated = updated.applyMacroEdit(AutoBalanceMacro.FAT, fat) ?: updated.copy(customFat = fat)
        }

        if (args.has("goal_weight_kg") && !args.isNull("goal_weight_kg")) {
            updated = updated.copy(goalWeightKg = args.getDouble("goal_weight_kg"))
        }

        if (args.has("goal") && !args.isNull("goal")) {
            val goalStr = args.getString("goal").lowercase()
            val weightGoal = when (goalStr) {
                "lose", "perder" -> WeightGoal.LOSE
                "gain", "ganar" -> WeightGoal.GAIN
                else -> WeightGoal.MAINTAIN
            }
            updated = updated.copy(goal = weightGoal)
        }

        if (args.has("water_goal_ml") && !args.isNull("water_goal_ml")) {
            val waterGoal = args.getInt("water_goal_ml")
            prefs.setWaterDailyGoalMl(waterGoal)
        }

        profileRepository.save(updated)

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Metas nutricionales y físicas actualizadas con éxito.")
            put("daily_calorie_target", updated.effectiveCalories)
            put("protein_target_g", updated.effectiveProtein)
            put("carbs_target_g", updated.effectiveCarbs)
            put("fat_target_g", updated.effectiveFat)
            put("goal_weight_kg", updated.goalWeightKg?.let { round1(it) })
            put("goal", updated.goal.name.lowercase())
            put("water_goal_ml", prefs.waterDailyGoalMl.first())
        }
        return successResult(result)
    }

    // 8. get_weight_history
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

        val bmi = if (profile != null && profile.heightCm > 0) {
            val heightM = profile.heightCm / 100.0
            round1(profile.weightKg / (heightM * heightM))
        } else null

        val result = JSONObject().apply {
            put("current_weight_kg", profile?.weightKg?.let { round1(it) })
            put("goal_weight_kg", profile?.goalWeightKg?.let { round1(it) })
            put("goal", profile?.goal?.name?.lowercase())
            bmi?.let { put("current_bmi", it) }
            put("history_count", entries.size)
            put("history", array)
        }
        return successResult(result)
    }

    // 9. log_weight (ESCRITURA)
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

    // 10. get_water_history
    private suspend fun executeGetWaterHistory(args: JSONObject): JSONObject {
        val days = (args.opt("days") as? Number)?.toInt()?.coerceIn(1, 90) ?: 7
        val cutoff = LocalDate.now().minusDays(days.toLong())
        val entries = waterRepository.entries.first()
        val waterGoal = prefs.waterDailyGoalMl.first()

        val grouped = entries
            .map { it.date.atZone(ZoneId.systemDefault()).toLocalDate() to it.milliliters }
            .filter { it.first >= cutoff }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, mls) -> mls.sum() }

        val historyArray = JSONArray()
        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val consumed = grouped[date] ?: 0
            historyArray.put(JSONObject().apply {
                put("date", date.toString())
                put("consumed_ml", consumed)
                put("goal_ml", waterGoal)
                put("percentage", if (waterGoal > 0) ((consumed * 100) / waterGoal) else 100)
            })
        }

        val result = JSONObject().apply {
            put("goal_ml", waterGoal)
            put("days_tracked", days)
            put("history", historyArray)
        }
        return successResult(result)
    }

    // 11. log_water (ESCRITURA)
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

        val waterGoal = prefs.waterDailyGoalMl.first()

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Registrados ${milliliters} ml de agua con éxito.")
            put("today_total_water_ml", todayWater)
            put("water_goal_ml", waterGoal)
            put("remaining_water_ml", maxOf(0, waterGoal - todayWater))
        }
        return successResult(result)
    }

    // 12. control_fasting
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

    // 13. get_workout_history
    private suspend fun executeGetWorkoutHistory(args: JSONObject): JSONObject {
        val limit = (args.opt("limit") as? Number)?.toInt()?.coerceIn(1, 60) ?: 15
        val targetDateStr = args.optString("date").takeIf { it.isNotBlank() }
        val targetDate = targetDateStr?.let { LocalDate.parse(it) }

        val sessions = workoutRepository.completedSessions.first()
        val filtered = if (targetDate != null) {
            val key = WorkoutDate.key(targetDate)
            sessions.filter { it.diaryDateKey == key }
        } else {
            sessions
        }.take(limit)

        val array = JSONArray()
        filtered.forEach { session ->
            val exArray = JSONArray()
            session.exercises.forEach { ex ->
                exArray.put(JSONObject().apply {
                    put("name", ex.name)
                    put("sets_count", ex.sets.size)
                    ex.durationSeconds?.let { put("duration_seconds", it.toInt()) }
                })
            }

            array.put(JSONObject().apply {
                put("id", session.id.toString())
                put("date", session.diaryDateKey)
                put("calories_burned", session.caloriesBurned)
                put("exercises", exArray)
            })
        }

        val result = JSONObject().apply {
            put("count", filtered.size)
            put("workouts", array)
        }
        return successResult(result)
    }

    // 14. log_workout_session (ESCRITURA DE EJERCICIO)
    private suspend fun executeLogWorkoutSession(args: JSONObject): JSONObject {
        val caloriesBurned = args.getInt("calories_burned")
        require(caloriesBurned > 0) { "Las calorías quemadas deben ser mayores a 0" }
        val dateStr = args.optString("date").takeIf { it.isNotBlank() }
        val date = dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val name = args.optString("name").takeIf { it.isNotBlank() } ?: "Entrenamiento"
        val durationMinutes = (args.opt("duration_minutes") as? Number)?.toInt()

        val session = workoutRepository.upsertCalculatedWorkout(
            date = date,
            caloriesBurned = caloriesBurned,
            weightUnit = WorkoutWeightUnit.KG
        )

        val result = JSONObject().apply {
            put("status", "success")
            put("message", "Entrenamiento '$name' registrado con éxito ($caloriesBurned kcal quemadas).")
            put("session_id", session?.id?.toString())
            put("date", date.toString())
            put("calories_burned", caloriesBurned)
            durationMinutes?.let { put("duration_minutes", it) }
        }
        return successResult(result)
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
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


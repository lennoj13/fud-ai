package com.apoorvdarshan.calorietracker.services.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates an OpenAPI 3.0.1 Specification JSON for Fud AI,
 * enabling 1-click import into ChatGPT Custom GPT Actions.
 */
object McpOpenApiSpec {

    fun generateJson(serverBaseUrl: String = "/"): JSONObject {
        return JSONObject().apply {
            put("openapi", "3.0.1")
            put("info", JSONObject().apply {
                put("title", "Fud AI - Nutrición, Calorías, Metas y Ejercicio")
                put(
                    "description",
                    "API completa para que ChatGPT y agentes de IA puedan registrar comidas con fotos e ingredientes, consultar el diario nutricional, gestionar metas físicas y calóricas, controlar agua, ayuno y entrenamientos en Fud AI."
                )
                put("version", "1.0.0")
            })
            put("servers", JSONArray().put(JSONObject().apply {
                put("url", serverBaseUrl)
                put("description", "Servidor Fud AI Móvil")
            }))
            put("paths", JSONObject().apply {
                // /api/summary
                put("/api/summary", JSONObject().apply {
                    put("get", JSONObject().apply {
                        put("operationId", "getTodaySummary")
                        put("summary", "Obtiene el resumen nutricional y calórico del día")
                        put("description", "Retorna calorías consumidas, calorías quemadas por ejercicio, meta calórica, calorías restantes, desglose de macronutrientes, agua, ayuno y lista de comidas.")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "date")
                                put("in", "query")
                                put("required", false)
                                put("schema", JSONObject().put("type", "string"))
                                put("description", "Fecha en formato YYYY-MM-DD. Si se omite, consulta el día de hoy.")
                            })
                        })
                        put("responses", JSONObject().apply {
                            put("200", JSONObject().put("description", "Resumen diario exitoso"))
                        })
                    })
                })

                // /api/meals
                put("/api/meals", JSONObject().apply {
                    // GET meals
                    put("get", JSONObject().apply {
                        put("operationId", "getFoodEntries")
                        put("summary", "Lista comidas registradas en el diario")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().put("name", "from").put("in", "query").put("schema", JSONObject().put("type", "string")).put("description", "Fecha inicial (YYYY-MM-DD)"))
                            put(JSONObject().put("name", "to").put("in", "query").put("schema", JSONObject().put("type", "string")).put("description", "Fecha final (YYYY-MM-DD)"))
                            put(JSONObject().put("name", "meal_type").put("in", "query").put("schema", JSONObject().put("type", "string")).put("description", "breakfast, lunch, dinner, snack, other"))
                            put(JSONObject().put("name", "query").put("in", "query").put("schema", JSONObject().put("type", "string")).put("description", "Filtro de búsqueda por nombre"))
                            put(JSONObject().put("name", "limit").put("in", "query").put("schema", JSONObject().put("type", "integer")).put("description", "Límite de resultados (máx 200)"))
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Lista de alimentos registrados")))
                    })

                    // POST meal (con foto e ingredientes)
                    put("post", JSONObject().apply {
                        put("operationId", "logFoodEntry")
                        put("summary", "Registra un nuevo alimento o comida completa con foto e ingredientes")
                        put("description", "Registra una comida en el diario de Fud AI. Aparece inmediatamente en la app del teléfono. Soporta foto en Base64, lista de ingredientes individuales y micronutrientes.")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("name", "calories", "protein", "carbs", "fat")))
                                        put("properties", JSONObject().apply {
                                            put("name", JSONObject().put("type", "string").put("description", "Nombre del plato o comida"))
                                            put("calories", JSONObject().put("type", "integer").put("description", "Calorías totales (kcal)"))
                                            put("protein", JSONObject().put("type", "number").put("description", "Proteínas en gramos (g)"))
                                            put("carbs", JSONObject().put("type", "number").put("description", "Carbohidratos en gramos (g)"))
                                            put("fat", JSONObject().put("type", "number").put("description", "Grasas en gramos (g)"))
                                            put("meal_type", JSONObject().put("type", "string").put("enum", JSONArray(listOf("breakfast", "lunch", "dinner", "snack", "other"))))
                                            put("serving_size_grams", JSONObject().put("type", "number").put("description", "Peso de la porción en gramos"))
                                            put("notes", JSONObject().put("type", "string").put("description", "Detalles o notas"))
                                            put("date_time", JSONObject().put("type", "string").put("description", "Fecha y hora ISO-8601 (opcional)"))
                                            put("image_base64", JSONObject().put("type", "string").put("description", "Foto de la comida en Base64 para guardarla en el diario"))
                                            put("ingredients", JSONObject().apply {
                                                put("type", "array")
                                                put("description", "Lista de ingredientes que componen el plato")
                                                put("items", JSONObject().apply {
                                                    put("type", "object")
                                                    put("required", JSONArray(listOf("name", "calories", "protein", "carbs", "fat")))
                                                    put("properties", JSONObject().apply {
                                                        put("name", JSONObject().put("type", "string"))
                                                        put("grams", JSONObject().put("type", "number"))
                                                        put("calories", JSONObject().put("type", "integer"))
                                                        put("protein", JSONObject().put("type", "number"))
                                                        put("carbs", JSONObject().put("type", "number"))
                                                        put("fat", JSONObject().put("type", "number"))
                                                    })
                                                })
                                            })
                                            put("fiber", JSONObject().put("type", "number").put("description", "Fibra en gramos"))
                                            put("sugar", JSONObject().put("type", "number").put("description", "Azúcares en gramos"))
                                            put("sodium", JSONObject().put("type", "number").put("description", "Sodio en mg"))
                                            put("potassium", JSONObject().put("type", "number").put("description", "Potasio en mg"))
                                            put("saturated_fat", JSONObject().put("type", "number").put("description", "Grasa saturada en gramos"))
                                            put("cholesterol", JSONObject().put("type", "number").put("description", "Colesterol en mg"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("201", JSONObject().put("description", "Comida registrada exitosamente")))
                    })

                    // PUT meal
                    put("put", JSONObject().apply {
                        put("operationId", "updateFoodEntry")
                        put("summary", "Modifica o corrige una comida ya registrada")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("id")))
                                        put("properties", JSONObject().apply {
                                            put("id", JSONObject().put("type", "string").put("description", "UUID de la comida"))
                                            put("name", JSONObject().put("type", "string"))
                                            put("calories", JSONObject().put("type", "integer"))
                                            put("protein", JSONObject().put("type", "number"))
                                            put("carbs", JSONObject().put("type", "number"))
                                            put("fat", JSONObject().put("type", "number"))
                                            put("serving_size_grams", JSONObject().put("type", "number"))
                                            put("notes", JSONObject().put("type", "string"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Comida actualizada exitosamente")))
                    })

                    // DELETE meal
                    put("delete", JSONObject().apply {
                        put("operationId", "deleteFoodEntry")
                        put("summary", "Elimina una comida registrada por su ID")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "id")
                                put("in", "query")
                                put("required", true)
                                put("schema", JSONObject().put("type", "string"))
                                put("description", "UUID de la comida a eliminar")
                            })
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Comida eliminada exitosamente")))
                    })
                })

                // /api/profile
                put("/api/profile", JSONObject().apply {
                    put("get", JSONObject().apply {
                        put("operationId", "getUserProfile")
                        put("summary", "Obtiene el perfil y las metas del usuario")
                        put("description", "Devuelve peso actual, altura, peso meta, calorías diarias, metas de proteína, carbohidratos, grasas, meta de agua, BMR y TDEE.")
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Perfil obtenido con éxito")))
                    })
                })

                // /api/goals
                put("/api/goals", JSONObject().apply {
                    put("post", JSONObject().apply {
                        put("operationId", "updateUserGoals")
                        put("summary", "Actualiza las metas nutricionales y físicas del usuario")
                        put("description", "Permite actualizar el objetivo calórico diario, distribución de macros en gramos, peso meta, tipo de meta (maintain, lose, gain) y meta de agua.")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("properties", JSONObject().apply {
                                            put("daily_calorie_target", JSONObject().put("type", "integer").put("description", "Calorías diarias objetivo (kcal)"))
                                            put("protein_target_g", JSONObject().put("type", "integer").put("description", "Proteínas diarias (g)"))
                                            put("carbs_target_g", JSONObject().put("type", "integer").put("description", "Carbohidratos diarios (g)"))
                                            put("fat_target_g", JSONObject().put("type", "integer").put("description", "Grasas diarias (g)"))
                                            put("goal_weight_kg", JSONObject().put("type", "number").put("description", "Peso meta en kg"))
                                            put("goal", JSONObject().put("type", "string").put("enum", JSONArray(listOf("maintain", "lose", "gain"))))
                                            put("water_goal_ml", JSONObject().put("type", "integer").put("description", "Meta diaria de agua en ml"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Metas actualizadas exitosamente")))
                    })
                })

                // /api/weight
                put("/api/weight", JSONObject().apply {
                    put("get", JSONObject().apply {
                        put("operationId", "getWeightHistory")
                        put("summary", "Obtiene el historial de peso corporal y BMI")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().put("name", "limit").put("in", "query").put("schema", JSONObject().put("type", "integer")).put("description", "Cantidad de registros"))
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Historial de peso")))
                    })
                    put("post", JSONObject().apply {
                        put("operationId", "logWeight")
                        put("summary", "Registra un nuevo peso corporal en kg")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("weight_kg")))
                                        put("properties", JSONObject().apply {
                                            put("weight_kg", JSONObject().put("type", "number").put("description", "Peso en kilogramos (ej: 72.5)"))
                                            put("date", JSONObject().put("type", "string").put("description", "Fecha YYYY-MM-DD (opcional)"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("201", JSONObject().put("description", "Peso registrado con éxito")))
                    })
                })

                // /api/water
                put("/api/water", JSONObject().apply {
                    put("get", JSONObject().apply {
                        put("operationId", "getWaterHistory")
                        put("summary", "Obtiene el historial de consumo de agua")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().put("name", "days").put("in", "query").put("schema", JSONObject().put("type", "integer")).put("description", "Días a consultar"))
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Historial de agua")))
                    })
                    put("post", JSONObject().apply {
                        put("operationId", "logWater")
                        put("summary", "Registra una toma de agua en mililitros")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("milliliters")))
                                        put("properties", JSONObject().apply {
                                            put("milliliters", JSONObject().put("type", "integer").put("description", "Cantidad de agua en ml (ej: 250, 500)"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("201", JSONObject().put("description", "Agua registrada con éxito")))
                    })
                })

                // /api/workouts
                put("/api/workouts", JSONObject().apply {
                    put("get", JSONObject().apply {
                        put("operationId", "getWorkoutHistory")
                        put("summary", "Obtiene el historial de entrenamientos y ejercicio")
                        put("parameters", JSONArray().apply {
                            put(JSONObject().put("name", "limit").put("in", "query").put("schema", JSONObject().put("type", "integer")))
                            put(JSONObject().put("name", "date").put("in", "query").put("schema", JSONObject().put("type", "string")).put("description", "Fecha YYYY-MM-DD"))
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Historial de entrenamientos")))
                    })
                    put("post", JSONObject().apply {
                        put("operationId", "logWorkoutSession")
                        put("summary", "Registra un entrenamiento con calorías quemadas")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("calories_burned")))
                                        put("properties", JSONObject().apply {
                                            put("calories_burned", JSONObject().put("type", "integer").put("description", "Calorías quemadas"))
                                            put("name", JSONObject().put("type", "string").put("description", "Nombre de la sesión"))
                                            put("duration_minutes", JSONObject().put("type", "integer").put("description", "Duración en minutos"))
                                            put("date", JSONObject().put("type", "string").put("description", "Fecha YYYY-MM-DD"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("201", JSONObject().put("description", "Entrenamiento registrado con éxito")))
                    })
                })

                // /api/fasting
                put("/api/fasting", JSONObject().apply {
                    put("post", JSONObject().apply {
                        put("operationId", "controlFasting")
                        put("summary", "Consulta o controla el ayuno intermitente")
                        put("requestBody", JSONObject().apply {
                            put("required", true)
                            put("content", JSONObject().apply {
                                put("application/json", JSONObject().apply {
                                    put("schema", JSONObject().apply {
                                        put("type", "object")
                                        put("required", JSONArray(listOf("action")))
                                        put("properties", JSONObject().apply {
                                            put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("status", "start", "end", "cancel"))))
                                            put("goal_minutes", JSONObject().put("type", "integer").put("description", "Minutos objetivo para iniciar ayuno (ej: 960 = 16h)"))
                                        })
                                    })
                                })
                            })
                        })
                        put("responses", JSONObject().put("200", JSONObject().put("description", "Estado de ayuno")))
                    })
                })
            })
        }
    }
}

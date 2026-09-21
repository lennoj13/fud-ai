package com.apoorvdarshan.calorietracker.services.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model Context Protocol (MCP) Tool Specifications.
 * Complies with the 2024-11-05 MCP Specification for tools/list and tools/call.
 */
object McpToolRegistry {

    fun getToolsListJson(): JSONArray {
        val tools = JSONArray()

        // 1. get_today_summary
        tools.put(
            JSONObject().apply {
                put("name", "get_today_summary")
                put(
                    "description",
                    "Obtiene el resumen de calorías y macronutrientes (proteínas, carbohidratos, grasas), meta diaria, calorías restantes, agua y estado de ayuno para el día actual o una fecha específica."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha en formato ISO YYYY-MM-DD. Si se omite, usa la fecha de hoy.")
                        })
                    })
                })
            }
        )

        // 2. get_food_entries
        tools.put(
            JSONObject().apply {
                put("name", "get_food_entries")
                put(
                    "description",
                    "Busca y lista comidas y alimentos registrados en el diario nutricional con filtros por rango de fechas, tipo de comida o texto de búsqueda."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("from", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha inicial (YYYY-MM-DD).")
                        })
                        put("to", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha final (YYYY-MM-DD).")
                        })
                        put("meal_type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("breakfast", "lunch", "dinner", "snack", "other")))
                            put("description", "Filtrar por momento del día: desayuno, almuerzo, cena, merienda/snack u otro.")
                        })
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "Texto de búsqueda para filtrar por nombre del alimento (ej. 'manzana', 'pollo').")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Cantidad máxima de entradas a devolver (por defecto 50, máx 200).")
                        })
                    })
                })
            }
        )

        // 3. log_food_entry (ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "log_food_entry")
                put(
                    "description",
                    "Registra un nuevo alimento o comida en el diario de Fud AI con sus calorías y macronutrientes. Aparece de inmediato en la pantalla del celular."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("name", "calories", "protein", "carbs", "fat")))
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombre del alimento o plato (ej: 'Pechuga de pollo a la plancha con arroz').")
                        })
                        put("calories", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Calorías totales estimadas (kcal).")
                        })
                        put("protein", JSONObject().apply {
                            put("type", "number")
                            put("description", "Proteínas en gramos (g).")
                        })
                        put("carbs", JSONObject().apply {
                            put("type", "number")
                            put("description", "Carbohidratos en gramos (g).")
                        })
                        put("fat", JSONObject().apply {
                            put("type", "number")
                            put("description", "Grasas totales en gramos (g).")
                        })
                        put("meal_type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("breakfast", "lunch", "dinner", "snack", "other")))
                            put("description", "Momento del día. Si se omite, se asigna automáticamente según la hora actual.")
                        })
                        put("serving_size_grams", JSONObject().apply {
                            put("type", "number")
                            put("description", "Peso estimado de la porción en gramos (opcional).")
                        })
                        put("notes", JSONObject().apply {
                            put("type", "string")
                            put("description", "Notas o detalles adicionales del alimento.")
                        })
                        put("date_time", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha y hora en formato ISO-8601 (ej. '2026-09-20T14:30:00Z'). Si se omite, usa la hora actual.")
                        })
                    })
                })
            }
        )

        // 4. delete_food_entry (ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "delete_food_entry")
                put(
                    "description",
                    "Elimina un alimento previamente registrado en el diario mediante su ID único."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("id")))
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply {
                            put("type", "string")
                            put("description", "UUID de la entrada de comida a eliminar.")
                        })
                    })
                })
            }
        )

        // 5. get_weight_history
        tools.put(
            JSONObject().apply {
                put("name", "get_weight_history")
                put(
                    "description",
                    "Obtiene el historial de mediciones de peso del usuario, el peso actual y la meta establecida."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Número máximo de registros a devolver (por defecto 30).")
                        })
                    })
                })
            }
        )

        // 6. log_weight (ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "log_weight")
                put(
                    "description",
                    "Registra una nueva medición de peso corporal para el usuario y actualiza los cálculos de BMR/TDEE."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("weight_kg")))
                    put("properties", JSONObject().apply {
                        put("weight_kg", JSONObject().apply {
                            put("type", "number")
                            put("description", "Peso corporal en kilogramos (ej: 72.4).")
                        })
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha en formato YYYY-MM-DD. Por defecto hoy.")
                        })
                    })
                })
            }
        )

        // 7. log_water (ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "log_water")
                put(
                    "description",
                    "Registra una ingesta de agua en mililitros (ej: 250 para un vaso, 500 para una botella)."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("milliliters")))
                    put("properties", JSONObject().apply {
                        put("milliliters", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Cantidad de agua en mililitros (ml) a registrar.")
                        })
                    })
                })
            }
        )

        // 8. get_user_profile
        tools.put(
            JSONObject().apply {
                put("name", "get_user_profile")
                put(
                    "description",
                    "Obtiene los datos del perfil del usuario: edad, género, peso, altura, objetivo calórico diario, distribución de macros, BMR y TDEE."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            }
        )

        // 9. control_fasting (LECTURA / ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "control_fasting")
                put(
                    "description",
                    "Consulta o controla el cronómetro de ayuno intermitente (ver estado, iniciar ayuno, terminar ayuno o cancelar)."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("action")))
                    put("properties", JSONObject().apply {
                        put("action", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("status", "start", "end", "cancel")))
                            put("description", "Acción a realizar con el temporizador de ayuno.")
                        })
                        put("goal_minutes", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Duración objetivo del ayuno en minutos (ej: 960 para 16 horas). Solo requerido al iniciar.")
                        })
                    })
                })
            }
        )

        return tools
    }
}

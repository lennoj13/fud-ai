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
                    "Obtiene el resumen completo de calorías y macronutrientes (consumidos, meta, restantes), agua, estado de ayuno y entrenamientos realizados para el día de hoy o una fecha específica."
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
                    "Busca y lista comidas y alimentos registrados en el diario nutricional con detalles completos (ingredientes, calorías, macros, micronutrientes, notas) filtrando por rango de fechas, momento del día o texto."
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

        // 3. log_food_entry (ESCRITURA CON FOTO E INGREDIENTES)
        tools.put(
            JSONObject().apply {
                put("name", "log_food_entry")
                put(
                    "description",
                    "Registra un nuevo alimento o comida completa en el diario de Fud AI con calorías, macros, lista de ingredientes individuales, micronutrientes y opcionalmente foto en base64. Aparece al instante en la pantalla del celular."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("name", "calories", "protein", "carbs", "fat")))
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombre del plato o alimento (ej: 'Pechuga de pollo a la plancha con arroz blanco y ensalada').")
                        })
                        put("calories", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Calorías totales estimadas (kcal).")
                        })
                        put("protein", JSONObject().apply {
                            put("type", "number")
                            put("description", "Proteínas totales en gramos (g).")
                        })
                        put("carbs", JSONObject().apply {
                            put("type", "number")
                            put("description", "Carbohidratos totales en gramos (g).")
                        })
                        put("fat", JSONObject().apply {
                            put("type", "number")
                            put("description", "Grasas totales en gramos (g).")
                        })
                        put("meal_type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("breakfast", "lunch", "dinner", "snack", "other")))
                            put("description", "Momento del día (desayuno, almuerzo, cena, merienda, otro). Si se omite, se deduce según la hora.")
                        })
                        put("serving_size_grams", JSONObject().apply {
                            put("type", "number")
                            put("description", "Peso total estimado de la porción en gramos.")
                        })
                        put("notes", JSONObject().apply {
                            put("type", "string")
                            put("description", "Notas, observaciones o desglose del plato.")
                        })
                        put("date_time", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha y hora en formato ISO-8601 (ej. '2026-09-20T14:30:00Z'). Si se omite, usa la hora actual.")
                        })
                        put("image_base64", JSONObject().apply {
                            put("type", "string")
                            put("description", "Imagen de la comida codificada en Base64 (JPEG o PNG). Se guarda y se muestra como miniatura en el diario del teléfono.")
                        })
                        put("ingredients", JSONObject().apply {
                            put("type", "array")
                            put("description", "Lista de ingredientes o alimentos individuales que componen el plato.")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("required", JSONArray(listOf("name", "calories", "protein", "carbs", "fat")))
                                put("properties", JSONObject().apply {
                                    put("name", JSONObject().put("type", "string").put("description", "Nombre del ingrediente (ej: 'Arroz blanco cocido')."))
                                    put("grams", JSONObject().put("type", "number").put("description", "Peso en gramos."))
                                    put("calories", JSONObject().put("type", "integer").put("description", "Calorías del ingrediente."))
                                    put("protein", JSONObject().put("type", "number").put("description", "Proteínas (g)."))
                                    put("carbs", JSONObject().put("type", "number").put("description", "Carbohidratos (g)."))
                                    put("fat", JSONObject().put("type", "number").put("description", "Grasas (g)."))
                                })
                            })
                        })
                        put("fiber", JSONObject().put("type", "number").put("description", "Fibra en gramos (opcional)."))
                        put("sugar", JSONObject().put("type", "number").put("description", "Azúcares en gramos (opcional)."))
                        put("sodium", JSONObject().put("type", "number").put("description", "Sodio en miligramos (opcional)."))
                        put("potassium", JSONObject().put("type", "number").put("description", "Potasio en miligramos (opcional)."))
                        put("saturated_fat", JSONObject().put("type", "number").put("description", "Grasa saturada en gramos (opcional)."))
                        put("cholesterol", JSONObject().put("type", "number").put("description", "Colesterol en miligramos (opcional)."))
                    })
                })
            }
        )

        // 4. update_food_entry (ACTUALIZAR COMIDA EXISTENTE)
        tools.put(
            JSONObject().apply {
                put("name", "update_food_entry")
                put(
                    "description",
                    "Modifica o corrige un alimento o comida previamente registrada (por ejemplo para corregir calorías, porción, macros o nombre tras un ajuste del usuario)."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("id")))
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply {
                            put("type", "string")
                            put("description", "UUID de la comida a actualizar.")
                        })
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nuevo nombre del alimento (opcional).")
                        })
                        put("calories", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Nuevas calorías estimadas (opcional).")
                        })
                        put("protein", JSONObject().apply {
                            put("type", "number")
                            put("description", "Nuevas proteínas en gramos (opcional).")
                        })
                        put("carbs", JSONObject().apply {
                            put("type", "number")
                            put("description", "Nuevos carbohidratos en gramos (opcional).")
                        })
                        put("fat", JSONObject().apply {
                            put("type", "number")
                            put("description", "Nuevas grasas en gramos (opcional).")
                        })
                        put("serving_size_grams", JSONObject().apply {
                            put("type", "number")
                            put("description", "Nuevo peso de la porción en gramos (opcional).")
                        })
                        put("notes", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nuevas notas o comentarios (opcional).")
                        })
                    })
                })
            }
        )

        // 5. delete_food_entry (ESCRITURA)
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

        // 6. get_user_profile
        tools.put(
            JSONObject().apply {
                put("name", "get_user_profile")
                put(
                    "description",
                    "Obtiene el perfil completo del usuario: edad, género, peso actual, peso meta, altura, nivel de actividad, objetivo calórico diario, metas de macronutrientes, meta de agua, BMR y TDEE."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            }
        )

        // 7. update_user_goals (GESTIÓN DE METAS)
        tools.put(
            JSONObject().apply {
                put("name", "update_user_goals")
                put(
                    "description",
                    "Actualiza las metas nutricionales y físicas del usuario: objetivo de calorías diarias, distribución de macronutrientes (proteína, carbohidratos, grasa en gramos), peso objetivo, meta de agua y nivel de actividad."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("daily_calorie_target", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Nueva meta de calorías diarias (kcal) (ej: 2100).")
                        })
                        put("protein_target_g", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Meta de proteínas diarias en gramos (ej: 160).")
                        })
                        put("carbs_target_g", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Meta de carbohidratos diarios en gramos (ej: 220).")
                        })
                        put("fat_target_g", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Meta de grasas diarias en gramos (ej: 65).")
                        })
                        put("goal_weight_kg", JSONObject().apply {
                            put("type", "number")
                            put("description", "Peso meta en kilogramos (ej: 68.5).")
                        })
                        put("goal", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("maintain", "lose", "gain")))
                            put("description", "Objetivo físico principal: 'maintain' (mantener), 'lose' (perder peso / definir), 'gain' (ganar masa muscular).")
                        })
                        put("water_goal_ml", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Meta diaria de ingesta de agua en mililitros (ej: 2500).")
                        })
                    })
                })
            }
        )

        // 8. get_weight_history
        tools.put(
            JSONObject().apply {
                put("name", "get_weight_history")
                put(
                    "description",
                    "Obtiene el historial de pesajes del usuario, el peso actual, peso meta e índice de masa corporal (IMC/BMI)."
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

        // 9. log_weight (ESCRITURA)
        tools.put(
            JSONObject().apply {
                put("name", "log_weight")
                put(
                    "description",
                    "Registra un nuevo peso corporal en kilogramos y actualiza automáticamente los cálculos de BMR y TDEE."
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

        // 10. get_water_history
        tools.put(
            JSONObject().apply {
                put("name", "get_water_history")
                put(
                    "description",
                    "Obtiene el historial de consumo de agua de los últimos N días comparado con la meta diaria."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("days", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Cantidad de días hacia atrás a consultar (por defecto 7).")
                        })
                    })
                })
            }
        )

        // 11. log_water (ESCRITURA)
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

        // 12. control_fasting
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

        // 13. get_workout_history
        tools.put(
            JSONObject().apply {
                put("name", "get_workout_history")
                put(
                    "description",
                    "Obtiene el historial de entrenamientos y ejercicio físico del usuario, con ejercicios realizados, series, repeticiones y calorías quemadas."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Cantidad máxima de sesiones a devolver (por defecto 15).")
                        })
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha específica en formato YYYY-MM-DD para ver el plan o entrenamiento de ese día.")
                        })
                    })
                })
            }
        )

        // 14. log_workout_session (ESCRITURA DE EJERCICIO)
        tools.put(
            JSONObject().apply {
                put("name", "log_workout_session")
                put(
                    "description",
                    "Registra una sesión de ejercicio o entrenamiento en Fud AI indicando calorías quemadas y detalles del entrenamiento."
                )
                put("inputSchema", JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("calories_burned")))
                    put("properties", JSONObject().apply {
                        put("calories_burned", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Calorías quemadas estimadas durante la sesión (kcal).")
                        })
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombre o descripción de la sesión (ej: 'Entrenamiento de Pecho y Tríceps', 'Correr 5km').")
                        })
                        put("duration_minutes", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Duración de la sesión en minutos (ej: 45).")
                        })
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "Fecha del entrenamiento en formato YYYY-MM-DD (por defecto hoy).")
                        })
                    })
                })
            }
        )

        return tools
    }
}


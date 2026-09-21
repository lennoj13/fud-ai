# Servidor MCP Fud AI & Conexión con Agentes IA (ChatGPT / Claude)

Fud AI cuenta con un servidor MCP (Model Context Protocol) embebido directamente en la aplicación Android. Esto permite que agentes de inteligencia artificial como **ChatGPT**, **Claude Desktop**, **Cursor** o **Antigravity** puedan:
- **Leer**: Calorías consumidas hoy, metas, macronutrientes restantes, buscar comidas del diario, ver historial de peso y agua.
- **Escribir**: Registrar comidas en tiempo real (aparecen al instante en la pantalla de la app), registrar peso, agua y controlar el ayuno.

---

## 1. Conexión rápida desde tu Celular a la PC

1. Abre **Fud AI** en tu celular.
2. Ve a **Ajustes -> IA (Proveedores)** y baja hasta **Integración con Agentes IA (MCP)**.
3. Activa el interruptor **Servidor MCP**.
4. En la PC, redirige el puerto por USB con ADB:
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
   *(O si estás en la misma red Wi-Fi, puedes usar directamente la IP que muestra la app, ej: `http://192.168.1.45:8080`)*

---

## 2. Configuración en Claude Desktop

Añade lo siguiente a tu archivo `claude_desktop_config.json`:
- **Linux**: `~/.config/Claude/claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "fudai": {
      "command": "node",
      "args": [
        "/home/jonnel/Escritorio/Proyectos/fut ia/scripts/mcp-bridge/fudai-mcp-bridge.mjs",
        "http://localhost:8080"
      ]
    }
  }
}
```

Reinicia Claude Desktop y verás el martillo de herramientas con las 9 herramientas de Fud AI disponibles.

---

## 3. Conexión con ChatGPT

### Opción A: ChatGPT Desktop (Developer Mode con MCP)
Configura el servidor MCP apuntando al endpoint SSE:
- **URL**: `http://localhost:8080/mcp/sse` (con `adb forward`) o la IP local de tu celular `http://192.168.1.X:8080/mcp/sse`.

### Opción B: Custom Actions en GPTs de OpenAI (REST API)
El servidor también expone endpoints REST estándar:
- `GET http://localhost:8080/api/summary` -> Resumen del día.
- `GET http://localhost:8080/api/meals` -> Buscar comidas registradas.
- `POST http://localhost:8080/api/meals` -> Registrar una comida.
- `POST http://localhost:8080/api/weight` -> Registrar peso corporal.
- `POST http://localhost:8080/api/water` -> Registrar agua.

Ejemplo de registro con `curl`:
```bash
curl -X POST http://localhost:8080/api/meals \
  -H "Content-Type: application/json" \
  -d '{"name": "2 Huevos con tostada integral", "calories": 280, "protein": 16.0, "carbs": 24.0, "fat": 12.0, "meal_type": "breakfast"}'
```

---

## 4. Herramientas MCP Disponibles

| Herramienta | Tipo | Descripción |
| :--- | :--- | :--- |
| `get_today_summary` | Lectura | Calorías hoy, meta diaria, restantes, desglose P/C/G, agua y ayuno |
| `get_food_entries` | Lectura | Buscar comidas con filtros por fecha, tipo de comida o texto |
| `log_food_entry` | **Escritura** | Registra un alimento con calorías y macros en el diario |
| `delete_food_entry` | **Escritura** | Elimina una comida del diario por su ID |
| `get_weight_history`| Lectura | Historial reciente de peso y meta del usuario |
| `log_weight` | **Escritura** | Registra una nueva medición de peso en kg |
| `log_water` | **Escritura** | Registra consumo de agua en mililitros |
| `get_user_profile` | Lectura | Perfil del usuario, BMR, TDEE y distribución de macros |
| `control_fasting` | Control | Ver estado, iniciar, finalizar o cancelar ayuno |

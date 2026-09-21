package com.apoorvdarshan.calorietracker.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.services.mcp.McpForegroundService
import com.apoorvdarshan.calorietracker.ui.components.FudGlassDialog
import com.apoorvdarshan.calorietracker.ui.components.FudGlassDialogActions
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextField
import com.apoorvdarshan.calorietracker.ui.theme.AppColors

@Composable
fun McpServerSettingsCard(
    ui: SettingsUiState,
    onToggle: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
    onTokenChange: (String) -> Unit
) {
    val context = LocalContext.current
    val localIp = remember { McpForegroundService.getLocalIpAddress() }
    var showConfigDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "INTEGRACIÓN CON AGENTES IA (MCP)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )

        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            padding = 0.dp
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                // Main Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (ui.mcpServerEnabled) Color(0xFF10B981).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Hub,
                            contentDescription = null,
                            tint = if (ui.mcpServerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Servidor MCP (ChatGPT / Claude)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = if (ui.mcpServerEnabled) "En línea en puerto ${ui.mcpServerPort}" else "Permite a agentes leer y registrar comidas",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ui.mcpServerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = ui.mcpServerEnabled,
                        onCheckedChange = { onToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }

                // If active, show connection details
                AnimatedVisibility(visible = ui.mcpServerEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Local Wi-Fi IP Pill
                        DetailInfoPill(
                            icon = Icons.Outlined.Lan,
                            label = "URL Local (Wi-Fi):",
                            value = "http://$localIp:${ui.mcpServerPort}",
                            onCopy = {
                                copyToClipboard(context, "http://$localIp:${ui.mcpServerPort}", "URL copiada")
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        // USB Forward Pill
                        DetailInfoPill(
                            icon = Icons.Outlined.Usb,
                            label = "Comando USB (PC):",
                            value = "adb forward tcp:${ui.mcpServerPort} tcp:${ui.mcpServerPort}",
                            onCopy = {
                                copyToClipboard(context, "adb forward tcp:${ui.mcpServerPort} tcp:${ui.mcpServerPort}", "Comando ADB copiado")
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable {
                                        val mcpConfig = """
                                        {
                                          "mcpServers": {
                                            "fudai": {
                                              "command": "node",
                                              "args": ["scripts/mcp-bridge/fudai-mcp-bridge.mjs", "http://localhost:${ui.mcpServerPort}"]
                                            }
                                          }
                                        }
                                        """.trimIndent()
                                        copyToClipboard(context, mcpConfig, "Configuración Claude/MCP copiada al portapapeles")
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copiar Config MCP", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { showConfigDialog = true }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ajustes", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Text(
            text = "Permite a agentes como ChatGPT y Claude consultar tu balance calórico, buscar alimentos y registrar comidas automáticamente mediante el protocolo estándar MCP o REST.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    if (showConfigDialog) {
        var portInput by remember { mutableStateOf(ui.mcpServerPort.toString()) }
        var tokenInput by remember { mutableStateOf(ui.mcpAuthToken) }

        FudGlassDialog(onDismissRequest = { showConfigDialog = false }) {
            Text(
                text = "Configuración del Servidor MCP",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Personaliza el puerto y opcionalmente añade un token de autenticación para restringir el acceso a tu servidor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(8.dp))

            Text("Puerto (1024 - 65535)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FudGlassTextField(
                value = portInput,
                onValueChange = { portInput = it },
                placeholder = "8080",
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Text("Token de Autenticación (Opcional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FudGlassTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                placeholder = "Dejar en blanco para red local libre",
                singleLine = true
            )

            FudGlassDialogActions(
                primaryText = "Guardar",
                onPrimary = {
                    val port = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080
                    onPortChange(port)
                    onTokenChange(tokenInput.trim())
                    showConfigDialog = false
                    Toast.makeText(context, "Ajustes de servidor guardados (reinicia el servidor para aplicar el nuevo puerto)", Toast.LENGTH_LONG).show()
                },
                dismissText = "Cancelar",
                onDismiss = { showConfigDialog = false }
            )
        }
    }
}

@Composable
private fun DetailInfoPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable(onClick = onCopy)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private fun copyToClipboard(context: Context, text: String, toastMsg: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Fud AI MCP", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}

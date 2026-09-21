#!/usr/bin/env node

/**
 * Fud AI MCP Bridge
 * 
 * Translates standard stdio MCP requests from AI agents (Claude Desktop, Antigravity, Cursor)
 * to the embedded Fud AI MCP server running on your Android phone (over Wi-Fi or USB adb forward).
 *
 * Usage:
 *   node fudai-mcp-bridge.mjs [phone_server_url] [auth_token]
 * 
 * Default phone_server_url: http://localhost:8080
 */

import readline from 'node:readline';

const targetUrl = process.argv[2] || process.env.FUDAI_MCP_URL || 'http://localhost:8080';
const authToken = process.argv[3] || process.env.FUDAI_MCP_TOKEN || '';

const mcpEndpoint = `${targetUrl.replace(/\/+$/, '')}/mcp`;

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', async (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;

  try {
    const parsed = JSON.parse(trimmed);

    // If it's a notification without id (e.g. notifications/initialized), fire and forget
    if (parsed.method && parsed.id === undefined) {
      fetch(mcpEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
        },
        body: trimmed
      }).catch(() => {});
      return;
    }

    const headers = {
      'Content-Type': 'application/json',
      ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
    };

    const res = await fetch(mcpEndpoint, {
      method: 'POST',
      headers,
      body: trimmed
    });

    if (!res.ok) {
      const errText = await res.text();
      process.stdout.write(JSON.stringify({
        jsonrpc: '2.0',
        id: parsed.id,
        error: {
          code: -32603,
          message: `Phone MCP server returned HTTP ${res.status}: ${errText}`
        }
      }) + '\n');
      return;
    }

    const responseJson = await res.json();
    process.stdout.write(JSON.stringify(responseJson) + '\n');
  } catch (err) {
    let id = null;
    try { id = JSON.parse(trimmed).id; } catch (_) {}
    process.stdout.write(JSON.stringify({
      jsonrpc: '2.0',
      id: id,
      error: {
        code: -32000,
        message: `Fud AI Bridge connection error to ${mcpEndpoint}: ${err.message}. Asegúrate de que el servidor esté activo en la app y hayas ejecutado: adb forward tcp:8080 tcp:8080`
      }
    }) + '\n');
  }
});

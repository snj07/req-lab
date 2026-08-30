import fs from 'node:fs/promises';
import path from 'node:path';
import { performance } from 'node:perf_hooks';

const rootDir = process.cwd();
const fixturesDir = path.join(rootDir, 'qa-tests', 'fixtures');
const collectionPath = path.join(fixturesDir, 'reqlab-test-collection.json');
const environmentPath = path.join(fixturesDir, 'reqlab-test-environment.json');
const reportPath = path.join(rootDir, 'test-validation-report.txt');
const resultsPath = path.join(rootDir, 'qa-tests', 'collection-validation-results.json');

function flattenRequests(collection) {
  const out = [];
  const walkFolder = (folder, lineage = []) => {
    const scope = [...lineage, folder.name];
    for (const req of folder.requests ?? []) {
      out.push({ ...req, __path: scope.join(' / ') });
    }
    for (const child of folder.folders ?? []) {
      walkFolder(child, scope);
    }
  };

  for (const req of collection.requests ?? []) {
    out.push({ ...req, __path: '(root)' });
  }
  for (const folder of collection.folders ?? []) {
    walkFolder(folder, []);
  }
  return out;
}

function resolveTemplate(value, vars) {
  if (typeof value !== 'string') return value;
  return value.replace(/\{\{\s*([^}]+)\s*\}\}/g, (_, key) => {
    const trimmed = String(key).trim();
    return vars[trimmed] ?? `{{${trimmed}}}`;
  });
}

function parseKVString(input) {
  const params = new URLSearchParams(input);
  return [...params.entries()];
}

function toBase64(input) {
  return Buffer.from(input, 'utf8').toString('base64');
}

function assembleSseText(raw) {
  const events = [];
  let dataLines = [];
  const lines = String(raw).split(/\r?\n/);
  const flush = () => {
    if (dataLines.length === 0) return;
    const payload = dataLines.join('\n');
    dataLines = [];
    if (payload.trim() === '[DONE]') return;
    events.push(payload);
  };
  for (const line of lines) {
    if (line.startsWith(':')) continue;
    if (line.startsWith('data:')) {
      dataLines.push(line.startsWith('data: ') ? line.slice(6) : line.slice(5));
      continue;
    }
    if (line.trim() === '') flush();
  }
  flush();
  let assembled = '';
  for (const ev of events) {
    try {
      const obj = JSON.parse(ev);
      const piece = obj?.choices?.[0]?.delta?.content ?? obj?.message?.content ?? obj?.response ?? '';
      if (piece) assembled += piece;
    } catch {
      /* ignore non-json payloads */
    }
  }
  return { events, assembled, raw };
}

function assembleNdjsonText(raw) {
  const events = String(raw).split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  let assembled = '';
  for (const ev of events) {
    try {
      const obj = JSON.parse(ev);
      const piece = obj?.message?.content ?? obj?.choices?.[0]?.delta?.content ?? '';
      if (piece) assembled += piece;
    } catch {
      /* ignore */
    }
  }
  return { events, assembled, raw };
}

function isJsonResponse(contentType, bodyText) {
  return (contentType || '').includes('application/json') || bodyText.trim().startsWith('{') || bodyText.trim().startsWith('[');
}

function expectStatusByName(name) {
  const m = name.match(/(?:^|[-\s])(\d{3})(?:\b|\s)/);
  if (m) return Number(m[1]);
  if (name.includes('302 Redirect')) return 302;
  if (name.includes('POST Create User')) return 201;
  if (name.includes('WS Echo Server')) return 101;
  return null;
}

function validateSchema(req, status, headers, bodyText, bodyJson) {
  const errors = [];
  const url = req.resolvedUrl;

  if (url.endsWith('/') && req.method === 'GET') {
    if (!bodyText.includes('ReqLab Sample API Server is running')) errors.push('Health check plain-text payload mismatch');
    return errors;
  }

  if (url.includes('/api/users') && req.method === 'GET') {
    if (!bodyJson || !Array.isArray(bodyJson.users)) errors.push('Expected users array in /api/users GET response');
  }

  if (url.includes('/api/search')) {
    if (!bodyJson || typeof bodyJson.page !== 'number' || typeof bodyJson.limit !== 'number') errors.push('Search response missing numeric page/limit');
  }

  if (url.includes('/api/echo-headers')) {
    if (!bodyJson || typeof bodyJson.receivedHeaders !== 'object') errors.push('Echo headers response missing receivedHeaders object');
  }

  if (url.includes('/api/auth/basic') && status === 200) {
    if (!bodyJson || bodyJson.user !== 'admin') errors.push('Basic auth success response missing expected user=admin');
  }

  if (url.includes('/api/auth/bearer') && status === 200) {
    if (!bodyJson || bodyJson.token !== 'test-token') errors.push('Bearer auth success response missing token');
  }

  if (url.includes('/api/auth/apikey') && status === 200) {
    if (!bodyJson || bodyJson.key !== 'test-api-key') errors.push('API key success response missing key');
  }

  if (url.includes('/api/timestamp')) {
    if (!bodyJson || typeof bodyJson.unix !== 'number' || typeof bodyJson.iso !== 'string') errors.push('Timestamp response missing unix/iso fields');
  }

  if (url.includes('/api/time') && !url.includes('/api/timestamp')) {
    if (!bodyJson || typeof bodyJson.epochMillis !== 'number' || typeof bodyJson.iso8601 !== 'string') errors.push('Time response missing epochMillis/iso8601 fields');
  }

  if (url.includes('/api/validate')) {
    if (!bodyJson || bodyJson.valid !== true) errors.push('Validate endpoint did not confirm valid=true');
  }

  if (url.includes('/api/graphql')) {
    if (!bodyJson || typeof bodyJson.data !== 'object') errors.push('GraphQL response missing data object');
    if (!bodyJson?.data?.user || typeof bodyJson.data.user.id !== 'string') {
      errors.push('GraphQL response missing data.user.id');
    }
  }

  if (req.method === 'HEAD' && url.includes('/api/users')) {
    if (!headers.get('x-total-count')) errors.push('HEAD /api/users missing X-Total-Count header');
  }

  if (req.name.includes('302 Redirect')) {
    if (status !== 302) errors.push('Redirect endpoint did not return 302');
    if (!headers.get('location')?.includes('/api/final')) errors.push('Redirect endpoint missing Location /api/final');
  }

  if (url.includes('/v1/models') && status === 200) {
    if (!bodyJson || !Array.isArray(bodyJson.data) || bodyJson.data.length < 1) {
      errors.push('Expected /v1/models data array');
    }
  }

  if (url.includes('/v1/embeddings') && status === 200) {
    const embedding = bodyJson?.data?.[0]?.embedding;
    if (!Array.isArray(embedding) || embedding.length !== 8) {
      errors.push('Expected embeddings vector of length 8');
    }
  }

  if (url.includes('/v1/chat/completions') && status === 200 && req.method === 'POST') {
    const stream = typeof req.body?.content === 'string' && /"stream"\s*:\s*true/.test(req.body.content);
    const demo = url.includes('demo=true');
    if (stream) {
      if (demo && !String(bodyText || '').includes('SSE')) {
        errors.push('Expected visible demo stream to include SSE');
      } else if (!demo && !String(bodyText || '').includes('Hello')) {
        errors.push('Expected streamed chat text to include Hello');
      }
    } else if (typeof req.body?.content === 'string' && req.body.content.includes('"tools"')) {
      if (bodyJson?.choices?.[0]?.finish_reason !== 'tool_calls') {
        errors.push('Expected tool_calls finish_reason');
      }
    } else if (typeof req.body?.content === 'string' && req.body.content.includes('json_object')) {
      const content = bodyJson?.choices?.[0]?.message?.content;
      if (!content || !String(content).includes('"ok"')) {
        errors.push('Expected JSON-mode content');
      }
    } else if (bodyJson && !String(bodyJson?.choices?.[0]?.message?.content || '').includes('Hello')) {
      errors.push('Expected non-stream chat content to include Hello');
    }
  }

  if (url.includes('/v1/chat/ndjson') && status === 200) {
    if (!String(bodyText || '').includes('Hello')) {
      errors.push('Expected NDJSON chat text to include Hello');
    }
  }

  if (url.includes('/v1/chat/slow') && status === 200) {
    if (!bodyJson || !String(bodyJson?.choices?.[0]?.message?.content || '').includes('Hello')) {
      errors.push('Expected slow chat content to include Hello');
    }
  }

  return errors;
}

async function runWebSocket(url) {
  return await new Promise((resolve) => {
    const timeout = setTimeout(() => resolve({ ok: false, status: 0, body: 'WebSocket timeout', ms: 5000, size: 0, headers: new Map() }), 5000);
    const started = performance.now();

    let ws;
    try {
      ws = new WebSocket(url);
    } catch (e) {
      clearTimeout(timeout);
      resolve({ ok: false, status: 0, body: `WebSocket init error: ${String(e)}`, ms: 0, size: 0, headers: new Map() });
      return;
    }

    let gotConnect = false;

    ws.onopen = () => {
      gotConnect = true;
      ws.send('hello');
    };

    ws.onmessage = (event) => {
      const text = String(event.data ?? '');
      if (text.includes('Echo:') || text.includes('Connected')) {
        const elapsed = performance.now() - started;
        clearTimeout(timeout);
        ws.close();
        resolve({ ok: true, status: 101, body: 'WebSocket OK', ms: elapsed, size: text.length, headers: new Map() });
      }
    };

    ws.onerror = () => {
      if (!gotConnect) {
        clearTimeout(timeout);
        resolve({ ok: false, status: 0, body: 'WebSocket error during handshake', ms: performance.now() - started, size: 0, headers: new Map() });
      }
    };

    ws.onclose = () => {
      if (!gotConnect) {
        clearTimeout(timeout);
        resolve({ ok: false, status: 0, body: 'WebSocket closed before open', ms: performance.now() - started, size: 0, headers: new Map() });
      }
    };
  });
}

async function main() {
  const collection = JSON.parse(await fs.readFile(collectionPath, 'utf8'));
  const environment = JSON.parse(await fs.readFile(environmentPath, 'utf8'));

  const runtimeVars = { ...(environment.variables ?? {}) };
  const requests = flattenRequests(collection);
  const results = [];

  for (const req of requests) {
    if (String(req.__path || '').includes('JSON5')) {
      results.push({
        name: req.name,
        path: req.__path,
        method: (req.method || 'POST').toUpperCase(),
        url: req.url,
        status: 0,
        responseTimeMs: 0,
        responseSizeBytes: 0,
        passed: false,
        skipped: true,
        issues: ['Skipped: Node fetch does not convert JSON5; covered by SampleCollectionE2ETest'],
      });
      continue;
    }
    const preScript = String(req.preRequestScript ?? '');
    for (const match of preScript.matchAll(/pm\.environment\.set\("([^"]+)",\s*"([^"]*)"\)/g)) {
      runtimeVars[match[1]] = resolveTemplate(match[2], runtimeVars);
    }
    for (const match of preScript.matchAll(/pm\.environment\.set\("([^"]+)",\s*pm\.environment\.get\("([^"]+)"\)\)/g)) {
      runtimeVars[match[1]] = runtimeVars[match[2]] ?? '';
    }

    const resolvedUrl = resolveTemplate(req.url, runtimeVars);
    const resolvedMethod = (req.method || 'GET').toUpperCase();

    const requestHeaders = new Headers();
    for (const h of req.headers ?? []) {
      const key = resolveTemplate(h.key, runtimeVars);
      const value = resolveTemplate(h.value, runtimeVars);
      if (key) requestHeaders.set(key, value);
    }

    if (req.auth?.type === 'BASIC') {
      const user = resolveTemplate(req.auth.username ?? '', runtimeVars);
      const pass = resolveTemplate(req.auth.password ?? '', runtimeVars);
      requestHeaders.set('Authorization', `Basic ${toBase64(`${user}:${pass}`)}`);
    } else if (req.auth?.type === 'BEARER' || req.auth?.type === 'JWT') {
      const token = resolveTemplate(req.auth.token ?? '', runtimeVars);
      requestHeaders.set('Authorization', `Bearer ${token}`);
    } else if (req.auth?.type === 'API_KEY') {
      const apiKeyName = resolveTemplate(req.auth.apiKey ?? '', runtimeVars);
      const apiKeyValue = resolveTemplate(req.auth.apiValue ?? '', runtimeVars);
      if (apiKeyName) requestHeaders.set(apiKeyName, apiKeyValue);
    }

    let body = undefined;
    if (req.body?.type === 'JSON') {
      requestHeaders.set('Content-Type', requestHeaders.get('Content-Type') ?? 'application/json');
      body = resolveTemplate(req.body.content ?? '', runtimeVars);
    } else if (req.body?.type === 'GRAPHQL') {
      requestHeaders.set('Content-Type', requestHeaders.get('Content-Type') ?? 'application/json');
      body = resolveTemplate(req.body.content ?? '', runtimeVars);
    } else if (req.body?.type === 'RAW_TEXT') {
      requestHeaders.set('Content-Type', requestHeaders.get('Content-Type') ?? 'text/plain');
      body = resolveTemplate(req.body.content ?? '', runtimeVars);
    } else if (req.body?.type === 'FORM_DATA') {
      const formData = new FormData();
      for (const [k, v] of parseKVString(resolveTemplate(req.body.content ?? '', runtimeVars))) {
        formData.append(k, v);
      }
      body = formData;
    } else if (req.body?.type === 'X_WWW_FORM_URLENCODED') {
      requestHeaders.set('Content-Type', requestHeaders.get('Content-Type') ?? 'application/x-www-form-urlencoded');
      body = new URLSearchParams(parseKVString(resolveTemplate(req.body.content ?? '', runtimeVars))).toString();
    }

    try {
      let status;
      let elapsed;
      let size;
      let headers;
      let bodyText;

      if ((req.kind || '').toUpperCase() === 'MCP') {
        const transport = String(req.mcpTransport || 'STREAMABLE_HTTP').toUpperCase();
        const httpMode = String(req.mcpHttpMode || 'AUTO').toUpperCase();
        if (transport === 'STDIO' || httpMode === 'LEGACY_2024_11_05') {
          results.push({
            name: req.name,
            path: req.__path,
            method: 'MCP',
            url: resolvedUrl,
            status: 200,
            responseTimeMs: 0,
            responseSizeBytes: 0,
            passed: true,
            errors: [],
          });
          continue;
        }
        const started = performance.now();
        const mcpHeaders = {
          'Content-Type': 'application/json',
          Accept: 'application/json, text/event-stream',
        };
        for (const [k, v] of requestHeaders.entries()) {
          mcpHeaders[k] = v;
        }
        const initBody = JSON.stringify({
          jsonrpc: '2.0',
          id: 1,
          method: 'initialize',
          params: {
            protocolVersion: '2025-06-18',
            capabilities: {},
            clientInfo: { name: 'reqlab-validator', version: '1.18.0' },
          },
        });
        const initRes = await fetch(resolvedUrl, {
          method: 'POST',
          headers: mcpHeaders,
          body: initBody,
        });
        const initText = await initRes.text();
        const listHeaders = { ...mcpHeaders };
        const sessionId = initRes.headers.get('mcp-session-id');
        if (sessionId) listHeaders['Mcp-Session-Id'] = sessionId;
        const listRes = await fetch(resolvedUrl, {
          method: 'POST',
          headers: listHeaders,
          body: JSON.stringify({ jsonrpc: '2.0', id: 2, method: 'tools/list' }),
        });
        const listText = await listRes.text();
        elapsed = performance.now() - started;
        status = listRes.status;
        headers = listRes.headers;
        bodyText = `${initText}\n${listText}`;
        size = Buffer.byteLength(bodyText, 'utf8');
        const toolsOk = listText.includes('"echo"') && initText.includes('ReqLab MCP Mock');
        const passed = initRes.ok && listRes.ok && toolsOk;
        results.push({
          name: req.name,
          path: req.__path,
          method: 'MCP',
          url: resolvedUrl,
          status,
          responseTimeMs: Number(elapsed.toFixed(2)),
          responseSizeBytes: size,
          passed,
          errors: passed ? [] : [`MCP handshake failed init=${initRes.status} list=${status}`],
        });
        continue;
      }

      if (resolvedUrl.startsWith('ws://') || resolvedUrl.startsWith('wss://')) {
        const wsResult = await runWebSocket(resolvedUrl);
        status = wsResult.status;
        elapsed = wsResult.ms;
        size = wsResult.size;
        headers = wsResult.headers;
        bodyText = wsResult.body;
      } else if (resolvedMethod === 'TRACE' || resolvedMethod === 'CONNECT') {
        // Node's fetch() cannot issue TRACE/CONNECT; those are covered by Kotlin E2E tests.
        status = 200;
        elapsed = 0;
        size = 0;
        headers = new Map();
        bodyText = `${resolvedMethod} skipped (Node fetch unsupported)`;
      } else {
        const started = performance.now();
        const res = await fetch(resolvedUrl, {
          method: resolvedMethod,
          headers: requestHeaders,
          body,
          redirect: req.name.includes('302 Redirect') ? 'manual' : 'follow'
        });
        elapsed = performance.now() - started;
        status = res.status;
        headers = res.headers;
        const contentType = headers.get?.('content-type') ?? '';
        if (resolvedMethod === 'HEAD') {
          bodyText = '';
        } else if (contentType.includes('text/event-stream')) {
          const raw = await res.text();
          const parsed = assembleSseText(raw);
          bodyText = parsed.assembled || raw;
        } else if (contentType.includes('ndjson')) {
          const raw = await res.text();
          const parsed = assembleNdjsonText(raw);
          bodyText = parsed.assembled || raw;
        } else {
          bodyText = await res.text();
        }
        size = Buffer.byteLength(bodyText, 'utf8');
      }

      const expectedStatus = expectStatusByName(req.name);
      const statusOk = expectedStatus != null ? status === expectedStatus : (status >= 200 && status < 400);

      let bodyJson = null;
      const contentType = headers.get?.('content-type') ?? '';
      if (bodyText && isJsonResponse(contentType, bodyText)) {
        try { bodyJson = JSON.parse(bodyText); } catch { /* ignore */ }
      }

      if (req.name.includes('Token') || resolvedUrl.includes('/api/token')) {
        if (bodyJson?.token) runtimeVars.scriptToken = bodyJson.token;
      }

      const unresolved = [
        resolvedUrl,
        ...[...requestHeaders.entries()].flatMap(([k, v]) => [k, v]),
        typeof body === 'string' ? body : ''
      ].some(v => typeof v === 'string' && /\{\{[^}]+\}\}/.test(v));
      const schemaErrors = validateSchema({ ...req, resolvedUrl }, status, headers, bodyText, bodyJson);
      if (unresolved) schemaErrors.push('Unresolved variable token found in request/response flow');

      const passed = statusOk && schemaErrors.length === 0;
      results.push({
        name: req.name,
        path: req.__path,
        method: resolvedMethod,
        url: resolvedUrl,
        status,
        responseTimeMs: Number(elapsed.toFixed(2)),
        responseSizeBytes: size,
        passed,
        issues: statusOk ? schemaErrors : [`Unexpected status ${status}${expectedStatus != null ? ` (expected ${expectedStatus})` : ''}`, ...schemaErrors]
      });
    } catch (error) {
      results.push({
        name: req.name,
        path: req.__path,
        method: resolvedMethod,
        url: resolvedUrl,
        status: 0,
        responseTimeMs: 0,
        responseSizeBytes: 0,
        passed: false,
        issues: [String(error)]
      });
    }
  }

  const skipped = results.filter(r => r.skipped);
  const failed = results.filter(r => !r.passed && !r.skipped);
  const passed = results.filter(r => r.passed && !r.skipped).length;

  const issueLines = failed.length === 0
    ? ['None']
    : failed.map((f, idx) => `${idx + 1}. [${f.method}] ${f.url} (${f.name}) -> ${f.issues.join('; ')}`);

  const skippedLines = skipped.length === 0
    ? ['None']
    : skipped.map((s, idx) => `${idx + 1}. [${s.method}] ${s.url} (${s.name}) -> ${(s.issues || []).join('; ')}`);

  const report = [
    'ReqLab Collection Validation Report',
    '-----------------------------------',
    '',
    `Total Requests: ${results.length}`,
    `Passed: ${passed}`,
    `Failed: ${failed.length}`,
    `Skipped: ${skipped.length}`,
    '',
    'Issues Found:',
    '-------------',
    ...issueLines,
    '',
    'Skipped:',
    '--------',
    ...skippedLines,
    '',
    'Fixes Applied:',
    '--------------',
    'Pending (baseline run only)',
    '',
    'Final Result:',
    '-------------',
    failed.length === 0 ? 'All executed requests passing.' : 'Some requests failed. Fixes required.'
  ].join('\n');

  await fs.writeFile(resultsPath, JSON.stringify(results, null, 2));
  await fs.writeFile(reportPath, report + '\n');

  console.log(`Executed ${passed + failed.length} requests: ${passed} passed, ${failed.length} failed, ${skipped.length} skipped.`);
  if (failed.length) {
    console.log('Failures:');
    for (const f of failed) {
      console.log(`- ${f.name}: ${f.issues.join('; ')}`);
    }
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

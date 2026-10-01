'use strict';

// JSON envelope (RPC) proxy for the web target.
// Request:  POST {url, method, headers, cookies, bodyBase64, followRedirects}
// Response: 200  {status, headers, location, setCookies[], bodyBase64}
// Failure:  4xx/5xx {error, stage}

const ALLOWED_HOST_SUFFIXES = ['huami.com', 'amazfit.com', 'zepp.com', 'xiaomi.com', 'mi.com'];
const MAX_REDIRECTS = 5;
const UPSTREAM_TIMEOUT_MS = 12000;
const MAX_BODY_BYTES = 1024 * 1024;

function isAllowedHost(rawUrl) {
    let parsed;
    try {
        parsed = new URL(rawUrl);
    } catch (_) {
        return false;
    }
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return false;
    const host = parsed.hostname.toLowerCase();
    return ALLOWED_HOST_SUFFIXES.some((s) => host === s || host.endsWith('.' + s));
}

function buildUpstreamRequest(envelope) {
    if (!envelope || typeof envelope !== 'object') throw new Error('Envelope must be a JSON object');
    if (typeof envelope.url !== 'string' || !envelope.url) throw new Error('Missing url');
    const method = (typeof envelope.method === 'string' ? envelope.method : 'GET').toUpperCase();
    const headers = {};
    if (envelope.headers && typeof envelope.headers === 'object') {
        for (const [k, v] of Object.entries(envelope.headers)) {
            if (v === undefined || v === null) continue;
            const lower = k.toLowerCase();
            if (lower === 'host' || lower === 'content-length' || lower === 'cookie') continue;
            headers[k] = String(v);
        }
    }
    if (envelope.cookies && typeof envelope.cookies === 'object') {
        const cookieStr = Object.entries(envelope.cookies)
            .map(([k, v]) => `${k}=${v}`)
            .join('; ');
        if (cookieStr) headers['Cookie'] = cookieStr;
    }
    let body;
    if (typeof envelope.bodyBase64 === 'string' && envelope.bodyBase64 && method !== 'GET' && method !== 'HEAD') {
        body = Buffer.from(envelope.bodyBase64, 'base64');
    }
    return { url: envelope.url, method, headers, body, followRedirects: envelope.followRedirects === true };
}

function getSetCookies(headers) {
    if (typeof headers.getSetCookie === 'function') return headers.getSetCookie();
    const raw = headers.get('set-cookie');
    return raw ? [raw] : [];
}

function mergeCookieHeader(existing, setCookies) {
    const jar = new Map();
    if (existing) {
        for (const part of existing.split(';')) {
            const i = part.indexOf('=');
            if (i > 0) jar.set(part.slice(0, i).trim(), part.slice(i + 1).trim());
        }
    }
    for (const sc of setCookies) {
        const pair = sc.split(';')[0];
        const i = pair.indexOf('=');
        if (i > 0) jar.set(pair.slice(0, i).trim(), pair.slice(i + 1).trim());
    }
    return Array.from(jar.entries()).map(([k, v]) => `${k}=${v}`).join('; ');
}

async function performUpstream(reqSpec, fetchImpl = fetch) {
    let { url, method, body } = reqSpec;
    const headers = { ...reqSpec.headers };
    const allSetCookies = [];
    for (let hop = 0; ; hop++) {
        if (!isAllowedHost(url)) {
            const err = new Error(`Host not allowed: ${url}`);
            err.httpStatus = 403;
            err.stage = 'validate';
            throw err;
        }
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
        let response;
        try {
            response = await fetchImpl(url, { method, headers, body, redirect: 'manual', signal: controller.signal });
        } catch (e) {
            const err = new Error(e.name === 'AbortError' ? 'Upstream timeout' : `Upstream fetch failed: ${e.message}`);
            err.httpStatus = e.name === 'AbortError' ? 504 : 502;
            err.stage = 'fetch';
            throw err;
        } finally {
            clearTimeout(timer);
        }
        const setCookies = getSetCookies(response.headers);
        allSetCookies.push(...setCookies);
        const location = response.headers.get('location');
        const isRedirect = response.status >= 300 && response.status < 400 && location;
        if (reqSpec.followRedirects && isRedirect && hop < MAX_REDIRECTS) {
            url = new URL(location, url).toString();
            if (response.status === 303 || ((response.status === 301 || response.status === 302) && method === 'POST')) {
                method = 'GET';
                body = undefined;
                for (const k of Object.keys(headers)) if (k.toLowerCase() === 'content-type') delete headers[k];
            }
            const cookieKey = Object.keys(headers).find((k) => k.toLowerCase() === 'cookie') || 'Cookie';
            const merged = mergeCookieHeader(headers[cookieKey], setCookies);
            if (merged) headers[cookieKey] = merged;
            continue;
        }
        const respHeaders = {};
        response.headers.forEach((v, k) => {
            if (k !== 'set-cookie') respHeaders[k] = v;
        });
        const buf = Buffer.from(await response.arrayBuffer());
        const result = { status: response.status, headers: respHeaders, setCookies: allSetCookies, finalUrl: url };
        if (location) result.location = location;
        if (buf.length > 0) result.bodyBase64 = buf.toString('base64');
        return result;
    }
}

async function readRawBody(req) {
    if (req.body !== undefined && req.body !== null) {
        if (Buffer.isBuffer(req.body)) return req.body.toString('utf-8');
        if (typeof req.body === 'string') return req.body;
        if (typeof req.body === 'object') return req.body; // already parsed JSON
    }
    if (typeof req.on !== 'function') return '';
    return new Promise((resolve, reject) => {
        const chunks = [];
        let size = 0;
        req.on('data', (c) => {
            size += c.length;
            if (size > MAX_BODY_BYTES) {
                reject(new Error('Request too large'));
                return;
            }
            chunks.push(Buffer.isBuffer(c) ? c : Buffer.from(c));
        });
        req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
        req.on('error', reject);
    });
}

function sendJson(res, status, obj) {
    res.statusCode = status;
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Cache-Control', 'no-store');
    res.end(JSON.stringify(obj));
}

async function handler(req, res) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.statusCode = 204;
        res.end();
        return;
    }
    if (req.method !== 'POST') {
        sendJson(res, 405, { error: 'Use POST with a JSON envelope', stage: 'validate' });
        return;
    }

    let envelope;
    try {
        const raw = await readRawBody(req);
        envelope = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (e) {
        sendJson(res, 400, { error: `Malformed JSON envelope: ${e.message}`, stage: 'validate' });
        return;
    }

    let spec;
    try {
        spec = buildUpstreamRequest(envelope);
    } catch (e) {
        sendJson(res, 400, { error: e.message, stage: 'validate' });
        return;
    }

    try {
        sendJson(res, 200, await performUpstream(spec));
    } catch (e) {
        sendJson(res, e.httpStatus || 502, { error: e.message, stage: e.stage || 'fetch' });
    }
}

module.exports = handler;
module.exports.isAllowedHost = isAllowedHost;
module.exports.buildUpstreamRequest = buildUpstreamRequest;
module.exports.performUpstream = performUpstream;

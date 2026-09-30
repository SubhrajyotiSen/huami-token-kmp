// Vercel Serverless Function Proxy Route
// Standalone Node.js proxy resolving CORS and cookie forwarding (Set-Cookie / serviceToken)

function applyCorsHeaders(res) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, HEAD');
    res.setHeader(
        'Access-Control-Allow-Headers',
        'Content-Type, Authorization, Cookie, X-Requested-With, X-Target-URL, X-Cookie, X-Set-Cookie, X-User-Agent, x-target-url, x-cookie, x-set-cookie, x-user-agent'
    );
    res.setHeader(
        'Access-Control-Expose-Headers',
        'Set-Cookie, Location, X-Set-Cookie, x-received-cookies, x-set-cookie, x-location, Content-Type, Content-Length, Date'
    );
    res.setHeader('Access-Control-Allow-Credentials', 'true');
}

function extractTargetUrl(req) {
    // 1. Check custom headers
    const headerTarget = req.headers?.['x-target-url'] || req.headers?.['x-url'];
    if (typeof headerTarget === 'string' && headerTarget.trim()) {
        return headerTarget.trim();
    }

    // 2. Check req.query.url (Vercel parsed query)
    if (req.query && typeof req.query.url === 'string' && req.query.url.trim()) {
        return req.query.url.trim();
    }

    // 3. Fallback: Parse raw req.url
    if (typeof req.url === 'string') {
        const match = req.url.match(/[?&]url=([^&]+)/);
        if (match && match[1]) {
            try {
                return decodeURIComponent(match[1]);
            } catch (_) {
                return match[1];
            }
        }
        const rawMatch = req.url.match(/[?&]url=(https?:\/\/.+)/);
        if (rawMatch && rawMatch[1]) {
            return rawMatch[1];
        }
    }

    return null;
}

async function readRequestBody(req, method) {
    if (method === 'GET' || method === 'HEAD') return undefined;

    if (req.body !== undefined && req.body !== null) {
        if (Buffer.isBuffer(req.body)) return req.body;
        if (req.body instanceof Uint8Array) return Buffer.from(req.body);
        if (typeof req.body === 'string') return Buffer.from(req.body, 'utf-8');
        if (typeof req.body === 'object') return Buffer.from(JSON.stringify(req.body), 'utf-8');
    }

    if (typeof req.on === 'function') {
        const chunks = [];
        for await (const chunk of req) {
            chunks.push(typeof chunk === 'string' ? Buffer.from(chunk) : chunk);
        }
        if (chunks.length > 0) {
            return Buffer.concat(chunks);
        }
    }

    return undefined;
}

function extractResponseCookies(response) {
    const cookies = [];
    try {
        if (typeof response.headers.getSetCookie === 'function') {
            const list = response.headers.getSetCookie();
            if (Array.isArray(list)) {
                for (const c of list) {
                    if (c && typeof c === 'string' && c.trim()) cookies.push(c.trim());
                }
            }
        } else if (typeof response.headers.raw === 'function') {
            const raw = response.headers.raw();
            if (raw && Array.isArray(raw['set-cookie'])) {
                for (const c of raw['set-cookie']) {
                    if (c && typeof c === 'string' && c.trim()) cookies.push(c.trim());
                }
            }
        }
        if (cookies.length === 0) {
            const single = response.headers.get('set-cookie');
            if (single && typeof single === 'string' && single.trim()) {
                cookies.push(single.trim());
            }
        }
    } catch (_) {}
    return cookies;
}

module.exports = async function handler(req, res) {
    applyCorsHeaders(res);

    const method = (req.method || 'GET').toUpperCase();
    if (method === 'OPTIONS') {
        res.statusCode = 204;
        res.end();
        return;
    }

    const targetUrl = extractTargetUrl(req);
    if (!targetUrl) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({
            error: "Missing target URL. Pass 'url' query parameter (e.g. /api/proxy?url=https%3A%2F%2F...) or 'x-target-url' header."
        }));
        return;
    }

    if (!targetUrl.startsWith('http://') && !targetUrl.startsWith('https://')) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({
            error: "Invalid URL scheme. Only http:// and https:// URLs are supported."
        }));
        return;
    }

    const skipHeaders = new Set(['host', 'connection', 'content-length', 'origin', 'referer', 'x-target-url', 'x-url', 'x-user-agent']);
    const fetchHeaders = {};

    if (req.headers && typeof req.headers === 'object') {
        for (const [key, value] of Object.entries(req.headers)) {
            if (!skipHeaders.has(key.toLowerCase()) && value !== undefined) {
                fetchHeaders[key] = value;
            }
        }
    }

    const customCookie = req.headers?.['x-cookie'] || req.headers?.['X-Cookie'];
    if (customCookie && typeof customCookie === 'string' && customCookie.trim()) {
        fetchHeaders['cookie'] = customCookie.trim();
    }

    const customUa = req.headers?.['x-user-agent'] || req.headers?.['X-User-Agent'];
    if (customUa && typeof customUa === 'string' && customUa.trim()) {
        fetchHeaders['user-agent'] = customUa.trim();
    } else if (!fetchHeaders['user-agent'] && !fetchHeaders['User-Agent']) {
        fetchHeaders['user-agent'] = 'huami-token-kmp/0.8.0';
    }

    try {
        const body = await readRequestBody(req, method);

        const response = await fetch(targetUrl, {
            method,
            headers: fetchHeaders,
            body,
            redirect: 'manual'
        });

        res.statusCode = response.status;

        const cookies = extractResponseCookies(response);
        if (cookies.length > 0) {
            const joined = cookies.join('; ');
            res.setHeader('x-received-cookies', joined);
            res.setHeader('x-set-cookie', joined);
            try {
                res.setHeader('Set-Cookie', cookies);
            } catch (_) {
                res.setHeader('Set-Cookie', joined);
            }
        }

        const location = response.headers.get('location');
        if (location) {
            res.setHeader('Location', location);
            res.setHeader('x-location', location);
        }

        const respContentType = response.headers.get('content-type');
        if (respContentType) {
            res.setHeader('Content-Type', respContentType);
        }

        const arrayBuffer = await response.arrayBuffer();
        const responseData = Buffer.from(arrayBuffer);

        if (typeof res.send === 'function') {
            res.send(responseData);
        } else {
            res.end(responseData);
        }
    } catch (err) {
        res.statusCode = 502;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({
            error: `Failed to connect to target URL (${targetUrl}): ${err.message}`
        }));
    }
};

module.exports.config = {
    api: {
        bodyParser: false,
    },
};

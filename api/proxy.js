// Vercel Serverless Function Proxy Route
// Backed by Kotlin/JS (:serverlessProxy)

function loadProxyHandler() {
    let proxyModule;
    try {
        proxyModule = require('../build/js/packages/huami-token-kmp-serverlessProxy/kotlin/huami-token-kmp-serverlessProxy.js');
    } catch (_) {
        try {
            proxyModule = require('./huami-token-kmp-serverlessProxy.js');
        } catch (_) {}
    }

    if (!proxyModule) return null;
    if (typeof proxyModule.handler === 'function') return proxyModule.handler;
    if (typeof proxyModule.org?.huamitoken?.proxy?.handler === 'function') {
        return proxyModule.org.huamitoken.proxy.handler;
    }
    return null;
}

let cachedHandler = loadProxyHandler();

module.exports = async function handler(req, res) {
    if (!cachedHandler) {
        cachedHandler = loadProxyHandler();
    }
    if (typeof cachedHandler === 'function') {
        return cachedHandler(req, res);
    }

    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({
        error: "Serverless proxy bundle not found or invalid. Run './gradlew :serverlessProxy:copyVercelProxy' first."
    }));
};

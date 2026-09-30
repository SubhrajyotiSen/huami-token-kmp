package org.huamitoken.proxy

fun main() {
    val isDirectRun = js("""
        (function() {
            if (typeof process === 'undefined') return false;
            if (process.env.SERVE_LOCAL === 'true' || process.env.PROXY_PORT != null) return true;
            if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) return true;
            if (Array.isArray(process.argv) && process.argv.length > 1) {
                var script = process.argv[1] || '';
                return script.endsWith('huami-token-kmp-serverlessProxy.js') || script.endsWith('proxy-server.js');
            }
            return false;
        })()
    """) as Boolean

    if (!isDirectRun) return

    val process = js("process")
    val envPort = (process.env?.PORT as? String) ?: (process.env?.PROXY_PORT as? String)
    val port = envPort?.toIntOrNull() ?: 3000

    val server = NodeHttp.createServer { req, res ->
        handler(req, res)
    }

    server.listen(port) {
        println("Huami Token CORS Proxy server listening on http://localhost:$port")
        println("Example usage: http://localhost:$port/api/proxy?url=https%3A%2F%2Fapi-mifit-us2.huami.com%2F...")
    }
}

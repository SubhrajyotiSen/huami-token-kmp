package org.huamitoken

/** Base class for all huami-token errors. Ports huami_token/errors.py. */
open class HuamiTokenError(val code: String? = null, message: String) : Exception(message)

class AuthenticationError(code: String? = null, message: String? = null) :
    HuamiTokenError(code, message ?: "Authentication failed (code=$code)")

class LogoutError(code: String? = null, message: String? = null) :
    HuamiTokenError(code, message ?: "Logout failed (code=$code)")

class DeviceError(code: String? = null, message: String? = null) :
    HuamiTokenError(code, message ?: "Device error (code=$code)")

class MigrationInProgressError(message: String? = null) : Exception(
    message ?: "This feature is not yet reverse-engineered. " +
        "Track progress here: https://codeberg.org/argrento/huami-token/issues/119",
)

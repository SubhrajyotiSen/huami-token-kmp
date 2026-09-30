package org.huamitoken

/**
 * Saves downloaded GPS files to platform-appropriate local storage and returns
 * a user-friendly description of where the files were saved.
 */
expect fun saveGpsFiles(files: Map<String, ByteArray>): String

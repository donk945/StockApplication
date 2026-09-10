import java.io.File
import java.util.Properties

/** 读取 DEEPSEEK_API_KEY：优先 `local.properties`，否则仓库内 `key.properties`。 */
object DeepSeekKeyGen {

    fun readKey(rootDir: File): String {
        val local = readFrom(File(rootDir, "local.properties"))
        if (local.isNotBlank()) {
            return local
        }
        return readFrom(File(rootDir, "key.properties"))
    }

    private fun readFrom(file: File): String {
        if (!file.exists()) {
            return ""
        }
        val props = Properties()
        file.inputStream().use { props.load(it) }
        return props.getProperty("DEEPSEEK_API_KEY", "")?.trim().orEmpty()
    }

    fun writeKotlin(
        outDir: File,
        packageName: String,
        objectName: String,
        key: String,
    ) {
        outDir.mkdirs()
        val escaped = escapeKotlin(key)
        File(outDir, "$objectName.kt").writeText(
            """
            package $packageName

            /** Generated from local.properties DEEPSEEK_API_KEY. Do not edit. */
            internal object $objectName {
                const val DEEPSEEK = "$escaped"
            }
            """.trimIndent() + "\n"
        )
    }

    fun writeObjcHeader(outFile: File, key: String) {
        outFile.parentFile?.mkdirs()
        val escaped = escapeObjc(key)
        outFile.writeText(
            """
            // Generated from local.properties DEEPSEEK_API_KEY. Do not edit.
            #ifndef DeepSeekAPIKey_h
            #define DeepSeekAPIKey_h
            #define BUILTIN_DEEPSEEK_API_KEY @"$escaped"
            #endif
            """.trimIndent() + "\n"
        )
    }

    fun writeEts(outFile: File, key: String) {
        outFile.parentFile?.mkdirs()
        val escaped = escapeEts(key)
        outFile.writeText(
            """
            // Generated from local.properties DEEPSEEK_API_KEY. Do not edit.
            export const DEEPSEEK_API_KEY: string = '$escaped';
            """.trimIndent() + "\n"
        )
    }

    private fun escapeKotlin(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\\$")
    }

    private fun escapeObjc(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun escapeEts(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
    }
}

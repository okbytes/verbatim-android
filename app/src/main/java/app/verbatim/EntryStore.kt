package app.verbatim

import android.content.Context
import android.util.AtomicFile
import java.io.File

class EntryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library-v1.json"))

    fun hasAnyStoredFile(): Boolean =
        file.baseFile.exists() ||
            File(file.baseFile.path + ".bak").exists() ||
            File(file.baseFile.path + ".new").exists()

    fun read(): Library {
        if (!hasAnyStoredFile()) return Library()
        return Library.decode(file.readFully().toString(Charsets.UTF_8))
    }

    /** Returns raw bytes from whichever underlying store file exists, for recovery export. */
    fun readRawBytes(): ByteArray? {
        val candidate =
            listOf(
                    file.baseFile,
                    File(file.baseFile.path + ".bak"),
                    File(file.baseFile.path + ".new"),
                )
                .firstOrNull { it.isFile }
        return candidate?.readBytes()
    }

    fun write(library: Library) {
        val bytes = library.encode().toByteArray()
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}

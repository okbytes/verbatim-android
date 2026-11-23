package app.verbatim

import android.content.Context
import android.util.AtomicFile
import java.io.File

class EntryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library-v1.json"))

    fun read(): Library {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return Library()
        return Library.decodePrivateStore(file.readFully().toString(Charsets.UTF_8))
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

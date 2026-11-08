package com.example.mindmap.data

import android.content.Context
import com.example.mindmap.model.MindMapDocument
import com.example.mindmap.model.MindMapSummary
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Отвечает за сохранение, загрузку, переименование и удаление JSON-файлов карт.
 * Все файлы хранятся во внутреннем хранилище приложения: filesDir/mind_maps/{id}.json
 */
class MindMapRepository(context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mapsDir: File = File(context.filesDir, "mind_maps").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Читает все JSON-файлы из директории карт и возвращает отсортированный по дате список.
     */
    suspend fun getAllMapSummaries(): List<MindMapSummary> = withContext(Dispatchers.IO) {
        val files = mapsDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return@withContext emptyList()

        files.mapNotNull { file ->
            try {
                val json = file.readText()
                val doc = gson.fromJson(json, MindMapDocument::class.java)
                MindMapSummary(
                    id = doc.id,
                    title = doc.title,
                    lastModified = doc.lastModified,
                    nodeCount = doc.nodes.size,
                    connectionCount = doc.connections.size
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Загружает полную интеллект-карту по её ID.
     */
    suspend fun loadMap(id: String): MindMapDocument? = withContext(Dispatchers.IO) {
        val file = File(mapsDir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            gson.fromJson(file.readText(), MindMapDocument::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Сохраняет карту в JSON-файл, обновляя метку времени lastModified.
     */
    suspend fun saveMap(document: MindMapDocument): MindMapDocument = withContext(Dispatchers.IO) {
        val updatedDoc = document.copy(lastModified = System.currentTimeMillis())
        val file = File(mapsDir, "${updatedDoc.id}.json")
        file.writeText(gson.toJson(updatedDoc))
        updatedDoc
    }

    /**
     * Переименовывает существующую карту внутри JSON-файла.
     */
    suspend fun renameMap(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val doc = loadMap(id) ?: return@withContext
        saveMap(doc.copy(title = newTitle.trim()))
    }

    /**
     * Удаляет JSON-файл карты из хранилища.
     */
    suspend fun deleteMap(id: String) = withContext(Dispatchers.IO) {
        val file = File(mapsDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }
    }
}
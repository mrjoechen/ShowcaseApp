package com.alpha.showcase.common.networkfile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile

class RCloneConfigManager(private val configFilePath: String) {

    private val propertiesMap by lazy {
        LinkedHashMap<String, LinkedHashMap<String, String>>()
    }

    private val lock = Mutex()

    @Volatile
    private var initialized = false

    init {
        loadProperties()
    }

    suspend fun addSection(sectionName: String, configMap: Map<String, String>) {
        modifyProperties {
            propertiesMap[sectionName] = LinkedHashMap(configMap)
        }
    }

    suspend fun updateSection(sectionName: String, configMap: Map<String, String>) {
        modifyProperties {
            propertiesMap[sectionName]?.apply {
                putAll(configMap)
            }
        }
    }

    suspend fun replaceSection(sectionName: String, configMap: Map<String, String>) {
        modifyProperties {
            propertiesMap[sectionName] = LinkedHashMap(configMap)
        }
    }

    suspend fun deleteSection(sectionName: String) {
        modifyProperties {
            propertiesMap.remove(sectionName)
        }
    }

    suspend fun getSection(sectionName: String): Map<String, String>? {
        return lock.withLock {
            while (!initialized) {
                // You may handle this case differently if needed
            }
            propertiesMap[sectionName]
        }
    }

    suspend fun clear() {
        modifyProperties {
            propertiesMap.clear()
        }
    }

    private fun loadProperties() = CoroutineScope(Dispatchers.Default).launch {
//        lock.withLock {
//            withContext(Dispatchers.Default) {
//                val configFile = FileSystem.SYSTEM.source(configFilePath.toPath())
//                if (configFile.exists()) {
//                    val lines = configFile.inputStream().reader().readLines()
//                    var currentSection = ""
//                    // Loop through lines and parse sections and properties
//                    for (line in lines) {
//                        if (line.startsWith("[")) {
//                            // Found new section
//                            currentSection = line.substring(1, line.lastIndexOf("]"))
//                            propertiesMap[currentSection] = LinkedHashMap()
//                        } else if (line.contains("=")) {
//                            // Found property
//                            val keyValuePair = line.split("=", limit = 2)
//                            val k = keyValuePair[0].trim()
//                            val v = keyValuePair[1].trim()
//                            propertiesMap[currentSection]?.put(k, v)
//                        }
//                    }
//                }
//                initialized = true
//            }
//        }
    }

    private suspend fun modifyProperties(action: () -> Unit) {
        lock.withLock {
            while (!initialized) {
                // You may handle this case differently if needed
            }
            action()
            saveProperties()
        }
    }

    private suspend fun saveProperties() {
//        withContext(Dispatchers.Default) {
//            FileSystem.source(configFilePath.toPath()).writer().use {
//                val sb = StringBuilder()
//                for ((sec, properties) in propertiesMap) {
//                    sb.append("[$sec]\n")
//                    for ((k, v) in properties) {
//                        sb.append("$k = $v\n")
//                    }
//                    sb.append("\n")
//                }
//
//                it.write(sb.toString())
//            }
//        }
    }
}
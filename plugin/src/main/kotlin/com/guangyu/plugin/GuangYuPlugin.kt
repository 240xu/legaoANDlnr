@file:Suppress("unused")

package com.guangyu.plugin

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guangyu.plugin.bridge.BookSourceManager
import com.guangyu.plugin.engine.js.JsBridge
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi

@Plugin(
    version = BuildConfig.VERSION_CODE,
    name = "光遇聚合",
    versionName = BuildConfig.VERSION_NAME,
    author = "GuangYu",
    description = "Legado JSON 书源引擎插件 - 支持 lyc486 版 Legado 全部 JSON 书源规则 (CSS/XPath/JSONPath/Regex/JS)，可直接导入书源使用",
    updateUrl = "",
    apiVersion = 4
)
class GuangYuPlugin(
    val userDataRepositoryApi: UserDataRepositoryApi
) : LightNovelReaderPlugin {

    companion object {
        private const val TAG = "GuangYuPlugin"
        private const val BOOK_SOURCE_URL = "https://shuyuan.nyasama.net/shuyuan/18832c7d4853f72d2816600a95ef2648.json"
    }

    override fun onLoad() {
        Log.i(TAG, "光遇聚合插件已加载 (v${BuildConfig.VERSION_NAME})")
        Thread {
            try {
                val sources = BookSourceManager.loadFromUrl(BOOK_SOURCE_URL)
                Log.i(TAG, "已加载 ${sources?.size ?: 0} 个书源")
            } catch (e: Exception) {
                Log.e(TAG, "加载书源失败: ${e.message}")
            }
        }.start()
    }

    @Composable
    override fun PageContent(paddingValues: PaddingValues) {
        val sources by BookSourceManager.sourcesFlow.collectAsState()
        var showImportDialog by remember { mutableStateOf(false) }
        var importUrl by remember { mutableStateOf("") }
        var importJson by remember { mutableStateOf("") }
        var statusMessage by remember { mutableStateOf("初始化中...") }

        LaunchedEffect(sources) {
            statusMessage = if (sources.isEmpty()) "正在加载书源..." else "已加载 ${sources.size} 个书源"
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "光遇聚合插件 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("已加载书源:", style = MaterialTheme.typography.titleMedium)
            for (source in sources) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(source.bookSourceName, style = MaterialTheme.typography.titleSmall)
                        Text(source.bookSourceUrl, style = MaterialTheme.typography.bodySmall)
                        source.bookSourceGroup?.let {
                            Text("分组: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(onClick = { showImportDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("导入书源")
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("导入 Legado JSON 书源") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = importUrl,
                                onValueChange = { importUrl = it },
                                label = { Text("书源 URL") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = importJson,
                                onValueChange = { importJson = it },
                                label = { Text("或粘贴 JSON") },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                maxLines = 6
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (importUrl.isNotBlank()) {
                                Thread {
                                    val result = BookSourceManager.loadFromUrl(importUrl)
                                    statusMessage = if (result != null) "已导入 ${result.size} 个书源" else "导入失败"
                                }.start()
                            } else if (importJson.isNotBlank()) {
                                val result = BookSourceManager.loadFromJson(importJson)
                                statusMessage = "已导入 ${result.size} 个书源"
                            }
                            showImportDialog = false
                        }) { Text("导入") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

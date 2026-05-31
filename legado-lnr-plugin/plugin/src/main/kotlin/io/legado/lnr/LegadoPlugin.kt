package io.legado.lnr

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.lnr.util.BookSourceManager
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin

@Plugin(
    version = 1,
    name = "Legado 书源引擎",
    versionName = "1.0.0",
    author = "FanData",
    description = "Legado 书源转译插件 - 支持 lyc486 版 Legado JSON 书源的完整解析",
    updateUrl = "",
    apiVersion = 4
)
class LegadoPlugin : LightNovelReaderPlugin {

    companion object {
        private const val TAG = "LegadoPlugin"
    }

    override fun onLoad() {
        Log.i(TAG, "Legado 书源引擎插件已加载")
        BookSourceManager.init()
    }

    @Composable
    override fun PageContent(paddingValues: PaddingValues) {
        Column(modifier = Modifier.padding(paddingValues)) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Legado 书源引擎 v1.0.0\n已加载 ${BookSourceManager.getSourceCount()} 个书源"
            )
        }
    }
}

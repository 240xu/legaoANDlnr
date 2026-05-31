package io.legado.lnr

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
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
    description = "Legado 书源转译插件 - 支持 lyc486 版 Legado JSON 书源的完整解析，包括聚合源、JS 规则、登录面板",
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
        Column(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Legado 书源引擎 v1.0.0\n" +
                        "已加载 ${BookSourceManager.getSourceCount()} 个书源\n" +
                        "支持：聚合源、JS 规则、登录面板、段评"
            )
        }
    }
}
@file:Suppress("unused")

package com.guangyu.plugin

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guangyu.plugin.api.GuangYuApi
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi

@Plugin(
    version = BuildConfig.VERSION_CODE,
    name = "\u5149\u9047\u805a\u5408",
    versionName = BuildConfig.VERSION_NAME,
    author = "GuangYu",
    description = "\u5149\u9047\u805a\u5408\u4e66\u6e90\u63d2\u4ef6 - \u652f\u6301\u756a\u8304/\u4e03\u732b/\u5854\u8bfb/QQ\u9605\u8bfb/\u4e66\u65d7/\u8f7b\u5c0f\u8bf4\u7b49\u591a\u6e90\u805a\u5408\u641c\u7d22\u548c\u9605\u8bfb",
    updateUrl = "",
    apiVersion = 4
)
class GuangYuPlugin(
    val userDataRepositoryApi: UserDataRepositoryApi
) : LightNovelReaderPlugin {

    companion object {
        private const val TAG = "GuangYuPlugin"
    }

    override fun onLoad() {
        Log.i(TAG, "\u5149\u9047\u805a\u5408\u63d2\u4ef6\u5df2\u52a0\u8f7d")
    }

    @Composable
    override fun PageContent(paddingValues: PaddingValues) {
        Column(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "\u5149\u9047\u805a\u5408\u63d2\u4ef6 v${BuildConfig.VERSION_NAME}\n\u652f\u6301\uff1a\u756a\u8304/\u4e03\u732b/\u5854\u8bfb/QQ\u9605\u8bfb/\u4e66\u65d7/\u8f7b\u5c0f\u8bf4"
            )
        }
    }
}


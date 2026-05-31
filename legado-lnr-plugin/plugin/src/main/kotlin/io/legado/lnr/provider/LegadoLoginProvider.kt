package io.legado.lnr.provider

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.engine.entity.BookSource
import io.legado.engine.http.HttpClient
import io.legado.engine.js.JsEngine
import io.legado.lnr.util.BookSourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Legado 登录提供者 - 渲染书源 loginUi 面板
 *
 * 支持两种模式：
 * 1. loginUi 为空 → 纯 WebView 加载 loginUrl，捕获 Cookie
 * 2. loginUi 非空 → 解析 JSON/JS 动态构建 UI（文本框、密码框、按钮、选择器等）
 *
 * 配置隔离：所有配置以 "书源URL|key" 为键存储
 */
class LegadoLoginProvider(
    private val context: Context,
    private val httpClient: HttpClient = BookSourceManager.getHttpClient(),
    private val jsEngine: JsEngine = BookSourceManager.getJsEngine()
) {
    companion object {
        private const val TAG = "LegadoLoginProvider"
        // 按书源隔离的配置存储
        private val configStore = mutableMapOf<String, String>()
        // 按书源隔离的登录信息
        private val loginInfoStore = mutableMapOf<String, String>()
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        data class Success(val sourceUrl: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    /**
     * 打开登录面板（被动唤起：引擎检测到需要登录时调用）
     */
    fun openLogin(source: BookSource): WebView {
        _loginState.value = LoginState.Loading
        return if (source.loginUi.isNullOrBlank()) {
            createWebViewLogin(source)
        } else {
            createDynamicLogin(source)
        }
    }

    /**
     * 主动唤起：用户手动打开书源设置面板
     */
    fun openSourcePanel(source: BookSource): WebView {
        return openLogin(source)
    }

    /**
     * 获取指定书源的 Cookie
     */
    fun getCookies(sourceUrl: String): String {
        val domain = extractDomain(sourceUrl)
        return CookieManager.getInstance().getCookie(domain) ?: ""
    }

    /**
     * 获取指定书源的登录信息
     */
    fun getLoginInfo(sourceUrl: String): MutableMap<String, String> {
        val json = loginInfoStore[sourceUrl] ?: return mutableMapOf()
        return try {
            com.google.gson.Gson().fromJson(json, Map::class.java)
                .mapKeys { it.key.toString() }
                .mapValues { it.value.toString() }
                .toMutableMap()
        } catch (_: Exception) { mutableMapOf() }
    }

    /**
     * 保存指定书源的登录信息
     */
    fun putLoginInfo(sourceUrl: String, data: Map<String, String>) {
        loginInfoStore[sourceUrl] = com.google.gson.Gson().toJson(data)
    }

    /**
     * 获取配置（按书源隔离）
     */
    fun getConfig(sourceUrl: String, key: String, default: String = ""): String {
        return configStore["$sourceUrl|$key"] ?: default
    }

    /**
     * 设置配置（按书源隔离）
     */
    fun setConfig(sourceUrl: String, key: String, value: String) {
        configStore["$sourceUrl|$key"] = value
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewLogin(source: BookSource): WebView {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookie = cookieManager.getCookie(url)
                if (cookie != null) {
                    httpClient.setCookie(extractDomain(source.bookSourceUrl), cookie)
                    _loginState.value = LoginState.Success(source.bookSourceUrl)
                    Log.i(TAG, "登录 Cookie 已同步: ${source.bookSourceName}")
                }
            }
        }
        val loginUrl = source.loginUrl ?: return webView
        val absoluteUrl = io.legado.engine.util.NetworkUtils.getAbsoluteURL(
            source.bookSourceUrl, loginUrl
        )
        webView.loadUrl(absoluteUrl, source.getHeaderMap())
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createDynamicLogin(source: BookSource): WebView {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // 注入 JS 桥接对象
        val jsBridge = LoginJsBridge(source)
        webView.addJavascriptInterface(jsBridge, "loginBridge")
        // 解析 loginUi 并加载
        val loginUi = source.loginUi ?: return webView
        val html = buildLoginHtml(loginUi, source)
        webView.loadDataWithBaseURL(source.bookSourceUrl, html, "text/html", "UTF-8", null)
        return webView
    }

    /**
     * 构建 loginUi 的 HTML 页面
     * 支持 @js: 前缀（动态生成 JSON）和静态 JSON（RowUi 定义）
     */
    private fun buildLoginHtml(loginUi: String, source: BookSource): String {
        val rowUiJson = when {
            loginUi.startsWith("@js:") -> {
                try {
                    val jsCode = loginUi.substring(4)
                    jsEngine.eval(jsCode, source)?.toString() ?: "[]"
                } catch (e: Exception) {
                    Log.e(TAG, "loginUi JS 执行失败: ${e.message}", e)
                    "[]"
                }
            }
            loginUi.startsWith("<js>") -> {
                try {
                    val endIdx = loginUi.lastIndexOf("<")
                    val jsCode = loginUi.substring(4, if (endIdx > 4) endIdx else loginUi.length)
                    jsEngine.eval(jsCode, source)?.toString() ?: "[]"
                } catch (e: Exception) {
                    Log.e(TAG, "loginUi JS 执行失败: ${e.message}", e)
                    "[]"
                }
            }
            else -> loginUi
        }
        // 将 RowUi JSON 渲染为 HTML 表单
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
body { font-family: sans-serif; padding: 16px; background: #1a1a2e; color: #e0e0e0; }
.form-group { margin-bottom: 16px; }
label { display: block; margin-bottom: 4px; font-size: 14px; color: #aaa; }
input, select, textarea {
    width: 100%; padding: 10px; border: 1px solid #333; border-radius: 8px;
    background: #16213e; color: #e0e0e0; font-size: 16px; box-sizing: border-box;
}
button {
    padding: 10px 24px; border: none; border-radius: 8px; background: #0f3460;
    color: white; font-size: 16px; cursor: pointer; margin-right: 8px; margin-top: 8px;
}
button:hover { background: #1a5276; }
.spinner { display: none; text-align: center; padding: 20px; }
</style>
</head>
<body>
<div id="form-container"></div>
<script>
var rowUiData = $rowUiJson;
var loginData = {};

function buildForm(data) {
    var container = document.getElementById('form-container');
    container.innerHTML = '';
    if (!Array.isArray(data)) return;
    data.forEach(function(item, index) {
        var group = document.createElement('div');
        group.className = 'form-group';
        var type = item.type || 'text';
        var name = item.name || ('field_' + index);
        var label = item.label || name;
        var defaultVal = item.default || '';
        if (type === 'text' || type === 'password' || type === 'edit') {
            var lbl = document.createElement('label');
            lbl.textContent = label;
            group.appendChild(lbl);
            var input = document.createElement('input');
            input.type = type === 'password' ? 'password' : 'text';
            input.value = loginData[name] || defaultVal;
            input.id = 'input_' + name;
            input.addEventListener('change', function() { loginData[name] = this.value; });
            group.appendChild(input);
        } else if (type === 'button') {
            var btn = document.createElement('button');
            btn.textContent = label;
            btn.onclick = function() {
                var action = item.action || '';
                if (action.startsWith('http')) {
                    window.open(action);
                } else if (action) {
                    try {
                        var fn = new Function('result', 'java', action);
                        fn(loginData, { upLoginData: function(d) { loginBridge.onData(JSON.stringify(d)); } });
                    } catch(e) { console.error(e); }
                }
            };
            group.appendChild(btn);
        } else if (type === 'spinner' || type === 'select') {
            var lbl = document.createElement('label');
            lbl.textContent = label;
            group.appendChild(lbl);
            var select = document.createElement('select');
            select.id = 'input_' + name;
            var options = item.values || item.options || [];
            if (typeof options === 'string') options = options.split(',');
            options.forEach(function(opt, i) {
                var option = document.createElement('option');
                option.value = typeof opt === 'object' ? (opt.value || opt.label || '') : opt;
                option.textContent = typeof opt === 'object' ? (opt.label || opt.value || '') : opt;
                if (option.value === defaultVal || i === 0) option.selected = true;
                select.appendChild(option);
            });
            select.addEventListener('change', function() { loginData[name] = this.value; });
            group.appendChild(select);
        } else if (type === 'info' || type === 'text_view') {
            var p = document.createElement('p');
            p.textContent = label;
            p.style.color = '#888';
            group.appendChild(p);
        }
        container.appendChild(group);
    });
    // 添加登录按钮
    var loginBtn = document.createElement('button');
    loginBtn.textContent = '登录';
    loginBtn.onclick = function() { loginBridge.onLogin(JSON.stringify(loginData)); };
    container.appendChild(loginBtn);
}

buildForm(rowUiData);
</script>
</body>
</html>
        """.trimIndent()
    }

    /**
     * JS 桥接对象 - 供 WebView 中的 loginUi JS 调用
     */
    inner class LoginJsBridge(private val source: BookSource) {
        @JavascriptInterface
        fun onData(json: String) {
            try {
                val data = com.google.gson.Gson().fromJson(json, Map::class.java)
                    .mapKeys { it.key.toString() }
                    .mapValues { it.value.toString() }
                putLoginInfo(source.bookSourceUrl, data)
                Log.i(TAG, "loginUi 数据已同步: ${data.keys}")
            } catch (e: Exception) {
                Log.e(TAG, "loginUi 数据解析失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onLogin(json: String) {
            try {
                val data = com.google.gson.Gson().fromJson(json, Map::class.java)
                    .mapKeys { it.key.toString() }
                    .mapValues { it.value.toString() }
                putLoginInfo(source.bookSourceUrl, data)
                // 执行登录 JS
                val loginJs = source.loginUrl
                if (loginJs != null) {
                    // 合并 loginJs 和 login 函数调用
                    val fullJs = "$loginJs\nif(typeof login==='function'){login.apply(this);}"
                    jsEngine.eval(fullJs, source, mapOf(
                        "result" to data,
                        "java" to io.legado.engine.js.EngineJsExtensions()
                    ))
                }
                _loginState.value = LoginState.Success(source.bookSourceUrl)
                Log.i(TAG, "登录成功: ${source.bookSourceName}")
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "登录失败")
                Log.e(TAG, "登录失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getConfig(key: String): String {
            return getConfig(source.bookSourceUrl, key)
        }

        @JavascriptInterface
        fun setConfig(key: String, value: String) {
            setConfig(source.bookSourceUrl, key, value)
        }

        @JavascriptInterface
        fun getLoginInfo(): String {
            return com.google.gson.Gson().toJson(getLoginInfo(source.bookSourceUrl))
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (_: Exception) {
            url
        }
    }
}

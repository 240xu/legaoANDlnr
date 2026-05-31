package io.legado.lnr.provider

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.engine.entity.BookSource
import io.legado.engine.http.HttpClient
import io.legado.engine.js.EngineJsExtensions
import io.legado.engine.js.JsEngine
import io.legado.lnr.util.BookSourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Legado 登录提供者 - 渲染书源 loginUi 面板
 * 支持:
 * 1. 简单 URL 登录 (loginUrl 为普通 URL)
 * 2. 动态 JS 面板 (loginUi 为 RowUi JSON 数组)
 * 3. loginUrl 中定义的 JS 函数 (login, register, logout 等)
 * 4. jsLib 中定义的全局函数 (getServerSettings, getHtmlSettings 等)
 */
class LegadoLoginProvider(
    private val context: Context,
    private val httpClient: HttpClient = BookSourceManager.getHttpClient(),
    private val jsEngine: JsEngine = BookSourceManager.getJsEngine()
) {
    companion object {
        private const val TAG = "LegadoLoginProvider"
        private val configStore = mutableMapOf<String, String>()
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
     * 打开书源面板（登录/设置）
     * 优先使用 loginUi（RowUi JSON），否则使用 loginUrl
     */
    fun openLogin(source: BookSource): WebView {
        _loginState.value = LoginState.Loading
        // 先在 Rhino 中预执行 jsLib 和 loginUrl，使全局函数可用
        preExecuteScripts(source)
        return if (source.loginUi.isNullOrBlank()) {
            createWebViewLogin(source)
        } else {
            createDynamicLogin(source)
        }
    }

    fun openSourcePanel(source: BookSource): WebView = openLogin(source)

    fun getCookies(sourceUrl: String): String {
        val domain = extractDomain(sourceUrl)
        return CookieManager.getInstance().getCookie(domain) ?: ""
    }

    fun getLoginInfo(sourceUrl: String): MutableMap<String, String> {
        val json = loginInfoStore[sourceUrl] ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson<Map<String, String>>(json, type).toMutableMap()
        } catch (_: Exception) { mutableMapOf() }
    }

    fun putLoginInfo(sourceUrl: String, data: Map<String, String>) {
        loginInfoStore[sourceUrl] = Gson().toJson(data)
    }

    fun getConfig(sourceUrl: String, key: String, default: String = ""): String =
        configStore["$sourceUrl|$key"] ?: default

    fun setConfig(sourceUrl: String, key: String, value: String) {
        configStore["$sourceUrl|$key"] = value
    }

    /**
     * 在 Rhino 中预执行 jsLib 和 loginUrl，使全局函数可用
     */
    private fun preExecuteScripts(source: BookSource) {
        try {
            // 执行 jsLib（定义 getVariable, BaseUrl, request 等全局函数）
            val jsLib = source.jsLib
            if (!jsLib.isNullOrBlank()) {
                jsEngine.eval(jsLib, source, jsLib = jsLib)
            }
            // 执行 loginUrl 中的 JS（定义 login, register, logout 等函数）
            val loginUrl = source.loginUrl
            if (!loginUrl.isNullOrBlank() && (loginUrl.startsWith("//") || loginUrl.startsWith("function"))) {
                jsEngine.eval(loginUrl, source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "预执行脚本失败: ${e.message}")
        }
    }

    /**
     * 通过 Rhino 执行 loginUi 按钮的 action
     * 这样 action 中可以调用 jsLib 定义的全局函数
     */
    private fun executeAction(source: BookSource, action: String): String? {
        if (action.isBlank()) return null
        return try {
            jsEngine.eval(action, source)?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "执行 action 失败: $action, 错误: ${e.message}")
            null
        }
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
                }
            }
        }
        val loginUrl = source.loginUrl ?: return webView
        // 如果 loginUrl 是 JS 代码而不是 URL，不加载
        if (loginUrl.startsWith("//") || loginUrl.startsWith("function")) {
            return webView
        }
        val absoluteUrl = io.legado.engine.util.NetworkUtils.getAbsoluteURL(source.bookSourceUrl, loginUrl)
        webView.loadUrl(absoluteUrl, source.getHeaderMap())
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createDynamicLogin(source: BookSource): WebView {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(LoginJsBridge(source), "loginBridge")
        val loginUi = source.loginUi ?: return webView
        val html = buildLoginHtml(loginUi, source)
        webView.loadDataWithBaseURL(source.bookSourceUrl, html, "text/html", "UTF-8", null)
        return webView
    }

    private fun buildLoginHtml(loginUi: String, source: BookSource): String {
        // loginUi 可能是:
        // 1. JSON 数组字符串 (RowUi 格式)
        // 2. @js: 或 <js> 前缀的 JS 代码，执行后返回 JSON 数组
        val rowUiJson = when {
            loginUi.startsWith("@js:") -> {
                try {
                    jsEngine.eval(loginUi.substring(4), source)?.toString() ?: "[]"
                } catch (e: Exception) {
                    Log.e(TAG, "loginUi JS 失败: ${e.message}"); "[]"
                }
            }
            loginUi.startsWith("<js>") -> {
                try {
                    val endIdx = loginUi.lastIndexOf("</js>")
                    val jsCode = if (endIdx > 4) loginUi.substring(4, endIdx) else loginUi.substring(4)
                    jsEngine.eval(jsCode, source)?.toString() ?: "[]"
                } catch (e: Exception) {
                    Log.e(TAG, "loginUi JS 失败: ${e.message}"); "[]"
                }
            }
            else -> loginUi
        }

        return """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:sans-serif;padding:16px;background:#1a1a2e;color:#e0e0e0;margin:0}
.form-group{margin-bottom:16px}
label{display:block;margin-bottom:4px;font-size:14px;color:#aaa}
input,select{width:100%;padding:10px;border:1px solid #333;border-radius:8px;background:#16213e;color:#e0e0e0;font-size:16px;box-sizing:border-box}
button{padding:10px 24px;border:none;border-radius:8px;background:#0f3460;color:white;font-size:16px;cursor:pointer;margin-right:8px;margin-top:8px}
button:hover{background:#1a5276}
.btn-row{display:flex;flex-wrap:wrap;gap:8px}
.info-text{color:#888;font-size:13px;padding:4px 0}
.error{color:#e94560;font-size:13px;margin-top:8px}
</style></head><body>
<div id="form-container"></div>
<div id="result-area"></div>
<script>
var rowUiData=$rowUiJson;
var loginData={};

function buildForm(data){
  var c=document.getElementById('form-container');c.innerHTML='';
  if(!Array.isArray(data))return;
  var btnRow=null;
  data.forEach(function(item,index){
    var type=item.type||'text';
    var name=item.name||('field_'+index);
    var label=item.label||name;
    var def=item.default||'';

    if(type==='text'||type==='edit'){
      var g=document.createElement('div');g.className='form-group';
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var inp=document.createElement('input');inp.type='text';
      inp.value=loginData[name]||def;inp.id='input_'+name;
      inp.addEventListener('change',function(){loginData[name]=this.value;});
      g.appendChild(inp);c.appendChild(g);btnRow=null;
    }else if(type==='password'){
      var g=document.createElement('div');g.className='form-group';
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var inp=document.createElement('input');inp.type='password';
      inp.value=loginData[name]||def;inp.id='input_'+name;
      inp.addEventListener('change',function(){loginData[name]=this.value;});
      g.appendChild(inp);c.appendChild(g);btnRow=null;
    }else if(type==='button'){
      if(!btnRow){btnRow=document.createElement('div');btnRow.className='form-group btn-row';c.appendChild(btnRow);}
      var btn=document.createElement('button');btn.textContent=label;
      var action=item.action||'';
      btn.onclick=function(){loginBridge.onAction(action);};
      btnRow.appendChild(btn);
    }else if(type==='spinner'||type==='select'){
      var g=document.createElement('div');g.className='form-group';
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var sel=document.createElement('select');sel.id='input_'+name;
      var opts=item.values||item.options||[];if(typeof opts==='string')opts=opts.split(',');
      opts.forEach(function(opt,i){
        var o=document.createElement('option');
        o.value=typeof opt==='object'?(opt.value||opt.label||''):opt;
        o.textContent=typeof opt==='object'?(opt.label||opt.value||''):opt;
        if(o.value===def||i===0)o.selected=true;sel.appendChild(o);
      });
      sel.addEventListener('change',function(){loginData[name]=this.value;});
      g.appendChild(sel);c.appendChild(g);btnRow=null;
    }else if(type==='info'||type==='text_view'){
      var g=document.createElement('div');g.className='form-group';
      var p=document.createElement('p');p.className='info-text';p.textContent=label;g.appendChild(p);
      c.appendChild(g);btnRow=null;
    }else{
      // 默认当作文本
      var g=document.createElement('div');g.className='form-group';
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var inp=document.createElement('input');inp.type='text';
      inp.value=loginData[name]||def;inp.id='input_'+name;
      inp.addEventListener('change',function(){loginData[name]=this.value;});
      g.appendChild(inp);c.appendChild(g);btnRow=null;
    }
  });
}
buildForm(rowUiData);

// 显示结果
function showResult(html){
  document.getElementById('result-area').innerHTML=html;
}
function appendResult(html){
  document.getElementById('result-area').innerHTML+=html;
}
</script></body></html>"""
    }

    /**
     * JS Bridge - WebView 与原生代码的桥梁
     * onAction: 执行 loginUi 按钮的 action（通过 Rhino 引擎）
     * onData: 接收登录数据
     * onLogin: 执行登录
     * getConfig/setConfig: 读写配置
     * getLoginInfo: 获取登录信息
     */
    inner class LoginJsBridge(private val source: BookSource) {
        @JavascriptInterface
        fun onAction(action: String) {
            try {
                val result = executeAction(source, action)
                Log.d(TAG, "Action result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Action 执行失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onData(json: String) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val data = Gson().fromJson<Map<String, String>>(json, type)
                putLoginInfo(source.bookSourceUrl, data)
            } catch (e: Exception) {
                Log.e(TAG, "数据解析失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onLogin(json: String) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val data = Gson().fromJson<Map<String, String>>(json, type)
                putLoginInfo(source.bookSourceUrl, data)
                // 在 Rhino 中执行登录
                val loginJs = source.loginUrl
                if (loginJs != null && (loginJs.startsWith("//") || loginJs.startsWith("function"))) {
                    // loginUrl 是 JS 代码，已通过 preExecuteScripts 执行
                    // 调用 login(true) 函数
                    executeAction(source, "login(true)")
                }
                _loginState.value = LoginState.Success(source.bookSourceUrl)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "登录失败")
                Log.e(TAG, "登录失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getConfig(key: String): String = getConfig(source.bookSourceUrl, key)

        @JavascriptInterface
        fun setConfig(key: String, value: String) = setConfig(source.bookSourceUrl, key, value)

        @JavascriptInterface
        fun getLoginInfo(): String = Gson().toJson(getLoginInfo(source.bookSourceUrl))
    }

    private fun extractDomain(url: String): String =
        try { java.net.URL(url).host } catch (_: Exception) { url }
}
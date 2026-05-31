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
import io.legado.engine.js.JsEngine
import io.legado.lnr.util.BookSourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Legado 登录提供者 - 渲染书源 loginUi 面板
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

    fun openLogin(source: BookSource): WebView {
        _loginState.value = LoginState.Loading
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
        val rowUiJson = when {
            loginUi.startsWith("@js:") -> {
                try { jsEngine.eval(loginUi.substring(4), source)?.toString() ?: "[]" }
                catch (e: Exception) { Log.e(TAG, "loginUi JS 失败: ${e.message}"); "[]" }
            }
            loginUi.startsWith("<js>") -> {
                try {
                    val endIdx = loginUi.lastIndexOf("<")
                    val jsCode = loginUi.substring(4, if (endIdx > 4) endIdx else loginUi.length)
                    jsEngine.eval(jsCode, source)?.toString() ?: "[]"
                } catch (e: Exception) { Log.e(TAG, "loginUi JS 失败: ${e.message}"); "[]" }
            }
            else -> loginUi
        }
        return """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:sans-serif;padding:16px;background:#1a1a2e;color:#e0e0e0}
.form-group{margin-bottom:16px}
label{display:block;margin-bottom:4px;font-size:14px;color:#aaa}
input,select{width:100%;padding:10px;border:1px solid #333;border-radius:8px;background:#16213e;color:#e0e0e0;font-size:16px;box-sizing:border-box}
button{padding:10px 24px;border:none;border-radius:8px;background:#0f3460;color:white;font-size:16px;cursor:pointer;margin-right:8px;margin-top:8px}
button:hover{background:#1a5276}
</style></head><body>
<div id="form-container"></div>
<script>
var rowUiData=$rowUiJson;
var loginData={};
function buildForm(data){
  var c=document.getElementById('form-container');c.innerHTML='';
  if(!Array.isArray(data))return;
  data.forEach(function(item,index){
    var g=document.createElement('div');g.className='form-group';
    var type=item.type||'text';var name=item.name||('field_'+index);
    var label=item.label||name;var def=item.default||'';
    if(type==='text'||type==='password'||type==='edit'){
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var inp=document.createElement('input');inp.type=type==='password'?'password':'text';
      inp.value=loginData[name]||def;inp.id='input_'+name;
      inp.addEventListener('change',function(){loginData[name]=this.value;});g.appendChild(inp);
    }else if(type==='button'){
      var btn=document.createElement('button');btn.textContent=label;
      btn.onclick=function(){var action=item.action||'';
        if(action.startsWith('http')){window.open(action);}
        else if(action){try{new Function('result','java',action)(loginData,{upLoginData:function(d){loginBridge.onData(JSON.stringify(d));}});}catch(e){console.error(e);}}
      };g.appendChild(btn);
    }else if(type==='spinner'||type==='select'){
      var l=document.createElement('label');l.textContent=label;g.appendChild(l);
      var sel=document.createElement('select');sel.id='input_'+name;
      var opts=item.values||item.options||[];if(typeof opts==='string')opts=opts.split(',');
      opts.forEach(function(opt,i){
        var o=document.createElement('option');
        o.value=typeof opt==='object'?(opt.value||opt.label||''):opt;
        o.textContent=typeof opt==='object'?(opt.label||opt.value||''):opt;
        if(o.value===def||i===0)o.selected=true;sel.appendChild(o);
      });
      sel.addEventListener('change',function(){loginData[name]=this.value;});g.appendChild(sel);
    }else if(type==='info'||type==='text_view'){
      var p=document.createElement('p');p.textContent=label;p.style.color='#888';g.appendChild(p);
    }
    c.appendChild(g);
  });
  var lb=document.createElement('button');lb.textContent='登录';
  lb.onclick=function(){loginBridge.onLogin(JSON.stringify(loginData));};c.appendChild(lb);
}
buildForm(rowUiData);
</script></body></html>"""
    }

    inner class LoginJsBridge(private val source: BookSource) {
        @JavascriptInterface
        fun onData(json: String) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val data = Gson().fromJson<Map<String, String>>(json, type)
                putLoginInfo(source.bookSourceUrl, data)
            } catch (e: Exception) { Log.e(TAG, "数据解析失败: ${e.message}") }
        }

        @JavascriptInterface
        fun onLogin(json: String) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val data = Gson().fromJson<Map<String, String>>(json, type)
                putLoginInfo(source.bookSourceUrl, data)
                val loginJs = source.loginUrl
                if (loginJs != null) {
                    val fullJs = "$loginJs\nif(typeof login==='function'){login.apply(this);}"
                    jsEngine.eval(fullJs, source, mapOf(
                        "result" to data,
                        "java" to io.legado.engine.js.EngineJsExtensions()
                    ))
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

    private fun extractDomain(url: String): String = try { java.net.URL(url).host } catch (_: Exception) { url }
}

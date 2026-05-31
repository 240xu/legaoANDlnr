package io.legado.engine.provider

/**
 * 登录提供者接口
 * 由 LNR 宿主实现，处理书源登录流程
 */
interface LoginProvider {
    /** 打开登录页面（WebView） */
    fun openLogin(url: String, sourceUrl: String)
    /** 获取指定域名的 Cookies */
    fun getCookies(domain: String): String
    /** 设置指定域名的 Cookie */
    fun setCookie(domain: String, cookie: String)
    /** 检查指定域名是否已登录 */
    fun isLoggedIn(domain: String): Boolean
}
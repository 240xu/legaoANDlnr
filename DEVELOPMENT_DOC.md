# 光遇聚合 LNR 插件开发全记录

## 项目背景

将"光遇聚合"（GuangYu Aggregation）的 Legado 格式书源 JSON 转换为 LNR 插件。

- **书源地址**: `https://shuyuan.nyasama.net/shuyuan/18832c7d4853f72d2816600a95ef2648.json`
- **LNR API**: `apiVersion = 3`（0.4-SNAPSHOT）
- **产物**: `fandata.lnrp`

---

## 一、理解"书源"和"插件"的本质

### 1.1 书源是什么

"光遇聚合"书源本质上是一个 **配置文件 + 一段 JavaScript 程序**。它不是简单的 URL 列表，而是一套完整的爬虫逻辑，告诉阅读 App：

- 去哪里请求数据（服务器地址）
- 怎么请求（GET/POST、参数怎么拼）
- 怎么解析返回的 JSON/HTML（用 JSONPath/CSS/正则/JS 提取字段）
- 怎么展示（发现页怎么布局、搜索结果怎么排列）

书源的核心是一个 **JavaScript 运行时**。Legado 阅读 App 内置了一个 JS 引擎，书源中的所有 `<js>...</js>` 代码都在这个引擎里执行。这些 JS 代码可以调用 App 提供的 Java 桥接方法（`java.ajax()`、`java.getCookie()`、`java.base64Encode()` 等），实现网络请求、Cookie 管理、Base64 编解码等功能。

### 1.2 LNR 插件是什么

LNR 插件是一个 **独立的 Android APK**，通过实现 LNR 定义的接口来提供数据源功能。插件需要自己实现：

- 网络请求（用 OkHttp）
- 数据解析（用 kotlinx.serialization）
- UI 展示（通过 LNR API 返回标准数据结构）

插件不依赖任何外部 JS 引擎，所有逻辑都是原生 Kotlin 代码。

### 1.3 两者的核心区别

```
┌─────────────────────────────────────────────────────┐
│                    Legado 书源                        │
│                                                       │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐    │
│  │ JSON 配置 │ →  │ JS 引擎   │ →  │ Java 桥接方法 │    │
│  │ (规则)    │    │ (执行逻辑) │    │ (ajax/cookie)│    │
│  └──────────┘    └──────────┘    └──────────────┘    │
│                          ↓                            │
│                   ┌──────────┐                        │
│                   │ HTTP 请求 │                        │
│                   └──────────┘                        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│                    LNR 插件                           │
│                                                       │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐    │
│  │ Kotlin   │ →  │ OkHttp   │ →  │ API 端点      │    │
│  │ 原生代码  │    │ HTTP 客户端│    │ (硬编码)     │    │
│  └──────────┘    └──────────┘    └──────────────┘    │
│                          ↓                            │
│                   ┌──────────┐                        │
│                   │ HTTP 请求 │                        │
│                   └──────────┘                        │
└─────────────────────────────────────────────────────┘
```

**关键区别**:
- 书源靠 JS 引擎动态执行规则，插件靠原生代码硬编码逻辑
- 书源可以随时更新 JSON 文件改变行为，插件需要重新编译安装
- 书源依赖宿主 App 提供的桥接方法，插件自己管理所有依赖

---

## 二、书源 JSON 深度解析

### 2.1 搜索流程

书源的搜索流程分两步：

**第一步：构造搜索 URL（searchUrl 字段）**

```javascript
// searchUrl 是一段 <js> 代码
let gysearch = {
    key: key,           // 搜索关键词
    tab: tab,           // 搜索类型：小说/听书/漫画/短剧
    sourcesKey: sourcesKey,  // 来源：全部/番茄/七猫/...
    page: page,
    disabled_sources: disabled_sources
}
// 编码为 base64，生成 data: URL
gysearch = java.base64Encode(JSON.stringify(gysearch));
`data:;base64,${gysearch},{"type":"gysearch"}`;
```

这里生成了一个 `data:` URL，Legado 引擎会拦截这种 URL，解码 base64 内容，然后根据 `type` 字段（`gysearch`）决定调用哪个处理函数。

**第二步：解析搜索结果（ruleSearch.bookList 字段）**

```javascript
// ruleSearch.bookList 也是 <js> 代码
const res = JSON.parse(java.hexDecodeToString(result));
const { key, tab, sourcesKey, page, disabled_sources } = res;
// 构造真正的 API URL
let url = `/search?title=${key}&tab=${tab}&source=${sourcesKey}&page=${page}&disabled_sources=${disabled_sources}`;
request(url);  // 调用统一请求函数
// 结果通过 $.data JSONPath 提取
```

这一步才真正发出 HTTP 请求。注意参数名变了：`key` → `title`，`sourcesKey` → `source`。

**在插件中的实现**：

```kotlin
// 插件直接调用最终的 API，跳过 data: URL 中间步骤
fun search(title: String, tab: String, source: String, page: Int, disabledSources: Int) {
    val url = "$baseUrl/search?title=${encode(title)}&tab=${encode(tab)}&source=${encode(source)}&page=$page&disabled_sources=$disabledSources"
    // OkHttp GET 请求
}
```

### 2.2 书籍详情流程

**书源实现**：

```javascript
// bookUrl 字段 — 从搜索结果构造详情 URL
let gydetail = { book_id, sources, tab, url }
gydetail = java.base64Encode(JSON.stringify(gydetail));
`data:;base64,${gydetail},{"type":"gydetail"}`;

// ruleBookInfo — 实际请求
const requestUrl = `/detail?book_id=${book_id}&source=${sources}&tab=${tab}&variable=${varia}`;
return request(requestUrl, 'POST', { html: html });
```

详情请求是 POST，body 是 `{ html: html }`。`html` 参数在"本地"代理模式下是从网页抓取的 HTML 内容，在"服务器"模式下为空（服务器自己抓取）。

**插件实现**：

```kotlin
fun getBookDetail(bookId: String, source: String, tab: String) {
    val url = "$baseUrl/detail?book_id=${encode(bookId)}&source=${encode(source)}&tab=${encode(tab)}&variable=%7B%7D"
    // POST 空 body（服务器模式）
    val httpRequest = Request.Builder().url(url).post(EMPTY_BODY).build()
}
```

### 2.3 目录流程

**书源实现**：

```javascript
// ruleToc.chapterList — 复杂的 JS 逻辑
const queryString = `book_id=${book_id}&source=${sources}&tab=${tab}&variable=${varia}`;
const data = request(`/catalog?${queryString}`, "POST", { html });
```

目录也是 POST，和详情类似的格式。

**插件实现**：

```kotlin
fun getCatalog(bookId: String, source: String, tab: String) {
    val url = "$baseUrl/catalog?book_id=${encode(bookId)}&source=${encode(source)}&tab=${encode(tab)}&variable=%7B%7D"
    val httpRequest = Request.Builder().url(url).post(EMPTY_BODY).build()
}
```

### 2.4 正文流程

**书源实现**：

```javascript
// chapterUrl 字段 — 构造正文 URL
let gycontent = { book_id, item_id, title, sources, tab, url };
let encodedGycontent = java.base64Encode(JSON.stringify(gycontent));
content_url = `data:;base64,${encodedGycontent},{\"type\":\"gycontent\"}`;

// ruleContent.content — 实际请求
let CONTENT_URL = '/content';
// 调用 request('/content?version=5&source=...&tab=...&item_id=...')
```

**插件实现**：

```kotlin
fun getContent(bookId: String, itemId: String, source: String, tab: String) {
    val url = "$baseUrl/content?version=5&source=${encode(source)}&tab=${encode(tab)}&item_id=${encode(itemId)}"
    // GET 请求
}
```

### 2.5 发现页流程

**书源实现**：

发现页是最复杂的部分。`exploreUrl` 是一大段 JS，构建多行推荐数据：

```javascript
// 第一行：番茄登录/书架信息（需要 Cookie）
infoData = [{ title: "登录番茄", url: '...', style: {...} }];

// 第二行：番茄分组数据（需要登录）
var url = base_url + "/group_name?ssionid=" + fqssionid;
var response = JSON.parse(java.ajax(url));
response.data.forEach(function(group) { ... });

// 第三行：发现页样式
var durl = `${base_url}/discovestyle?source=${sources}&source_type=${source_type}&tab=${tab}`;
var res = java.ajax(durl);
var result = JSON.parse(res);
style_list = result.data || [];

// 第四行起：每个来源的推荐书籍
// 每个来源构造一个展开页 URL
source_list.forEach(item => {
    groupDatas.push({
        title: item,
        url: base_url + "/discover?tab=" + tab + "&source=" + item + "&page={{page}}"
    });
});
```

**插件的简化实现**：

原始发现页太复杂（包含登录、分组、样式、多来源展开等），插件用搜索模拟推荐：

```kotlin
// 对 4 个主要来源分别搜索"推荐"
val sources = listOf("番茄" to "小说", "七猫" to "小说", "塔读" to "小说", "QQ阅读" to "小说")
for ((source, tab) in sources) {
    val books = api.search("推荐", tab, source, 1, 0)
    rows.add(ExploreBooksRow(source, toDisplayBooks(books), false, ""))
}
```

这是一个明显的简化——用 `search("推荐")` 替代了真正的推荐 API（如 `/fqrecommend`）和发现页样式 API（`/discovestyle`）。

---

## 三、LNR 插件 API 详解

### 3.1 插件打包机制

LNR 插件本质上是一个 Android APK，但做了特殊处理：

**构建流程**：

```
Kotlin 源码
    ↓ KSP 注解处理器
自动生成 auto_register_manifest.xml（注册 Plugin 和 WebDataSource）
    ↓ Gradle assembleDebug
APK 文件
    ↓ 重命名为 .lnrp
最终插件包
```

**APK 内部结构**：

```
base.apk (即 .lnrp)
├── AndroidManifest.xml          ← 包含 meta-data 注册信息
├── classes.dex                  ← 所有 Kotlin 编译后的字节码
├── lib/                         ← 原生库（如果有）
└── assets/                      ← 资源文件
```

**AndroidManifest.xml 中的关键声明**：

```xml
<!-- 由 KSP 自动生成 -->
<meta-data android:name="lnr_plugin" android:value="com.guangyu.plugin.GuangYuPlugin"/>
<meta-data android:name="lnr_web_data_source" android:value="com.guangyu.plugin.GuangYuWebDataSource"/>

<!-- 插件发现广播 -->
<receiver android:name=".PluginDiscoveryReceiver" android:exported="true">
    <intent-filter>
        <action android:name="io.nightfish.lightnovelreader.PLUGIN_DISCOVERY"/>
    </intent-filter>
</receiver>
```

### 3.2 LNR 加载插件的完整流程

通过阅读 LNR 主机源码 `PluginManager.kt`，完整加载流程如下：

```
1. LNR 启动
   ↓
2. PluginManager.initAllAppPlugin()
   → 发送广播 io.nightfish.lightnovelreader.PLUGIN_DISCOVERY
   → 扫描已安装的 APK，找到含有 PluginDiscoveryReceiver 的包
   ↓
3. 对每个插件包：
   → DexClassLoader 加载 APK
   → AnnotationScanner 扫描 @Plugin 注解的类
   → PluginMetadata 解析（名称、版本、API 版本等）
   → 检查 ApiCompat.isSupported(apiVersion)
   → 检查 enabledPlugins.contains(packageName)
   ↓
4. loadPlugin(packageName)
   → 创建 PluginContext（数据目录、插件文件等）
   → pluginInjector.providePlugin() 创建插件实例
   → instance.onLoad()  ← 插件初始化
   → 获取 classLoader
   → webBookDataSourceManager.loadWebDataSourcesFromClassLoader()
      → 扫描 @WebDataSource 注解的类
      → 创建实例
      → registerWebDataSource() 注册到管理器
   ↓
5. onWebDataSourceListChange()
   → 找到当前选中的数据源（默认 wenku8）
   → 调用其 onLoad()
```

### 3.3 WebBookDataSource 接口

这是插件需要实现的核心接口：

```kotlin
interface WebBookDataSource {
    // 基础属性
    val id: Int                              // 唯一标识
    val offLine: Boolean                     // 是否离线
    val isOffLineFlow: StateFlow<Boolean>    // 离线状态流

    // 生命周期
    fun onLoad()                             // 数据源被激活时调用

    // 搜索
    val searchProvider: SearchProvider       // 搜索提供者

    // 发现页
    val explorePageProvider: ExplorePageProvider  // 探索页提供者

    // 书籍数据
    suspend fun getBookInformation(id: String): BookInformation
    suspend fun getBookVolumes(id: String): BookVolumes
    suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent
}
```

### 3.4 SearchProvider 接口

```kotlin
interface SearchProvider {
    val searchTypes: List<SearchType>        // 搜索类型列表
    fun search(searchType: SearchType, keyword: String): Flow<SearchResult>
}
```

搜索返回一个 `Flow<SearchResult>`，可以逐条 emit 结果：

```kotlin
override fun search(searchType: SearchType, keyword: String) = flow {
    val results = api.search(keyword, tab, "全部", 1, 0)
    if (results.isNullOrEmpty()) {
        emit(SearchResult.Empty())
    } else {
        for (item in results) {
            emit(SearchResult.MultipleBook(searchItemToBookInfo(item)))
        }
        emit(SearchResult.End())
    }
}
```

### 3.5 ExplorePageProvider 接口

发现页是一个密封接口，有两种实现方式：

```kotlin
sealed interface ExplorePageProvider {
    // 方式一：使用默认布局（卡片页 + 展开页）
    interface DefaultExplorePageProvider : ExplorePageProvider {
        val explorePageIdList: List<String>
        val exploreTapPageDataSourceMap: Map<String, ExploreTapPageDataSource>
        val exploreExpandedPageDataSourceMap: Map<String, ExploreExpandedPageDataSource>
    }

    // 方式二：完全自定义 UI
    interface CustomExplorePageProvider<T> : ExplorePageProvider {
        val uiState: T
        fun init(viewModelScope: CoroutineScope)
        @Composable fun Content(nestedScrollConnection: NestedScrollConnection)
    }
}
```

我们使用 `DefaultExplorePageProvider`，通过 `AbstractDefaultExplorePageProvider` 基类简化实现：

```kotlin
override val explorePageProvider = object : AbstractDefaultExplorePageProvider() {
    init {
        registerTapPage(GuangYuExploreTapPageDataSource())
    }
}
```

### 3.6 ExploreBooksRow 和 ExploreDisplayBook

探索页的数据结构：

```kotlin
// 一行书籍
data class ExploreBooksRow(
    val title: String,                          // 行标题（如"番茄"）
    val bookList: List<ExploreDisplayBook>,      // 书籍列表
    val expandable: Boolean,                     // 是否可展开
    val expandedPageDataSourceId: String         // 展开页数据源 ID
)

// 一本书
data class ExploreDisplayBook(
    val id: String,       // 书籍 ID
    val title: String,    // 书名
    val author: String,   // 作者
    val coverUri: Uri     // 封面图 URL
)
```

注意：这里用的是 `ExploreDisplayBook` 而不是 `BookInformation`。两者字段类似但类型不同。

### 3.7 ID 编码方案

LNR 使用字符串 ID 关联书籍和章节。由于光遇聚合的 API 需要多个参数（bookId、source、tab），需要把这些参数编码到一个字符串 ID 中：

```kotlin
object IdCodec {
    // 书籍 ID: bookId|source|tab
    fun encodeBookId(bookId: String, source: String, tab: String) =
        "$bookId|$source|$tab"

    // 章节 ID: itemId|title|source|tab|tocUrl
    fun encodeChapterId(itemId: String, title: String, source: String, tab: String, tocUrl: String) =
        "$itemId|$title|$source|$tab|$tocUrl"
}
```

这样在后续请求中，可以从 ID 中解码出所有参数：

```kotlin
// 获取书籍详情时
val (bookId, source, tab) = IdCodec.decodeBookId(id)
val detail = api.getBookDetail(bookId, source, tab)
```

---

## 四、开发过程中的错误与修正

### 错误 1：API 端点完全猜错

**最初的假设**：看到书源名有 `gy` 前缀，假设端点是 `POST /gyinfo`、`POST /gytoc`、`POST /gycontent`。

**实际**：通过阅读 `ruleSearch.bookList` 和 `ruleToc.chapterList` 的 JS 代码，发现正确端点是 `/search`、`/detail`、`/catalog`、`/content`。

**教训**：不能凭命名习惯猜测，必须阅读书源 JS 代码中的实际 URL。

### 错误 2：参数名搞错

**问题**：搜索返回"参数不能为空"。

**原因**：JS 代码中变量名是 `key`，但 URL 参数名是 `title`：
```javascript
const { key, tab, sourcesKey, page, disabled_sources } = res;
let url = `/search?title=${key}&tab=${tab}&source=${sourcesKey}...`;
//                ^^^^^        ^^^^^^
//                变量名 key    参数名 title
```

**教训**：JS 变量名和 URL 参数名可以不同，必须看 URL 模板字符串。

### 错误 3：服务器域名不可达

**问题**：`gy.nyasama.net` 无法解析。

**排查**：
1. 主机 DNS 查询失败 → 域名不存在
2. 书源 JS 中找到 `hosts` 数组 → 发现真实主机是 `gyks.cf` 系列和 IP 地址
3. `gyks.cf` 域名在模拟器和主机上都无法解析
4. IP `101.35.133.34:8888` 可以访问

**解决**：实现主机轮换机制，IP 优先，域名作为备用。

### 错误 4：数据源未激活

**问题**：`onLoad()` 未被调用。

**原因**：LNR 只对当前选中的数据源调用 `onLoad()`。默认是 `wenku8`，不是光遇聚合。

**解决**：用户需在设置 → 数据源中手动切换。这是 LNR 的设计决策，不是 bug。

### 错误 5：ExploreDisplayBook 类型不匹配

**问题**：编译报错 `List<BookInformation>` 无法转为 `List<ExploreDisplayBook>`。

**解决**：反编译 LNR API AAR 确认签名，用 `ExploreDisplayBook` 构造。

### 错误 6：AbstractDefaultExplorePageProvider 用法

**问题**：尝试重写 `createDefaultExploreTapPageDataSource()` 报错"overrides nothing"。

**解决**：反编译发现基类提供 `registerTapPage()` 方法，不是通过重写而是通过注册。

### 错误 7：探索页数据加载时机

**问题**：探索页显示"无探索页"。

**原因**：`rowsFlow` 初始为空，数据在后台线程加载，LNR 可能在数据到达前就检查了 flow。

**解决**：在 `init` 块中立即触发 `refresh()`，数据到达时 flow 自动更新。

### 错误 8：返回类型可空性

**问题**：`executeWithFallback` 返回 `T?`，但 `search()` 声明返回非空。

**解决**：改为可空返回类型，调用处做空检查。

---

## 五、功能验证结果

### 5.1 初始化流程（logcat）

```
PluginManager: App plugin successfully installed
GuangYuPlugin: 光遇聚合插件已加载
GuangYuWebDataSource: class initialized
ExplorePageProvider created
GuangYuWebDataSource: onLoad                        ← 用户切换数据源后
GuangYuApi: Found working host: http://101.35.133.34:8888
GuangYuApi: Cloud settings loaded, base URL: https://v1.gyks.cf
GuangYuApi: Cloud host also works: https://v1.gyks.cf
GuangYuWebDataSource: Using base URL: https://v1.gyks.cf
```

### 5.2 搜索验证

```
Searching: keyword=doupocangqiong, tab=小说
Search URL: https://v1.gyks.cf/search?title=doupocangqiong&tab=小说&source=全部
search response: 200
Search parsed: code=0, msg=ok, dataCount=50
```

UI 显示了 50 条搜索结果，来自番茄、七猫、塔读等多个来源。

### 5.3 详情和目录验证

```
detail request: POST .../detail?book_id=...&source=番茄&tab=小说
detail response: 200, body length: 1189

catalog request: POST .../catalog?book_id=...&source=番茄&tab=小说
catalog response: 200, body length: 21428
```

UI 显示：折竹问雪，作者 Sanates，已完结，62 章目录。

### 5.4 正文验证

```
content request: GET .../content?version=5&source=番茄&tab=小说&item_id=...
content response: 200, body length: 147
```

返回"您今日免登录访问次数已达上限(3次)！"——服务器端限制。

### 5.5 探索页验证

探索页正常显示番茄、七猫、塔读、QQ阅读四个来源的推荐书籍，每个来源 10+ 本书。

---

## 六、局限性

| 局限 | 原因 | 影响 |
|------|------|------|
| 无 JS 引擎 | 未移植 Rhino | 无法执行书源的动态 JS 规则 |
| 无账号登录 | 未实现番茄登录 | 番茄/七猫/塔读正文需登录 |
| 发现页简化 | 用 search("推荐") 模拟 | 无书架、无个性化推荐 |
| 服务器依赖 | 后端 API 是唯一数据源 | 服务器关闭则插件失效 |
| 无本地代理 | 未实现 HTML 抓取 | 无法使用"本地"网络模式 |
| 无书架同步 | 未移植同步逻辑 | 无法跨设备同步阅读进度 |
| 免登录限制 | 服务器策略 | 每日仅 3 次免费正文访问 |
| 无线路切换 | 未移植超时检测 | 服务器慢时无法自动切换 |

---

## 七、改进建议

**短期**: 添加番茄 Cookie 管理、调用 `/discovestyle` 获取真实布局、添加本地缓存

**中期**: 集成 Rhino 引擎、移植发现页完整逻辑、支持指定来源搜索

**长期**: 开发通用 Legado 书源导入器、实现 JS 沙箱和 Java 桥接层

---

## 八、构建与部署

```bash
# 环境
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
export ANDROID_HOME="C:\Users\ova44\AppData\Local\Android\Sdk"

# 构建
./gradlew.bat :plugin:assembleDebug --no-daemon

# 安装
adb install -r -t plugin-debug.apk

# 在 LNR 中启用：设置 → 扩展插件 → 启用"光遇聚合"
# 切换数据源：设置 → 数据源 → 选择"光遇聚合"
```

**国内镜像**:
- Gradle: `https://mirrors.cloud.tencent.com/gradle/`
- Maven: `https://maven.aliyun.com/repository/public`

---

## 九、参考资源

- LNR 插件 API: `https://api-doc.lnr.nariko.org/`
- LNR 插件模板: `https://github.com/dmzz-yyhyy/LightNovelReaderPlguin-Template`
- LNR 主项目: `https://github.com/dmzz-yyhyy/LightNovelReader`（refactoring 分支）
- Legado 源码: `https://gitee.com/lyc486/legado`

---

*基于实际开发和模拟器测试编写，所有错误和修正均为真实经历。*
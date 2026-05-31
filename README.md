# FanData - Legado 书源 LNR 转译插件

基于 [lyc486 版 Legado](https://gitee.com/lyc486/legado) 的解析引擎，为 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 提供完整的 Legado JSON 书源支持。

## 功能特性

- **完整书源兼容**：直接导入 lyc486 版 Legado JSON 书源，无需修改
- **全规则类型支持**：CSS 选择器、XPath、JSONPath、正则表达式、JavaScript
- **JS 安全沙箱**：集成 Rhino 引擎，配备 ClassShutter 安全限制
- **loginUi 面板**：完整渲染书源作者定义的多功能 Web 面板（登录、配置、开关等）
- **配置隔离存储**：每个书源的配置独立存储，互不影响
- **段评支持**：通过 custom.js 注入实现段落评论功能
- **双重唤起**：支持被动唤起（引擎检测登录需求）和主动唤起（用户手动打开）

## 项目结构

```
legado-lnr-plugin/
├── legado-engine/          # 纯逻辑引擎层（不依赖 Android UI）
│   └── src/main/java/io/legado/engine/
│       ├── analyze/        # 规则解析器（RuleAnalyzer, AnalyzeByXPath/JSoup/JSonPath/Regex）
│       ├── entity/         # 数据模型（BookSource, SearchRule, ExploreRule 等）
│       ├── http/           # HTTP 客户端（OkHttp 封装）
│       ├── js/             # JS 引擎（Rhino + 安全沙箱）
│       ├── provider/       # 接口定义（LoginProvider, ConfigProvider, Logger, CacheProvider）
│       └── util/           # 工具类（ContentHelper, NetworkUtils）
├── plugin/                 # LNR 插件层
│   └── src/main/kotlin/io/legado/lnr/
│       ├── LegadoPlugin.kt           # 插件入口
│       ├── LegadoWebDataSource.kt    # LNR WebBookDataSource 实现
│       ├── adapter/                  # 探索页适配器
│       ├── provider/                 # 登录/配置提供者实现
│       └── util/                     # 书源管理、段评脚本
└── build.gradle.kts
```

## 构建

1. 使用 Android Studio 打开 `legado-lnr-plugin/` 目录
2. 等待 Gradle 同步完成
3. 运行 `:plugin:assembleDebug` 任务
4. 生成的 `.lnrp` 文件位于 `plugin/build/outputs/apk/debug/`

## 安装

将生成的 `.lnrp` 文件传输到手机，在 LightNovelReader 中导入即可。

## 许可证

本项目采用 [GPL-3.0](LICENSE) 许可证。

本项目是基于 GPL-3.0 的 [Legado（lyc486 版）](https://gitee.com/lyc486/legado) 的衍生作品。

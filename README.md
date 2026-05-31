# 光遇聚合 Legado 书源引擎插件

Light Novel Reader (LNR) 插件，内嵌 lyc486 版 Legado JSON 书源解析引擎，支持直接导入和使用 Legado 格式的 JSON 书源。

## 功能特性

- **完整 Legado 规则引擎支持**：CSS 选择器、XPath、JSONPath、正则表达式、JavaScript 脚本
- **Rhino JS 引擎**：内嵌 Mozilla Rhino JavaScript 引擎，支持书源中的 JS 规则
- **搜索**：支持 Legado 书源的搜索规则
- **发现页**：支持书源的发现页/探索页规则
- **书籍详情**：获取书籍信息（名称、作者、封面、简介等）
- **目录**：获取章节目录
- **正文**：获取章节正文内容
- **书源导入**：支持从 URL 或 JSON 字符串导入 Legado 书源
- **WebView 书源面板**：支持 loginUi 书源设置面板

## 使用方法

1. 在 LNR 中安装此插件
2. 插件会自动加载默认的光遇聚合书源
3. 也可在插件页面手动导入其他 Legado JSON 书源

## 构建

```bash
./gradlew :plugin:assembleDebug
```

输出文件: `plugin/build/outputs/apk/debug/plugin-debug.apk.lnrp`

## 许可证

GPL-3.0 License

衍生自 [lyc486 版 Legado](https://gitee.com/lyc486/legado)

# 光遇聚合 LNR 插件

基于[光遇聚合书源](https://shuyuan.nyasama.net/shuyuan/18832c7d4853f72d2816600a95ef2648.json)开发的
[LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 插件。

## 支持的数据源

- **番茄小说** - Fanqie Novel
- **七猫小说** - Qimao Novel
- **塔读文学** - Tadu Literature
- **QQ阅读** - QQ Reading
- **书旗小说** - Shuqi Novel
- **轻小说** - Light Novel

## 支持的功能

- 多源聚合搜索
- 分类发现页（小说/听书/漫画/短剧）
- 书籍详情查看
- 目录获取
- 正文阅读
- 云端配置自动加载

## 构建

1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 运行 `:plugin:assembleDebug` 任务
4. 生成的 `.lnrp` 文件位于 `plugin/build/outputs/apk/debug/`

## 安装

将生成的 `.lnrp` 文件传输到手机，在 LightNovelReader 中导入即可。

## 许可证

本项目采用 [GPL-3.0](LICENSE) 许可证。

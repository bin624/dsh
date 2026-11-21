# DeepSeek 余额查询

一个 Android 原生 Compose 应用，用于查询 DeepSeek API 账户余额。视觉以浅灰紫背景、白色圆角卡片、蓝色强调色和悬浮液态玻璃底部导航为主。

## 功能

- 调用 DeepSeek 官方 `GET /user/balance` 接口查询账户余额。
- 展示账户可用状态、总余额、赠送余额、充值余额和币种。
- 支持 CNY / USD 多币种余额信息。
- API Key 使用 Android Keystore 保护的 AES-GCM 加密后保存在本机。
- 页面启动时自动查询，首页可手动刷新。
- 使用 Backdrop 的 vibrancy、blur、lens、highlight、shadow 效果构建悬浮液态玻璃导航栏。

## 运行

使用 Android Studio 打开项目，等待 Gradle 同步后运行 `:app`。

命令行构建（项目包含 Gradle Wrapper）：

```bash
./gradlew :app:assembleDebug
```

建议使用 JDK 17 或更高版本，并安装 Android SDK Platform 37。

## 配置 API Key

启动应用后进入底部「密钥」页，粘贴在 DeepSeek 开放平台创建的 API Key 并保存。密钥不会写入源码或仓库。余额请求由应用直接通过 HTTPS 发往 `api.deepseek.com`。

## 项目结构

- `app/src/main/java/com/bin624/dsh/data`：DeepSeek 余额 API 与响应模型。
- `app/src/main/java/com/bin624/dsh/security`：Android Keystore 加密存储。
- `app/src/main/java/com/bin624/dsh/ui`：页面、配色和液态玻璃底部导航。
- `docs/liquid-glass-bottom-navigation.md`：Backdrop 方案与实现说明。
- `docs/deepseek-balance-api.md`：接口字段、鉴权与能力边界。

## 技术参考

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- [Backdrop 官方文档](https://kyant.gitbook.io/backdrop)
- [DeepSeek 查询余额 API](https://api-docs.deepseek.com/zh-cn/api/get-user-balance)

Backdrop 2.0.1 采用 Apache-2.0 License。本项目通过 Maven Central 依赖该库，并自行组合其公开 Backdrop API 构建导航栏；Backdrop 本身不提供现成的高阶底部导航组件。

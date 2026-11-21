# DeepSeek 余额 API 说明

## 请求

- Method：`GET`
- URL：`https://api.deepseek.com/user/balance`
- Header：`Authorization: Bearer <DEEPSEEK_API_KEY>`
- 请求参数：无。

官方文档：[查询余额](https://api-docs.deepseek.com/zh-cn/api/get-user-balance)。

## 响应字段

```json
{
  "is_available": true,
  "balance_infos": [
    {
      "currency": "CNY",
      "total_balance": "110.00",
      "granted_balance": "10.00",
      "topped_up_balance": "100.00"
    }
  ]
}
```

- `is_available`：当前余额是否足以支持 API 调用。
- `balance_infos`：按币种返回的余额数组。
- `currency`：币种，官方文档列出 CNY 与 USD。
- `total_balance`：总余额。
- `granted_balance`：未过期的赠送余额。
- `topped_up_balance`：充值余额。

金额字段按字符串解析，避免先转为浮点数造成精度损失。

## 错误处理

应用针对常见 HTTP 状态提供提示：

- 401 / 403：API Key 无效或无权限。
- 429：请求过于频繁。
- 5xx：DeepSeek 服务端暂时不可用。
- 其他非 2xx：显示 HTTP 状态码。

## 能力边界

此接口只返回账户余额构成和 `is_available` 状态，不包含 Token 使用量、消费流水、每日用量、账单历史或赠金到期时间。应用只展示接口实际返回的数据，不据此推算消费趋势。

## 安全

API Key 使用 Android Keystore 中不可导出的 AES 密钥，以 AES/GCM/NoPadding 加密后存入应用私有 SharedPreferences。请求通过 HTTPS 直接发送到 DeepSeek 官方 API，不经过本项目的中转服务器。卸载应用或清除应用数据后，本地密钥可能需要重新配置。

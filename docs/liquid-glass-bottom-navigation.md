# 液态玻璃底部导航实现说明

## 1. 参考项目与边界

实现参考 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 及 [Backdrop 官方文档](https://kyant.gitbook.io/backdrop)，使用 Maven Central 依赖：

```kotlin
implementation("io.github.kyant0:backdrop:2.0.1")
```

Backdrop 是背景采样与图形效果库，并不是完整的 UI 组件库。上游 README 明确说明不包含高阶 UI 组件，导航栏需要由应用开发者自行组合。因此本项目实现自己的 `LiquidGlassBottomBar`，使用公开 Backdrop API，不把示例 App 的导航业务组件作为库 API 依赖。

## 2. 背景采样机制

页面内容使用 `rememberLayerBackdrop()` 创建采样图层，并通过 `layerBackdrop(backdrop)` 记录当前页面绘制内容。悬浮导航栏位于内容上层，通过同一个 backdrop 采样其后方背景。

```text
页面内容
  └─ layerBackdrop(backdrop) 记录背景图层
悬浮导航栏
  ├─ drawBackdrop(backdrop) 绘制玻璃容器
  └─ drawBackdrop(backdrop) 绘制选中态玻璃胶囊
```

这使玻璃层能对真实页面内容进行采样，而不是仅仅绘制一块固定半透明白色背景。

## 3. 玻璃容器效果

导航容器使用 Capsule 形状，组合以下效果：

- `vibrancy()`：增强采样背景的通透感。
- `blur(12.dp)`：柔化背景细节。
- `lens(24.dp, 28.dp)`：生成折射与透镜式背景形变。
- `Highlight.Default`：边缘高光。
- `Shadow` 与 `InnerShadow`：外部投影及内部边缘阴影。
- 半透明暖白表面和白色细描边：匹配参考图的浅色玻璃质感。

选中项使用独立的 Capsule 玻璃层，增加 lens 折射、高光和弹簧位移动画。导航项仍保留独立点击和 TalkBack Tab 语义。

## 4. 与设计截图配色对齐

主题色集中在 `ui/Palette.kt`：

| 用途 | 色值 |
|---|---|
| 页面背景 | `#F1EFF3` |
| 卡片 | `#FFFCFD` |
| 次级信息块 | `#F0EEF2` |
| 主文字 | `#171923` |
| 蓝色强调 | `#087FF5` |
| 淡蓝选中玻璃 | `#E6EDFC` |
| 绿色状态 | `#DCEFD5` |

布局使用浅灰紫底色、白色大圆角卡片、浅灰信息块、蓝色操作强调和底部悬浮胶囊导航。

## 5. 兼容性与验收

Backdrop 的实际折射、模糊表现依赖设备 Compose 图形栈对 RenderEffect / RuntimeShader 的支持。能力受限的设备可能以半透明表面、高光和描边作为降级视觉。

验收时应至少在 Android 12+ 真机检查：背景采样是否正确、选中项是否跟随导航切换、滚动时底栏是否保持悬浮、手势及系统导航栏是否有遮挡，以及低端设备上的帧率。

## 6. 许可证

Backdrop 2.0.1 的发布元数据标注 Apache License 2.0。本项目通过 Maven Central 引入依赖，没有复制其示例项目源码。

# 液态玻璃底部导航实现说明

## 1. 视频目标与交互拆解

参考用户提供的导航操作视频，底栏拆分为两个独立视觉层：

1. 稳定的半透明圆角胶囊容器：始终固定在屏幕底部，负责背景采样、柔化、通透感、高光和外部投影。
2. 可移动的选中液态透镜：宽度对应一个 Tab，位于图标与文字之上，能被手指横向拖动；拖动时拉伸、折射相邻图标和文字，松手后吸附到最近 Tab 并回弹。

重点不是把选中项涂成蓝色圆角矩形，而是让一块真正采样底栏内容的玻璃透镜在栏目间移动。蓝色只负责当前选中图标和文字的状态提示。

## 2. 参考项目与依赖

实现参考 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 及 [Backdrop 官方文档](https://kyant.gitbook.io/backdrop)，依赖：

```kotlin
implementation("io.github.kyant0:backdrop:2.0.1")
implementation("io.github.kyant0:shapes:1.2.1")
```

Backdrop 是背景采样和图形效果库，并不是完整 UI 组件库。上游 README 明确说明不包含高阶组件，因此本项目自行组合 `LiquidGlassBottomBar`。

## 3. 双层背景采样机制

页面本身由页面级 `LayerBackdrop` 记录；导航栏图标和文字另由 `tabBackdrop` 记录。选中液态透镜使用 `rememberCombinedBackdrop(pageBackdrop, tabBackdrop)`，同时采样页面和导航内容。

```text
页面内容 ── layerBackdrop(pageBackdrop) ─────────┐
                                                 ├─ rememberCombinedBackdrop
导航图标/文字 ── layerBackdrop(tabBackdrop) ─────┘
                                                 ↓
                                  移动的 drawBackdrop 液态透镜
```

这一步非常关键：如果选中层只采样页面背景，它只会像一块磨砂玻璃盖在导航图标上；结合导航自身的图层后，透镜才会扭曲、放大和折射相邻图标与文字，形成视频里那种移动玻璃感。

## 4. 操作与动画

- **点击切换**：点击任一 Tab，选中透镜使用弹簧动画滑到目标栏目，同时短暂提高按压强度。
- **横向拖动**：手势从导航栏区域开始，透镜中心持续映射到手指的横向位置，允许跨多个 Tab 拖动。
- **松手吸附**：拖动结束时将当前连续位置取整到最近 Tab，并更新选中状态。
- **拉伸与回弹**：按压进度控制透镜的纵向鼓起；拖动速度控制横向拉伸和纵向压缩；松手后速度归零、透镜回到标准尺寸。
- **折射反馈**：移动透镜叠加 `lens` 与 `chromaticAberration`，同时使用 Highlight、Shadow、InnerShadow；底栏容器本身保持稳定，不跟随透镜左右移动。

## 5. 玻璃效果组成

容器与透镜分别组合 Backdrop 的图形效果：

- `vibrancy()`：提升背景内容的通透感。
- `blur(10.dp)`：柔化被采样的背景。
- `lens(..., chromaticAberration = true)`：产生透镜折射与轻微色散。
- `Highlight.Default`：高光边缘。
- `Shadow` / `InnerShadow`：外部投影和内部边缘阴影。
- 极低不透明度的白色表面：避免把选中层做成纯色实心胶囊。

## 6. 配色

主题色集中在 `ui/Palette.kt`：

| 用途 | 色值 |
|---|---|
| 页面背景 | `#F1EFF3` |
| 卡片 | `#FFFCFD` |
| 次级信息块 | `#F0EEF2` |
| 主文字 | `#171923` |
| 蓝色强调 | `#087FF5` |
| 淡蓝玻璃参考色 | `#E6EDFC` |
| 绿色状态 | `#DCEFD5` |

选中项文字和图标使用蓝色；玻璃透镜表面维持近透明暖白色，让下方内容能通过折射可见。

## 7. 兼容性与验收

Backdrop 的实际折射、模糊表现依赖设备 Compose 图形栈对 RenderEffect / RuntimeShader 的支持。能力受限的设备可能以半透明表面、高光和描边作为降级视觉。

真机验收至少应检查：
- 点击四个栏目时，选中透镜是否平滑移动；
- 从当前透镜上按住左右拖动时，透镜是否连续跟手，而不是逐项跳动；
- 拖过图标时，图标和文字是否在透镜内部产生折射/色散；
- 松手后是否吸附到最近栏目，并自然回弹；
- 快速拖动后容器是否稳定、透镜是否不会抖动或跳位；
- 滚动页面时导航栏是否保持悬浮，且不遮挡系统手势区。

## 8. 许可证

Backdrop 2.0.1 的发布元数据标注 Apache License 2.0。本项目通过 Maven Central 引入依赖，并基于公开 API 自行实现导航组件。

# 水平转场动画排查记录（slide / std / 400ms）

> 用途：把「进入转场下层页到底有没有播动画」这条排查链路固化下来，后续不必重跑实验。
> 状态：**已定位并修复**。
> 根因：`slide` 也被挪进了内容级动画，而内容级动画由**应用主线程**驱动，重页面首帧卡顿会把
> 动画帧整个吞掉；`Animation` 又是墙钟驱动，下一帧画出来进度已到头 → 「一帧滑走 / 一帧白屏」。
> 修法：纯位移的 `slide` 回到 **window 级**（由系统合成器驱动）。详见 §0.5 / §5 / §8。

---

## 0. 结论

### 0.1 定论

**转场动画机制 100% 正常。** 铁证是 A/B 对照：

| 转场 | 退出页 | 进入页 | 逐帧表现 | 观感 |
|---|---|---|---|---|
| 我的 → 设置 | MainActivity | `SettingsActivity`（纯本地） | **连续 19 帧递减位移**，两层同框 | 平滑滑入 |
| 首页 → 动态详情 | MainActivity | `FeedActivity`（走网络） | **1 帧滑走 → 全白 → 硬切出内容** | 一帧白屏 |

两次实验**代码路径、动画资源、时长、退出页完全相同**，唯一变量是进入页。

**「一帧白屏」的真实成因**：`FeedActivity` 进入时在转场那 400ms 内主线程严重卡顿，
`Animation` 是**墙钟驱动**的（不是帧计数），一帧耗时 85~200ms 就意味着进度直接跳 20%~50%，
画不了几帧位移就走完了；同时 `FeedActivity` 的内容是整屏不透明 `colorSurface` 骨架 → 全白。

### 0.2 HWUI 帧统计（决定性对照）

`dumpsys gfxinfo com.example.c001apk reset` → 触发转场 → `dumpsys gfxinfo com.example.c001apk`

| 指标 | 首页 → 动态详情 | 我的 → 设置 |
|---|---|---|
| Total frames | 84 | 82 |
| Janky frames | 8 (9.52%) | 6 (7.32%) |
| 50th | 12ms | 13ms |
| **90th** | **40ms** | 20ms |
| **95th** | **85ms** | 32ms |
| 99th | 200ms | 150ms |
| Missed Vsync | 6 | 4 |

详情页直方图里单帧 `38/40/42/46/48/85×2/89/109/200 ms` 各一帧 —— 400ms 的转场只够画 2~4 帧。

### 0.3 一个早已存在的旁证

`animType=none`（完全不挂动画）与 `animType=slide` 的白屏时长几乎相同（**34 帧 vs 33 帧**）。
说明**白屏时长＝页面加载时长**，动画是叠加在其上的独立变量。当时把这个结果读成了「动画没生效」，
实际是「加载慢」把两种配置的表现拉平了。

### 0.4 已证伪的假设

- ~~`lout` 没播~~；~~编码丢帧假象~~；~~映射表 `_lout` 指错资源~~；~~上层窗口不透明盖住下层~~；
- ~~`clipToOutline` 把页面裁没了~~（`我的→设置` 证明裁剪路径正常）；
- ~~系统动画缩放干扰~~。

### 0.5 最终根因：驱动方式（window 级 vs 内容级）

用户提供了一个决定性线索：**`6c2ce299`（CI run 37407370264）那版没有这个问题**。
看提交历史，那条分界线非常清楚：

```
6c2ce299  feat(anim): 实验项新增「视差滑动」类型        <- 用户说的「初始版本」，转场还是 window 动画
093148bf  feat(anim): ... 转场改内容级动画(圆角/背景)   <- 分界线：动画从 window 挪到 android.R.id.content
17854257  feat(ui): 页面引入 M3 色阶差
9ded85ce  fix(anim): 转场两层同框——窗口透明 + 新旧页同帧驱动 + fillAfter
e5826fbf  fix(anim): 窗口透明后给非栈顶页垫页面底色
154f9096  fix(anim): 进入动画在首帧绘制前启动，消掉「先闪一屏空白再滑入」
034c022a / 7622c6b8 / 9a9a26f3   <- 连续三个 fix 都在跟「首帧钩子」搏斗
```

**两种动画的驱动者完全不同：**

| | window 级（`makeCustomAnimation` / `overridePendingTransition`） | 内容级（`view.startAnimation` on `android.R.id.content`） |
|---|---|---|
| 动画跑在哪 | 窗口 surface 上，由**系统合成器**逐帧变换 | 应用自己的绘制回调里（`View.applyLegacyAnimation` 在 `draw()` 内） |
| 主线程卡 200ms 会怎样 | 窗口照旧每帧在动 | **这 200ms 一帧都不画**；`Animation` 按墙钟算进度，下一帧画出来时进度已经到头 |
| 重页面 push | 平滑 | 「一帧滑走 / 一帧白屏」 |

这条解释了为什么同一份资源、同一个退出页，只有进入 `FeedActivity` 时出问题——详情页要发请求 +
解析 + 布局，把主线程卡住，内容级动画就没有帧可画了；而 `SettingsActivity` 是纯本地页，主线程不卡，
所以 t5 那 19 帧是完整的。

也解释了 §0.3 那个旁证：`none` 与 `slide` 白屏时长一样，是因为那时**两条路都还在内容级**。

而 `slide` 根本不需要内容级：纯位移时两个窗口**位移恒互补**（旧页右边缘 = 新页左边缘），
一个像素的缝都不会有，压根不需要「垫底色」。真正需要内容级的只有 `fade` / `parallax`
——它们会露出窗口之外的东西。修法见 §5。

---

## 1. 环境与工具

| 项 | 值 |
|---|---|
| 设备 | `10.0.0.38:5555`，ARM64 Android 13 GSI，320x480，低密度 |
| adb | `C:\Android\Sdk\platform-tools\adb.exe` |
| APK | `com.example.c001apk`，`c001apk_next V1.0.2-debug`（17:57 构建） |
| ffmpeg | `C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_*\ffmpeg-9.0.2-full_build\bin\ffmpeg.exe` |
| 临时目录 | `C:\Users\Admin\Downloads\dbg`（录像 t2~t6、抽帧、`an5.py`/`grid.py`/`bounds.py`/`latx.py`） |
| 代码仓 | `c:\Users\Admin\CodeBuddy\c001apk-md3e`（debug3 / M3E 实验线） |

---

## 2. 设备档位（用户拍板：slide + std + 400）

写入 `/data/data/com.example.c001apk/shared_prefs/settings.xml`：

```xml
<string name="animType">slide</string>
<string name="animCurve">std</string>
<string name="animDuration">400</string>
```

**可靠写入姿势**（踩坑见 §7）：

```powershell
adb push .\fix_std400.xml /data/local/tmp/fix.xml
adb shell "run-as com.example.c001apk sh -c 'cat /data/local/tmp/fix.xml > /data/data/com.example.c001apk/shared_prefs/settings.xml'"
adb shell "run-as com.example.c001apk grep anim /data/data/com.example.c001apk/shared_prefs/settings.xml"
```

改完必须 `am force-stop` 冷启动才生效。

---

## 3. 代码链路

| 文件 | 作用 |
|---|---|
| `app/.../util/TransitionAnim.kt` | `playEnter` / `startExit` / `animateContent` / `runOnFirstFrame` / `applyRoundClip` / `refreshBackdrops` |
| `app/.../util/TransitionAnimTable.kt` | `"{type}_{curve}_{duration}_{slot}"` → `R.anim.exp_*` |
| `app/.../res/anim/exp_slide_std_400_{rin,lin,lout,rout}.xml` | 四段位移资源 |
| `app/.../util/PrefManager.kt` | `animType`(默认 slide) / `animCurve`(默认 emph) / `animDuration`(默认 300)；**exit 时长＝enter-50ms** |

- `rin` 右进、`lin` 左进、`lout` 左出、`rout` 右出。
- 进入：上层 `rin`（`100%→0`），下层 `lout`（`0→-100%`）→ 两页**同向左滑**（推挤式）。
- 返回：当前页 `rout`（`0→100%`），下层 `lin`（`-100%→0`）。
- 四个 `exp_slide_std_400_*` 都是**纯 `<translate>`**，无 alpha/scale。
  ⚠️ `TransitionAnim.kt` line 284-286 的注释「旧页 lout 的终点是半屏外 + 0.7 + 全透明」是**过期描述**（那是 parallax 的行为），容易误导排查，建议清掉。
- `refreshBackdrops()`（line ~168-181）：给**非栈顶页的 DecorView** 垫 `?colorSurface` 不透明底色，避免透出桌面；栈顶页保持透明以便两层同框。
- 日志 tag：`TransitionAnim`（`D/TransitionAnim`）。

### 资源 ID 交叉验证

AAPT 按文件名字母序分配 entry，`lin < lout < rin < rout`：

| 来源 | 上层 | 下层 | 结论 |
|---|---|---|---|
| `slide_std_400` | `rin` 0x7F010114 | `lout` 0x7F010113 | 与字母序吻合 ✓ |
| `fade_std_500` | `fin` 0x7F010050 | `fout` 0x7F010051 | 同样吻合 ✓ |

→ 映射表无误。

### push 时的日志（t6，首页→详情）

```
15.862 enterOptions
15.864 consumeEnter -> true
15.862 playEnter FeedActivity lower=MainActivity
15.867 animateContent FeedActivity res=2130772244 view=ContentFrameLayout anim=true laidOut=false size=0x0 attached=false
15.868 runOnFirstFrame 挂 layout 回调 laidOut=false size=0x0
15.905 firstFrame fire size=320x441 laidOut=false      <- 距 playEnter 仅 38ms
15.906 animateContent MainActivity res=2130772243 ... laidOut=true size=320x480 attached=true
15.906 firstFrame fire size=320x480 laidOut=true
```

对照 t5（我的→设置）：`firstFrame fire` 距 playEnter **152ms**，且 `size=320x480`。
两者动画都正常启动、都拿到非 0 资源。

---

## 4. 实验记录

方法：`screenrecord --bit-rate 20000000` + `ffmpeg -fps_mode passthrough` 抽帧，再用 Python 做帧间差 / 反解位移。
`input tap 160 340` = 首页信息流第一项；`input tap 223 431` = 底部「我的」；`input tap 302 45` = 「我的」页右上设置。

### 4.1 t2 / t6 —— push 首页 → 动态详情（slide+std+400）

```
idx009  chg=   ~10px 位移起点（首页开始左移）
idx010  chg= 60212    整屏一帧变白
idx011-024            几乎无变化，仅一个小元素缓慢移动 317→187
idx028-038            全白静止
idx039  chg= 26932    硬切出详情页
idx042-063            图片逐行加载
```

**页面滑走只占 1 帧。** 反解位移实验（把首页帧左移 s 像素与每帧左半匹配，s 扫 0..W-40）
残差全为常数、无任何最优偏移 → 转场期间首页内容从未出现在屏幕上。

### 4.2 t3 —— pop/返回（同档位）

`idx053 chg=60458` 之后**连续 17 帧递减**（59039 → 34205 → … → 1154 → 411），
两层同时可见且在动 → **exit 转场一直正常**。

### 4.3 t4 —— push（fade+std+500，对照）

`idx011` 硬切 → 白屏；`idx044` 硬切 → 页面瞬间到位；`idx044-068` 的「渐变」经拼图确认是**图片逐行加载**。
→ fade 同样不可感知，**与 animType 无关**。

### 4.4 t5 —— push 我的 → 设置（决定性反例）

```
idx017  chg= 7673
idx019-037   连续 19 帧，3234 → 5143 → 5511 → … → 2597 平滑递减
idx038        0
```

拼图目视：**「我的」页整体左移，白色区域从右侧进入，「设置」页从右滑入，两层同框**，
与 `rin`/`lout` 的设计完全一致。

→ **`lout` 和 `rin` 都在正常工作。「一帧白屏」不是动画实现缺陷。**

### 4.5 t7 —— HWUI 帧统计 A/B

见 §0.2。详情页 95th = 85ms；设置页 95th = 32ms。

### 4.6 窗口透明性

`dumpsys window windows`：两个 Activity 窗口均 `fmt=TRANSPARENT`，
`windowBackground=@android:color/transparent` → 排除遮挡。

---

## 5. 修复（已实施）

### 5.0 为什么不是「让详情页首帧变快」

上一版把根因写成「进入页首帧太重」，方向是去优化详情页的加载。**这条路走不通**：详情页要发请求、
解析、布局，重是它的固有属性；而且只要还有任何一个重页面，问题就会在那一页复发。
真正该修的是**动画的驱动方式**——内容级动画的成败不该取决于业务页面的首帧耗时。

### 5.1 判据：动画过程会不会露出窗口之外的东西

| 类型 | 会不会露缝 | 通道 |
|---|---|---|
| `slide`（纯位移） | **不会**。两窗口位移恒互补，旧页右边缘 = 新页左边缘，像素级严丝合缝 | **window 级** |
| `none` | 无动画 | window 级（传 0） |
| `fade` | 会。两个半透明窗口交叉淡化，叠加后仍透出桌面 | 内容级 |
| `parallax` | 会。窗口缩放后四周露出桌面，四角又顶在屏幕圆角外 | 内容级 |

所以 `useWindowAnim = (type == slide || type == none)`，其余留在内容级。

### 5.2 改动清单

| 文件 | 改动 |
|---|---|
| `util/TransitionAnim.kt` | 新增 `useWindowAnim`；`enterOptions`/`enterOptionsCompat` 在 window 级下直接 `makeCustomAnimation(ctx, res("rin"), res("lout"))` 且**不再置 `enterPending`**；新增 `applyWindowExit()`；`startExit()` 在 window 级下直接 `return false`；`refreshBackdrops()` 按模式分流；`register()` 在 window 级下补一次垫底色 |
| `ui/base/BaseActivity.kt` | `finish()` 里 `super.finish()` 之后补 `TransitionAnim.applyWindowExit(this)` |
| `ui/base/BaseViewActivity.kt` | 同上 |

配套要点：

- **垫底色的分流**（`refreshBackdrops`）：window 级**连栈顶页一起垫**——位移不露缝，垫它是为了兜住
  「内容视图没铺满窗口」那一条：详情页 `contentView` 只有 **441** 高（窗口 480），两层都归位后
  底部那 39px 会直接透到下层页面上去。内容级模式反过来，栈顶必须保持透明才能两层同框。
- **半透明浮层页任何模式都不垫**：`ReplyActivity` 的 `pageBackground = false`（`AppThemeTranslucent`），
  给它垫不透明底色会把它下面那页整个盖死。
- **`none` 顺带被修正**：以前 `type == none` 只是新页不播，旧页的 `lout` 还会照播一遍（内容级、
  同样被卡顿吞掉），所以实测「`none` 与 `slide` 的白屏时长一样」（34 vs 33 帧）。现在 `none`
  走 `makeCustomAnimation(ctx, 0, 0)`，两层都真正不动。
- **两个调用点不用改**：`IntentUtil.kt:24` 与 `ReplyActivity.kt:900` 都只调 helper。
- **窗口级是改造前的既有通路**：`ReplyActivity` 这类浮层页在 window 动画时代（`6c2ce299` 及更早）
  本来就走的这条路，不是新引入的风险。

### 5.3 验收

```powershell
adb shell "dumpsys gfxinfo com.example.c001apk reset"
adb shell "input tap 160 340"; Start-Sleep 3
adb shell "dumpsys gfxinfo com.example.c001apk" | Select-String "Janky frames|95th|99th"
```
抽帧法（§6）判据：push 的 `idx` 序列里出现 **≥10 帧连续、数量级 2000~6000 px 的递减变化**。
注意 **gfxinfo 仍会显示卡顿**——那是对的，window 级动画的卖点就是「页面照旧卡，动画照样顺」，所以
这条改动**不能**用 gfxinfo 的 Janky 百分比当验收指标，只能看抽帧。


---

## 6. 二次验证清单（改完必跑）

```powershell
# 1) 进转场，抓帧统计
adb shell "dumpsys gfxinfo com.example.c001apk reset"
adb shell "input tap 160 340"; Start-Sleep 3
adb shell "dumpsys gfxinfo com.example.c001apk" | Select-String "Janky frames|95th|99th|Missed Vsync"

# 2) 录屏抽帧，看 push 是否出现连续多帧位移
adb shell "screenrecord --time-limit 8 --bit-rate 20000000 /sdcard/t.mp4"
# ffmpeg -fps_mode passthrough 抽帧 → python an5.py <dir>
```

判据：`idx` 序列里出现 **≥10 帧连续、数量级 2000~6000 px 的递减变化**，即动画可见。

---

## 7. 环境踩坑（下次直接用）

1. **PowerShell `>` 重定向出 UTF-16**：读文件要先判 BOM，且会混入 `#< CLIXML` 尾巴 → `.split("#< CLIXML")[0]` 截断。
2. **`run-as cp /sdcard/x` → Permission denied**（scoped storage）→ 走 `/data/local/tmp` 中转 + `cat >`。
3. **PowerShell 内联 `python -c "..."` 引号会被剥掉** → 一律写成 `.py` 文件再跑。
4. **`Start-Process powershell -Command "... $a ..."` 变量不展开** → 外层直接 `& $adb ...`。
5. **ffmpeg 抽帧用 `-fps_mode passthrough`**，`-vsync 0` 会丢帧。
6. **`dumpsys SurfaceFlinger --latency` 的层名要剥掉行首 handle**；
   本机这条命令取不到有效数据（全 0 / 空），**改用 `dumpsys gfxinfo` 更省事**。
7. `input tap` 坐标要用 `uiautomator dump` + `bounds.py` 取，别猜。
8. 本机 **没有 Android SDK build-tools，跑不了 Gradle**，编译验证交给 CI。

---

## 8. 操作记录（本次排查 + 修复）

按时间顺序，便于回看每一步是怎么收敛的：

| # | 操作 | 结果 |
|---|---|---|
| 1 | `git log --oneline` 看 debug3 历史 | 找到 `6c2ce299`（用户说的初始版本）→ `093148bf` 分界线 |
| 2 | 读 `TransitionAnim.kt` 全文（394 行） | 拿到 `playEnter` / `animateContent` / `runOnFirstFrame` / `refreshBackdrops` 全貌 |
| 3 | 读 `exp_slide_std_400_{rin,lin,lout,rout}.xml` | `rin` 100%→0、`lout` 0→-100%，同曲线同时长，**位移互补，资源本身没问题** |
| 4 | 读原始 `right_in/left_out/left_in/right_out.xml` | 同结构，只是曲线/时长不同 → 排除资源回归 |
| 5 | 搜 `consumeEnter`/`startExit`/`playEnter` 调用点 | 落在 `BaseActivity` / `BaseViewActivity` 的 onCreate / onResume / finish |
| 6 | 搜 `enterOptions` 调用点 | 只有 `IntentUtil.kt:24` 与 `ReplyActivity.kt:900`，都走 helper |
| 7 | 搜 `ReplyActivity` 的 `pageBackground` | = `false`（`AppThemeTranslucent`、全屏半透明）→ 垫底色时必须跳过 |
| 8 | 改 `TransitionAnim.kt` | `useWindowAnim` 分流 + `applyWindowExit` + `refreshBackdrops` 分流 + `register` 补垫 |
| 9 | 改两个基类 `finish()` | `super.finish()` 后补 `applyWindowExit` |
| 10 | `read_lints` 三个文件 | 0 diagnostic（注意本机无 SDK，属假绿灯，真正验证交 CI） |

更新前的设备侧实验（t2~t7、gfxinfo A/B、反解位移）见 §4，那是收敛到「驱动方式」这个结论的依据。


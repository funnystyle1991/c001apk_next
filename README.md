# c001apk_next

fake coolapk

[![装机量](https://service.houlangs.cn/c001apk/stats.php?format=svg&metric=installs)](https://service.houlangs.cn/c001apk/stats.php)
[![今日活跃](https://service.houlangs.cn/c001apk/stats.php?format=svg&metric=active)](https://service.houlangs.cn/c001apk/stats.php)
[![今日新增](https://service.houlangs.cn/c001apk/stats.php?format=svg&metric=new)](https://service.houlangs.cn/c001apk/stats.php)

![装机量历史](https://service.houlangs.cn/c001apk/stats.php?format=svg&metric=chart)

## 下载 / 更新

最新正式版：**[release-latest](https://github.com/kongwufang/c001apk_next/releases/tag/release-latest)**（正式版与测试版共用同一 versionCode，可互相覆盖安装）

App 内「设置 → 关于」可检查更新：更新检查走自建接口，支持多线路下载。

上面的徽章与曲线来自自建统计页，30 分钟刷新一次。统计只按**匿名随机 ID** 计（客户端自造，与账号、设备号都无关，一次安装一个）：超过 60 天没有请求的 ID 视为已卸载，从装机量里扣除；请求量不作为指标。

## 关于本项目 / Origin

本项目基于上游两个开源项目改编，沿用 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布，详见 [`LICENSE.md`](./LICENSE.md)。

代码主干血统（lineage）：

```
bggRGjQaUbCoE/c001apk-flutter  (Flutter 重写，已归档)
        │
        ▼
RzdPai/c001apk                (Kotlin 重写，本项目代码主线)
        │
        ▼
kongwufang/c001apk  ──fork──>  kongwufang/c001apk_next  (本仓库)
```

- 上游 Kotlin 主线：[RzdPai/c001apk](https://github.com/RzdPai/c001apk)
- 上游 Flutter 重写（已归档）：[bggRGjQaUbCoE/c001apk-flutter](https://github.com/bggRGjQaUbCoE/c001apk-flutter)

如上游更新，本项目会视情况进行同步（rebase/merge）。所有上游版权与许可声明均完整保留在 `LICENSE.md`。

---

Token/LoginUtil from: [CoolbbsYou](https://github.com/WaitFme/CoolbbsYou)


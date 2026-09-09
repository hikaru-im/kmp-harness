# References 使用说明

`references/` 用来放置 DSH、Cordis 等上游项目的本地只读副本，供源码对照和协议验证使用。它被根仓库的 `.gitignore` 忽略，不参与 Harness 的产品构建，也不应在这些目录里直接开发功能。

当前常用的参考仓库是：

| 目录 | 上游 | 默认分支 |
| --- | --- | --- |
| `references/deepseek-harness` | `https://github.com/deepseek-ai/deepseek-harness.git` | `master` |
| `references/cordis` | `https://github.com/cordiverse/cordis.git` | `main` |

## 更新参考仓库

脚本会自动发现 `references/*/.git`：

```bash
./scripts/update-references.sh
```

默认行为是抓取远程更新，显示每个仓库的 ahead/behind 状态，并逐个询问是否执行 `git pull --ff-only`。只查看本地版本、不访问网络时使用：

```bash
./scripts/update-references.sh --check
```

确认所有参考仓库都只需要快进时，可以自动更新：

```bash
./scripts/update-references.sh --pull
```

脚本不会强制覆盖本地提交；检测到分叉或 detached HEAD 时会跳过并报告。

## 初始化参考目录

`references/` 是可选目录。新环境可以按需要克隆：

```bash
mkdir -p references
git clone https://github.com/deepseek-ai/deepseek-harness.git references/deepseek-harness
git clone https://github.com/cordiverse/cordis.git references/cordis
```

如果只需要其中一个参考项目，只克隆对应目录即可。删除本地参考仓库不会影响主仓库；之后重新执行上面的 `git clone` 就能恢复。

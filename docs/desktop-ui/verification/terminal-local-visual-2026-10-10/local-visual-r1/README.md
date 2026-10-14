# 本地终端紧凑视觉 R1 证据

入口：acceptance.md / acceptance.json。6 类 35/35 与完整 JVM build 通过，12 终端完整页面图及 80 壳层图已核验。

source-manifest.json 记录 2,253 编译输入，source-before/source-after 保存本批 3 文件。完整冻结源码及构建仍在 manifest 所列 /tmp 路径；本目录不是完整仓库快照。XML、原始命令和环境记录供核查，脚本保留当时绝对路径及已有 parent helper 依赖。

asar-source 保存 6 个原版 asset 及 size/offset/SHA；分析与合同分清原版证据及 Harness 适配。documentation-before/after 和 documentation-sync.json 保存计划同步前后证据。

不归档 native-tmp 与 worker-jsonl；原始文件仍在 /tmp/harness-terminal-local-visual-evidence-20261010。dispatch、exit、prompt、delivery、stderr 与工作者末消息已归档。

archive-sha256.json 覆盖其生成时的全部归档文件，排除自身及其后生成的 archive-sha256-verification.json，以避免自引用。父线程另行逐文件读取核验。

原生窗口、系统 IME、Remote、原版像素与其他平台 launch 未验收。总目标继续 active，历史 R5 184/184 与本批数量分别记录。

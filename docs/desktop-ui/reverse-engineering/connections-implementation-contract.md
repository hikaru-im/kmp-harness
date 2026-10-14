# Connections 分析与下一批实施契约（2026-10-07）

状态：页面、真实数据集成和共享层测试已实现；桌面交互与截图正在验收。委托模型为SHUAIAPI/gpt-6.1-sol。证据来源与归档SHA见models-connections-analysis-2026-10-07.md。

## 原版组成与动作

remote-connections-settings-d2dabff157cf.js混合了三类功能，需要按用户范围裁剪：

| 区域 | 原版结构/动作 | 本项目边界 |
| --- | --- | --- |
| 本机/设备远控 | Control other devices、Control this Mac/PC；配对、授权、发现、撤销、保持唤醒 | 属于排除的电脑操控，不复刻入口、配对或授权流程 |
| SSH / WSL | segmented view → header/add → loading、empty或列表 → details/editor；refresh、发现SSH、保存受管理SSH配置、连接开关 | 保留编程Host连接视觉及能力说明；当前没有SSH/WSL adapter，不宣称可连接、不添加假的连接对象 |
| 详情 | Host、Alias、Port、Identity、Version；错误详情、登录、安装/更新 | 只展示真实Harness协议DTO提供的字段；无OS/arch、端口或版本来源时不填示例 |

已直接检查的UTF-8字符偏移：SSH header160505/160677、loading161353、empty161598；details Alias108290、Host108492、Port108715、Identity108933。偏移用于定位已提取JS，不能当作ASAR byte offset。

已发现宿主动作名：discover-remote-ssh-connections、refresh-remote-connections、refresh-remote-control-connections、save-codex-managed-remote-ssh-connections。它们是原版IPC动作名，不能调用成Harness已有API。

editor draft：displayName与sshHost必填并trim；alias模式使用发现来源和alias派生hostId；hostname模式为受管理对象生成/保留hostId；可选port仅整数且1..65535；identity模式必须有路径；名称与现有连接重复时报错。没有密码/API key字段。SSH编辑器待真实adapter契约之后实施，不能只做可保存却无法使用的示例表单。

## 当前Harness能力

- HostDescription仅包含hostId、displayName、protocolVersion和capabilities；capabilities不等于用户权限，不证明远端服务全部可用。
- Desktop的LocalConnection.host.describe()返回真实本机description；当前Desktop默认没有RemoteConnectionOwner。
- RemoteConnectionOwner按已认证account/tenant调用/app-api/harness/hosts发现Relay Host；select启动受监督连接，status含IDLE/DISCOVERING/CONNECTING/ONLINE/RECONNECTING/DISCONNECTED，connection发布后才表示握手完成。
- 切换Host会丢弃旧订阅；UI必须说明切换作用域，不能提前把候选Host标为当前在线连接。
- RemoteHarnessApp已传当前账号identity对应的status/discovery/hosts/select/refresh；Settings Connections与Hosts路由使用同一页面与真实数据源，旧HostScreen已移除。独立flow收集带identity门控，尚不构成完整原子事务快照保证。

## 页面与状态契约

复用SettingsFormFrame/Section/Card、HarnessButton、Search、Dialog、反馈控件，普通/紧凑自适应；主列上限728dp、水平16dp padding。当前批不增加动态UI/JS。

页面：标题连接 → 当前连接卡（本机/Relay、真实名称与协议、状态、刷新）→ 已发现编程Host列表（空/错误/重试、选择与详情）→ SSH未接入说明。没有远控、Cloud computer、浏览器、团队或云端环境入口。

建议typed页面状态只承载真实host/currentHost、连接生命周期、busy/error、动作可用性；不以示例列表作默认。操作回调缺失时禁用并给原因。详情只读可由UI持有选中hostId，所属对象变化后关闭/更新；不能复制不存在的数据。

刷新：本机调用host.describe并校验协议；Relay调用当前账号scope的discover。取消必须传播；一般错误显示简短可重试状态；重复刷新被阻止；scope变化后旧响应不可发布。选择：调用现有owner.select，CONNECTING期间不把目标冒充已在线；失败保持真实状态并允许重试。页内导航与Hosts页保持同一数据源。

## 实施写集与验收

建议下一次Agent仅写SettingsConnectionsPage.kt及必要局部UI helper；主线程先稳定状态DTO、负责HarnessApp/RemoteHarnessApp集成与测试，避免共享文件并行修改。没有具体稳定API前不启动依赖页面委托。

验收覆盖真实本机describe、Relay discovery/select边界、loading/empty/error/retry、无权限/无adapter、取消及scope变化、普通/紧凑浅深主题、详情关闭、按钮鼠标操作与导航返回。截图数据仅fixture；不修改用户真实账号/SSH配置，原图不足时仅报告结构与交互验证。

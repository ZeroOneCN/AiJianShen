# 私人健身教练（YuanLian）

一个不依赖任何境外服务的本地训练 App。纯 Java + XML，45 个类约 13 800 行，
运行时零第三方依赖，本地完整构建 APK 约 **168 MB**（其中约 140 MB 是 1324 段
640px 循环演示、34 MB 是 960px 静态图），素材全部打进安装包，装完即用、不下载任何资源。

没有云端账号，也不访问公网。用到的两个「对外」能力都是本地闭环：蓝牙直连体脂秤、
同一 Wi-Fi 下手机与手机点对点同步。

| | |
|---|---|
| 版本 | 1.6（versionCode 6） |
| 包名 | `com.yuanlian.app` |
| 系统要求 | Android 8.0+（minSdk 26），targetSdk 36 |
| 动作库 | 1324 个动作 · 10 个部位 · 力量 / 拉伸 / 有氧 |
| 安装包 | 168 MB，单 APK，无原生库，任何机型通用 |

<p>
<img src="screens/01-home.png" width="180" alt="首页">
<img src="screens/02-discover.png" width="180" alt="发现">
<img src="screens/07-library.png" width="180" alt="动作库">
<img src="screens/09-workout.png" width="180" alt="训练">
<img src="screens/11-result.png" width="180" alt="完成">
</p>

全部 14 张真机截图（含体重、体脂秤、手机同步）见 **[screens.html](screens.html)**，
用浏览器直接打开即可。

## 安装

从构建产物直接安装，无需任何额外资源包或联网首次启动。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 包内已含 `assets/img` 1324 张静态图、`assets/anim` 1324 段循环动画、
  字体与动作数据（`exercises.json` 659 KB、`boxes.json` 40 KB），
  不需要另外拷素材到 `/sdcard`。
- 当前是 **debug 签名**（`CN=Android Debug`），可正常安装、分享、分发；
  若需上架应用市场，需另配 release keystore 重新签名。
- 覆盖安装不会丢数据（全部数据在 `SharedPreferences` 内）。

## 功能

- **首页**：定制计划卡（未生成时是向导入口）、今日训练大卡、连续天数 / 本周完成 /
  累计分钟、七天安排（52dp 缩略图，训练日显示动作数 × 轮数 × 分钟，恢复日显示拉伸数）
- **动作库**：1324 个动作，10 个部位筛选，中英文名与肌群均可搜索
- **详情**：白底无缝的循环演示、部位 / 目标肌群 / 类型 / 器械标签、分步中文要领、
  底部固定「练这个动作」
- **训练**：第 r/R 轮 · 第 x/N 个动作的进度标签（休息时改写为「第 r/R 轮 · 组间休息」，
  不会把休息当成一个动作去计数）、按计划时长设置动作与休息秒数、卡片描边倒计时、
  暂停 / 跳过、完成自动记录；训练中可换动作、看动作要领、控制音乐
- **结果页**：完成时长 / 消耗 / 动作数、本周进度、感受记录、当次体重与 BMI、
  顺手设提醒，可一键再做一次
- **训练设置**：预备倒计时（关 / 3 / 5 / 10 秒）、休息时长（跟随计划或 10–60 秒）、
  提示音、振动、语音口令（系统 TTS）、背景音乐（从本机选择，复制进应用内，
  不依赖外部文件也不上传）
- **报告**：运动 / 消耗 / 分钟总览、本周柱状图、连续天数与日历详情、每日训练明细、
  体重曲线与 BMI、公斤 / 斤单位切换、手动补记分钟数
- **提醒**：每天定时本地通知，可先发测试通知；通知权限在页面里直接引导授权
- **定制**：目标 / 水平 / 频率 / 时长 / 器械 / 重点，生成并持久化一周计划
- **体重与身体成分**：当前体重卡、身高 / 年龄 / 性别、4 宫格主指标 + 14 项细分指标、
  测量记录；可蓝牙称重、手动记录，或从另一台手机同步
- **连接体脂秤**：扫描 → 连接 → 实时读数 → 稳定后自动保存；没有阻抗时只记体重与 BMI
- **手机间同步**：开 / 关本机同步服务、填对方地址、推送 / 拉取，页面显示上次同步结果
- 数据全部存在本机

### 权限

| 权限 | 用途 |
|---|---|
| `BLUETOOTH_SCAN`（`neverForLocation`）/ `BLUETOOTH_CONNECT` | Android 12+ 扫描并连接体脂秤 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Android 11 及以下的对应权限 |
| `ACCESS_FINE_LOCATION`（`maxSdkVersion=30`） | Android 11 及以下扫描 BLE 的系统要求 |
| `INTERNET` | 只用于手机间同步的局域网 HTTP，`networkSecurityConfig` 放行明文 |
| `POST_NOTIFICATIONS` | 训练提醒的本地通知 |

不开同步服务、不填对方地址时，应用不会发起任何网络请求；也没有任何域名或云端地址硬编码。

## 为什么是重写而不是改包

原目标 App（`homeworkout.homeworkouts.noequipment`）的启动链路依赖 Google UMP 同意 SDK
与广告/计费服务，在国内网络下初始化抛异常，被其 `catch (Throwable) → killProcess()`
兜底逻辑静默杀死，表现为「点了图标就闪退」。本项目从零实现，不引入任何此类依赖。

**设计语言借鉴的边界**：圆角尺度、间距节奏、配色属于公开设计模式，予以借鉴并对齐到规格；
原 App 的图标、动作插画、动画、文案均未使用。

样本 `base.apk` 是 Play 的 split base，缺 `base__abi` / `base__density`，单独
`adb install` 会报 `INSTALL_FAILED_MISSING_SPLIT`；base 内也没有 `liblpcore.so`。
因此没有用原包做运行时对照，设计规格来自 apktool 反编译出的 504 个 layout 与资源表。

## 设计规格（从样本 APK 资源里量出来的）

| 项目 | 值 | 出处 |
|---|---|---|
| 底色 | `#F8F8F8` | `colors.xml / bgColor` |
| 卡片 | `#FFFFFF` | — |
| 主色 | `#004AFF` | `colorAccent` |
| 主渐变 | `#0075FF → #004AFF`，横向 0° | `gradient_start` / `gradient_end` |
| 强调块渐变 | `#0B9EFF → #004AFF` | `bg_widget_streak_gradient` |
| 主文字 | `#1C212C` | `colorDark` |
| 次级文字 | `#888888` | `gray_888` |
| 大卡圆角 | 22.5dp | `r30 × 0.75` |
| 卡片圆角 | 18.75dp | `r25 × 0.75` |
| 胶囊 | 全圆角 | `bg_round_gradient_r25_ripple` |
| 正文字号 | 14 / 13 / 12sp | 布局中频次最高的三档 |
| 字体 | Outfit（SIL OFL，可商用） | `assets/fonts/outfit.ttf` |

关键细节：它的 `dp_*` 尺寸表被整体缩放了 **0.75**，所以 `r30` 实际是 22.5dp 而不是 30dp。
照名字抄会得到偏大的圆角——这是「看着像但总觉得不对」的常见原因。

### 两个平台差异带来的坑

- `ListView` 会丢弃 item XML 上的 `layout_margin`（走 `AbsListView.generateLayoutParams`，
  只读宽高），卡片间距只能用 `dividerHeight` 实现——动作库的 12dp 间距就是这么来的
- 往纵向 `LinearLayout` 里 `addView(child)` 默认参数是 **MATCH_PARENT 宽**，
  父容器 `setGravity(CENTER)` 对撑满宽度的子 View 无效，要居中的文字得显式给
  `WRAP_CONTENT` + `Gravity.CENTER`
- 子 View 会盖住父容器的圆角，须配合 `setClipToOutline(true)` 才能裁进圆角内

## 动作演示：矢量化重制

数据集提供的缩略图与 GIF 原生只有 **180×180**。旧版 320/360 输出是裁边后的 LANCZOS
插值放大，近看会有明显软边。1.5 版把每一帧重新制作为：

1. 按所有帧的并集包围盒裁掉白边，留 8% 边距、补成正方形
2. 静态图先做 3×、动画帧先做 2× LANCZOS 预放大，再交给 vtracer 描成 SVG
3. 用 resvg 渲染到静态图 **960×960**、动图 **640×640**；线条和色块来自矢量路径，
   放大看边缘不再有插值糊边
4. 960 静图会放大描摹分层产生的色带，所以用 `layer_difference=1` 的高保真参数，
   渲染后加 0.5px 柔化；640 动画帧保留更省时的参数，运动中也看不出色带
5. 动图保留原 GIF 的逐帧时长（首尾各 1s + 每帧 100ms）与循环标记；
   WebP 以 q84 / q58 编码，`noCompress` 保证 `ImageDecoder` 可随机访问

矢量化不是凭空补细节：180px 源图没有的高频信息不会出现，它只是把插值糊边换成干净
路径并保留原笔触。要真正 4K 级细节，需要更高分辨率的授权原片。

- 动作条目、英文名、肌群、中文分步要领：`hasaneyldrm/exercises-dataset`（**MIT**）
- 缩略图与演示 GIF：**© Gym visual**（<https://gymvisual.com/>），已购授权，
  详情页与 README 均保留署名；授权素材不随本仓库分发

渲染（`PhotoView.java`）：

- API 28+ 走 `ImageDecoder` + `AnimatedImageDrawable`，无限循环；API 26/27 自动回退静态 WebP
- 素材本身是纯白底，卡片、弹窗、列表也全是纯白：去掉描边与渐变，图像和容器没有分界线，
  这就是「背景融合」的来源；训练页再套一层白色大卡，用描边进度圈住画面
- 列表用静态图并按视图大小采样解码；动图只在详情弹窗和训练页播放，滚动与内存都稳
- 训练页把倒计时进度画成**卡片描边**（`DashPathEffect` 沿圆角矩形路径走一圈），
  计时结束正好合口

### 主体框归一化（`assets/boxes.json`）

数据集里每张插画的人物占比并不一致：有的顶满画布，有的四周留大片白。直接
`fitCenter` 会出现「这一张人头很大、下一张缩成小图」的跳动。

`tools/build_boxes.py` 离线把 1324 组素材逐张扫一遍：静图取一次、动图抽最多 6 帧，
求「非白像素」包围盒的**并集**（只算静图会让动图的手脚跑出框），加 3.5% 内边距、
边长下限 0.32 防止过小的局部特写被放大到失真，最后归一化写进 `boxes.json`（40 KB）。
运行时 `PhotoView.fitContent()` 按框 fitCenter，同一套缩放规则对所有图生效，
所以列表缩略图、详情弹窗和训练页里的人物大小是一致的。

## 数据

**1324 个动作**（数据集全量，含器械），力量 1204 / 拉伸 72 / 有氧 48。
10 个部位：手臂 292 / 腿臀 227 / 背 203 / 核心 169 / 胸 163 / 肩 143 /
小腿 59 / 小臂 37 / 有氧 29 / 颈部 2。

其中 1095 条标注了器械（哑铃 294 / 绳索 167 / 杠铃 154 / 器械 81 / 弹力带 54 …），
229 条纯徒手。列表和详情直接标出器械（`哑铃`、`单杠`…），排每日计划时优先挑
真正不用器械的那一批，所以「打开就能练」的体验不受影响。

英文名按最长短语优先翻译成中文（`45°`、`v.2`、`二式/三式` 去重后加后缀），
分步要领直接用数据集自带的中文说明，没有机翻。

## 定制计划

首页的「按你的条件生成计划」让用户选 6 组条件，30 秒排好一周：

| 条件 | 选项 | 影响 |
|---|---|---|
| 目标 | 减脂 / 增肌 / 塑形 / 放松 | 动作类型、主题模板、组间休息 |
| 水平 | 新手 / 进阶 / 高手 | 每个动作 30 / 40 / 50 秒 |
| 频率 | 每周 2–6 练 | 训练日分散，其余日子是 5 个拉伸的恢复日 |
| 时长 | 10 / 15 / 20 / 30 分钟 | 换算成动作段数，再拆成 1–8 轮 |
| 器械 | 徒手 / 哑铃·弹力带 / 单杠·长凳 / 健身房 | 动作池过滤，徒手始终保留，可多选 |
| 重点 | 全身 / 核心 / 腿臀 / 胸背 / 肩臂 / 有氧 | 部位过滤，可多选 |

生成逻辑在 `Data.weekPlan`：按目标选主题模板（例如减脂的全身燃脂、间歇有氧），
按「时长 ÷ (动作秒数 + 休息秒数)」算动作段数，再拆成轮次与每轮个数；
按主题部位从动作池里无重复抽取，增肌且选了器械时优先排器械动作。
条件不变的一个自然周内结果稳定，换周或改条件才重新排列。计划只存本地
SharedPreferences，不需要账号、也不联公网。

## 体重与体成分

### 蓝牙直连体脂秤

秤是 AntAF 那套私有 GATT 协议，没有标准蓝牙体重服务，所以协议逐行移植在
`BleScale`（对应原来 Python 的 `scale_reader.py`）：

| 项 | 值 |
|---|---|
| 服务 / 写 / 通知 | `0xFFB0` / `0xFFB1` / `0xFFB2` |
| 配对 | 不需要，直接连 |
| 帧长 | 20 字节，包头 `0xAC`，包类型在 `data[18]` |
| `0xD0` 资料包 | 下发身高 / 年龄 / 性别，功能位 `0x03` 打开阻抗；15 秒未收到回包会重发 |
| `0xD5` 体重包 | 32 位大端，低 18 位是克数，`bit18-20` 是精度档位，`bit31` 是稳定位 |
| `0xD6` 阻抗包 | 同上编码，值即阻抗（欧姆） |

精度档位要靠 `division` 反推（5 档，各有取整 / 取偶规则），另外补一条「小数 ≥ 0.9
进位」的修正，否则 74.6 会显示成 74.5。稳定位落下后才落库，避免来回跳的中间值被记成
两条记录；去重窗口 30 秒，同一段称重过程只写一条。

秤只给体重和阻抗，18 项体成分在 `BodyComp` 里按 BIA 公式算：瘦体重起点 + 分龄体脂阈值
+ 内脏脂肪经验式 + 骨量 / 肌肉量分档 + Mifflin-St Jeor 基础代谢。公式、阈值与取整
规则与 AntAF 的 `body_composition.py` 逐项对齐，已用同一组输入对拍验证（75 kg / 175 cm
/ 30 岁男 → BMI 24.49、体脂 20.15%、肌肉 57.37 kg、水分 54.77%、骨量 2.96、
BMR 1699、去脂 59.88，两版完全一致）。

真机实测（vivo V2359A / Android 16）已经跑通：扫到 `AFU-WL-TZ-A1`
（`D0:5C:00:0C:CE:C2`），连接后资料包下发成功，先后收到体重包与阻抗包，落库为
`73.95 kg` / `562 Ω` —— 阻抗能上来就说明 `0xD0` 的功能位生效了。
要出体成分需光脚双脚站稳、保持到稳定位落下：只穿袜或中途下秤时只会收到体重包，
此时页面显示 `--` 且不写体成分。

### 手机间同步

换手机不用重新开始，也不需要云。两台手机连同一个 Wi-Fi：

1. A 机打开「手机间同步」→ 开启「本机同步服务」，页面显示 `http://<局域网 IP>:8766`
2. B 机在同一页填这段地址，点「拉取对方」
3. 反向搬数据就点「推送本机」，或者两台机各自反过来操作一次

传输是一个手写的极简 HTTP 服务（`LanServer`，端口 8766，只有 `GET /snapshot`、
`POST /merge`、`GET /ping` 三个入口），收发都在 `LanSync` 里。快照是一份紧凑 JSON：
训练日、每天分钟数、训练明细、体重、18 项体成分、身高年龄性别。

合并规则是「并集 + 去重」，重复同步不会产生重复记录：日期取并集、同一天分钟数取较大值、
训练明细按时间戳去重、体重按日期去重、体成分按时间戳去重；档案只在本机为空时才被填充。
服务挂在进程上而不是页面上，所以开好服务后可以放心切到另一台手机操作。

界面上的「上次同步」会写清这次到底新增了训练日 / 体重 / 体成分各几条
（`Store.MergeStats`），不会是含糊的「同步完成」。

真机验证用 `adb forward tcp:18767 tcp:8766` 加 curl 完成：`GET /ping` 回
`{"ok":true,"app":"yuanlian"}`，`GET /snapshot` 带回本机全部数据，`POST /merge` 首次回
`{"ok":true,"added":2,"days":1,"weights":1,"sessions":2}`，同一份再推一次各项全 0
（去重生效）。非本应用快照会被拒绝合并，避免误推别人的 JSON 把数据洗干净。

## 构建

```bash
export JAVA_HOME=D:/CodeDevelop/Env/jdk-17
export ANDROID_HOME=D:/CodeDevelop/Env/AndroidSDK
./gradlew assembleDebug
# 产物 app/build/outputs/apk/debug/app-debug.apk
```

工具链 Gradle 9.3.1 + AGP 9.1.0 + JDK 17，minSdk 26 / targetSdk 36。
动图播放依赖 API 28 的 `AnimatedImageDrawable`，API 26/27 自动回退为静态缩略图。

数据与媒体重建（需要把已授权的数据集仓库放在 `E:/Code/Gym`）：

```bash
python -m pip install pillow "vtracer==0.6.15" "resvg_py==0.5.0"
python tools/build_gym.py             # 翻译/整理 1324 条 → assets/exercises.json
python tools/build_media_vector.py    # 矢量化 → assets/img、assets/anim
python tools/build_boxes.py           # 主体框归一化 → assets/boxes.json（约 1 分 50 秒）
# 只重做某一类：
# python tools/build_media_vector.py --mode stills
# python tools/build_media_vector.py --mode anims
# python tools/build_media.py         # 旧版位图流水线，保留作回退
```

演示素材不随仓库分发（见 `.gitignore`）：克隆后必须先放好授权数据集并跑一次
`build_media_vector.py`，否则动作图与动图为空。

## 代码结构

```
app/src/main/java/com/yuanlian/app/
├─ Data.java / Exercise.java / PlanProfile.java   数据加载、动作模型、计划条件
├─ Store.java                                     SharedPreferences 全部读写
├─ MainActivity / DiscoverActivity / PlanActivity 首页、发现、定制
├─ LibraryActivity / ReplaceSheet                 动作库列表与换动作弹层
├─ WorkoutActivity / ResultActivity               训练与完成
├─ ReportActivity / DayDetailActivity / StreakActivity  报告、单日详情、连续天数
├─ BodyActivity / ScaleActivity                  身体成分与体脂秤
├─ BleScale.java                                  AntAF 私有 GATT 协议移植
├─ BodyComp.java                                  BIA 18 项体成分公式
├─ LanServer / LanSync / SyncActivity / SyncPrefs 手机间同步
├─ PhotoView / IconView / TimerRingView / WeightChartView / QuickChartView / RulerSlider
├─ Ui.java / UiList.java                         dp、圆角、颜色、列表工厂等工具
└─ Reminder* / Voice* / BootReceiver              提醒调度、TTS 与开机恢复
```

## 更新记录

**1.6** — 体重与体成分（蓝牙体脂秤、18 项 BIA 指标、测量记录）、手机间局域网同步、
应用更名「私人健身教练」、蓝底渐变自适应图标、连续天数详情、训练页统一纯白底、
动作库卡片间距与发现页「为你精选」圆角修正。

**1.5** — 动作素材矢量化重制（640 动画 / 960 静图）、主体框归一化、补齐原包交互。

**1.0** — 从零重写，取代依赖 Google UMP 的原 App，实现训练主链路与本地数据。

## 应用图标

蓝底 + 展臂展示肌肉的线条剪影，图形由项目作者提供。母版是矢量文件 [art/icon.svg](art/icon.svg)，
Android 端为 Adaptive Icon：背景是渐变 drawable，前景是 VectorDrawable（另带 Android 13
单色图标），系统按任意密度实时栅格化，不存在放大插值。

## 版权

- 应用图标：项目作者提供的 SVG 图形
- 动作条目 / 肌群 / 中文要领：`exercises-dataset`（**MIT**）
- 演示图与动图：**© Gym visual**（<https://gymvisual.com/>），已购授权；
  详情页与截图页保留署名字样；授权素材不随本仓库分发
- 字体 Outfit：SIL Open Font License
- 原目标 App 的图标、插画、动画、文案均未使用

# 王者荣耀农场助手 · 开发文档

> 本文档基于初始需求（`用户需求.txt`）编写，用于指导前后端开发。  
> 玩法规则见 [farm-gameplay-reference.md](./farm-gameplay-reference.md)，静态数值见 `data/csv/`。

---

## 1. 项目概述

### 1.1 产品定位

面向个人玩家的 **王者荣耀农场** 数据工具 Web 应用，提供：

| 能力 | 说明 |
|------|------|
| 静态数据查阅 | 展示农场升级、小摊、土地、农作物、培育度等官方数值表 |
| 农作物图鉴 | 以大版面展示每种作物的详细属性与配图 |
| 个人账号管理 | 录入并管理多个王者账号、区服下的农场档案 |
| 成熟时间计算 | 按不同浇水策略计算作物成熟所需时间 |

### 1.2 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3 + TypeScript + Vue Router + Pinia |
| 后端 | Spring Boot 3 + Java |
| 数据库 | MySQL（或 PostgreSQL，开发期可先用 H2） |
| 认证 | JWT 或 Session（网站账号登录） |

### 1.3 项目目录

```
farm-helper/
├── frontend/          # Vue 3 前端
│   └── public/
│       └── images/
│           └── crops/ # 作物图片（命名与 crops.csv name 一致），缺图用 crop-placeholder.svg
├── backend/           # Spring Boot 后端
├── docs/              # 文档
├── data/
│   ├── raw/           # 原始 xls
│   └── csv/           # 导出的静态数值表
└── scripts/           # 数据转换脚本
```

---

## 2. 功能需求

### 2.1 农场数据展示（需求 1）

在网站提供统一入口，展示以下 **只读** 数值表（数据来源于 `data/csv/`，由后端导入或启动时加载）：

| 页面 | 数据源 | 说明 |
|------|--------|------|
| 农场升级表 | `farm_levels.csv` | 等级、升级费用、所需经验、解锁内容 |
| 小摊表 | `stall.csv` | 小摊等级、升级费用、经验、所需农场等级、售价加成 |
| 土地表 | `land.csv` | 农田开垦 / 二级地升级费用、经验、所需等级 |
| 农作物数据表 | `crops.csv` | 解锁等级、价格、产量、售价、经验、收获时间等 |
| 农作物培育度表 | `cultivation.csv` | 各作物培育等级所需培育度与备注 |

**交互要求：**

- 表格支持排序、筛选、搜索（按作物名、等级等）。
- 数值展示保留原文格式（如 `1.2万`、`5%`、`30s`），后端入库时可同时存原始字符串与解析后的数值。
- 数据表页与图鉴页分离（见 2.2），本模块以 **紧凑表格** 为主。

**暂不纳入本模块的表（可后续扩展）：**

- `mutation_rates.csv`、`rewards.csv` — 可在图鉴或独立页补充。

---

### 2.2 农作物图鉴（需求 2）

与「农作物数据表」为 **不同入口**，侧重展示体验而非纯表格。

| 对比项 | 农作物数据表 | 农作物图鉴 |
|--------|--------------|------------|
| 布局 | 全表一览 | 卡片 / 详情大版面 |
| 信息密度 | 高 | 中，突出单作物 |
| 配图 | 无 | 每种作物配 **非写实风格** 插图 |
| 用途 | 查数值、对比 | 浏览、了解作物特性 |

**图鉴单页展示字段（建议）：**

- 作物名称、解锁等级、种子价格、产量、基础总售价、经验、收获时间
- 变异上限、变异英雄（若有）
- 培育度概要（链接或折叠展示培育度表对应行）
- 作物类型标签：普通 / 高农场币 / 英雄作物

**配图方案：**

- 图片存放目录：`frontend/public/images/crops/`
- 命名规则：`{作物名称}.png` 或 `{作物名称}.svg`，与 `crops.csv` 的 `name` 字段对应（如 `小麦.png`、`英雄作物·花.png`）。
- 图片工具：`frontend/src/utils/cropImage.ts`，提供 `getCropImage(cropName)` 函数——有对应图片时返回图片路径，无图片时返回默认占位图 `crop-placeholder.svg`。
- 默认占位图：`frontend/public/images/crops/crop-placeholder.svg`，绿色风格植物简笔画，标注"暂无配图"。
- 添加新作物图片：将图片放入 `frontend/public/images/crops/`，文件名与作物名一致，运行时自动识别。

---

### 2.3 个人账号管理（需求 3）

#### 2.3.1 账号层级模型

网站账号与游戏账号 **严格区分**，层级如下：

```
网站用户 (User)
└── 王者荣耀账号 (GameAccount)          # 可多个
    ├── 平台类型（可选）：安卓QQ / 安卓微信 / 苹果QQ / 苹果微信
    ├── 区号信息（可选，文本）
    └── 区服 (GameServer)               # 可多个
        └── 农场档案 (FarmProfile)      # 每个区服对应一个王者农场
```

| 实体 | 说明 |
|------|------|
| **网站用户** | 注册 / 登录本网站的账号，与王者无关 |
| **王者荣耀账号** | 用户名下的游戏账号标识（如昵称备注），可记录平台与区号 |
| **区服** | 该王者账号下的具体游戏区服（如「微信 1 区」） |
| **农场档案** | 某一区服上唯一的农场进度记录 |

#### 2.3.2 农场档案 — 首期字段

后续可扩展，首期建议记录：

| 字段 | 类型 | 说明 |
|------|------|------|
| 农场等级 | int | 当前等级 |
| 当前经验 | int | 累计经验（升级后按规则扣减） |
| 农场币 | bigint | 当前持有 |
| 小摊等级 | int | 默认 0 或 1（未解锁时为 0） |
| 已开垦农田数 | int | 1～24 |
| 二级农田数 | int | 0～16 |
| 备注 | string | 用户自定义 |

**首期不强制录入、但预留扩展：**

- 各作物培育度与等级
- 24 块农田的种植状态（作物、种下时间、浇水记录）
- 社交次数（祝福、被偷取）

#### 2.3.3 功能列表

| 功能 | 说明 |
|------|------|
| 注册 / 登录 / 登出 | 网站账号 |
| 王者账号 CRUD | 增删改查，支持平台类型、区号 |
| 区服 CRUD | 隶属于某个王者账号 |
| 农场档案 CRUD | 隶属于某个区服，一对一 |
| 数据隔离 | 用户只能访问自己的账号树 |

---

### 2.4 成熟时间计算（需求 4）

独立入口，用户选择作物（或收获时间 T），计算在不同浇水策略下的 **实际成熟时间**。

#### 2.4.1 支持的策略

| 策略 | 说明 | 相对标准时间 |
|------|------|--------------|
| **完全不浇水** | 自然成熟 | T |
| **种下时浇一次** | 仅播种后浇 1 次水 | T − T/12 |
| **勤奋浇水** | 种下 1 次 + 干涸后 2 次，各减 T/12 | T − T/4 = 3T/4 |
| **极限浇水** | 勤奋策略基础上，再等待 T/15 后浇 1 次直接成熟 | T − 4T/15 = 11T/15 |

> 公式依据见 [farm-gameplay-reference.md §7](./farm-gameplay-reference.md)。

#### 2.4.2 收获时间解析

`crops.csv` 中 `收获时间` 字段需解析为秒数：

| 原文 | 秒数 |
|------|------|
| 30s | 30 |
| 2min | 120 |
| 5min | 300 |
| 20min | 1200 |
| 1h | 3600 |
| 8h | 28800 |
| 16h | 57600 |
| 32h | 115200 |

#### 2.4.3 页面交互

- 选择作物（下拉 / 搜索）或手动输入标准收获时间。
- 展示四种策略的成熟秒数及可读格式（如 `6小时40分`）。
- 可选：输入「种下时间」，输出各策略的 **预计成熟时刻**。
- 计算逻辑 **纯前端即可完成**；若需保存计算历史，再走后端 API。

#### 2.4.4 极限策略 — 浇水时间轴（供实现参考）

设标准时间为 T，W = T/3：

1. t = 0：种下并浇水，剩余时间减少 T/12。
2. t = W：第一次干涸，浇水，减少 T/12。
3. t = 2W：第二次干涸，浇水，减少 T/12。
4. t = 2W + T/15：第三次浇水，作物成熟。

总耗时 = 2W + T/15 = 2T/3 + T/15 = **11T/15**。

---

## 3. 数据设计

### 3.1 静态游戏数据（只读）

由 `data/csv/` 导入，建议表名与文件对应：

| 表名 | 来源 | 主键建议 |
|------|------|----------|
| `crop` | crops.csv | id / name |
| `cultivation` | cultivation.csv | crop_id + level |
| `farm_level` | farm_levels.csv | level |
| `land` | land.csv | id |
| `stall_level` | stall.csv | level |
| `mutation_rate` | mutation_rates.csv | type（后续） |
| `reward` | rewards.csv | id（后续） |

**说明：** CSV 为当前数据快照，不代表游戏上限；设计表结构时预留扩展行。

### 3.2 用户业务数据

```text
users
  id, username, password_hash, created_at

game_accounts
  id, user_id, display_name, platform_type, region_code, created_at

game_servers
  id, game_account_id, server_name, created_at

farm_profiles
  id, game_server_id, farm_level, current_exp, farm_coins,
  stall_level, farmland_count, upgraded_farmland_count, note, updated_at
```

**约束：**

- `game_accounts.user_id` → `users.id`
- `game_servers.game_account_id` → `game_accounts.id`
- `farm_profiles.game_server_id` → `game_servers.id`，**一对一**
- 删除王者账号时级联删除区服与农场档案（或软删除，二期定）

### 3.3 平台类型枚举

```text
ANDROID_QQ | ANDROID_WECHAT | IOS_QQ | IOS_WECHAT
```

前端展示中文，数据库存英文枚举。

---

## 4. 系统架构

```text
┌─────────────┐     HTTPS      ┌─────────────┐
│  Vue 3 SPA  │ ◄────────────► │ Spring Boot │
└─────────────┘                └──────┬──────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
              用户业务库          静态游戏数据        静态资源
              (MySQL)            (同库或缓存)      (作物图片)
```

| 模块 | 前端路由（建议） | 后端 API 前缀 |
|------|------------------|---------------|
| 首页 | `/` | — |
| 数据表 | `/data/*` | `/api/static/*` |
| 图鉴 | `/encyclopedia`, `/encyclopedia/:id` | `/api/crops/*` |
| 成熟计算 | `/calculator/maturity` | 可选 `/api/calculator/*` |
| 账号管理 | `/account/*` | `/api/user/*`, `/api/game-accounts/*` |

---

## 5. API 设计（概要）

### 5.1 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 当前用户 |

### 5.2 静态数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/static/farm-levels` | 农场升级表 |
| GET | `/api/static/stall` | 小摊表 |
| GET | `/api/static/land` | 土地表 |
| GET | `/api/static/crops` | 农作物列表 |
| GET | `/api/static/crops/{id}` | 单作物详情（图鉴） |
| GET | `/api/static/cultivation` | 培育度表 |
| GET | `/api/static/cultivation?crop={name}` | 按作物筛选 |

### 5.3 个人账号

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/game-accounts` | 当前用户的王者账号列表 |
| POST | `/api/game-accounts` | 新增王者账号 |
| PUT | `/api/game-accounts/{id}` | 更新 |
| DELETE | `/api/game-accounts/{id}` | 删除 |
| GET | `/api/game-accounts/{id}/servers` | 区服列表 |
| POST | `/api/game-accounts/{id}/servers` | 新增区服 |
| GET | `/api/servers/{id}/farm` | 获取农场档案 |
| PUT | `/api/servers/{id}/farm` | 更新农场档案 |

所有 `/api/game-accounts`、`/api/servers` 接口需校验资源归属当前登录用户。

---

## 6. 前端页面结构

```text
布局 (Layout)
├── 顶栏导航
│   ├── 农场数据 ▾
│   │   ├── 农场升级表
│   │   ├── 小摊表
│   │   ├── 土地表
│   │   ├── 农作物数据表
│   │   └── 农作物培育度表
│   ├── 农作物图鉴
│   ├── 成熟时间计算
│   └── 我的账号
├── 主内容区
└── 页脚
```

### 6.1 组件划分（建议）

| 组件 | 用途 |
|------|------|
| `DataTable.vue` | 通用静态数据表格 |
| `CropCard.vue` | 图鉴卡片 |
| `CropDetail.vue` | 图鉴详情页 |
| `MaturityCalculator.vue` | 成熟时间计算器 |
| `AccountTree.vue` | 王者账号 → 区服 → 农场 树形管理 |
| `FarmProfileForm.vue` | 农场档案表单 |

---

## 7. 后端模块划分（建议）

```text
com.farmhelper
├── config          # 安全、CORS、JWT
├── controller
│   ├── AuthController
│   ├── StaticDataController
│   ├── GameAccountController
│   └── FarmProfileController
├── service
├── repository
├── entity
├── dto
└── util
    └── HarvestTimeParser   # 收获时间字符串 → 秒
```

**静态数据加载：** 应用启动时从 CSV 或 SQL 种子数据导入；提供管理命令或通过 `scripts/` 重新导入。

---

## 8. 开发阶段

### 阶段一：基础框架与静态数据

- [ ] 初始化 `frontend`（Vue 3 + Router + Pinia + UI 库）
- [ ] 初始化 `backend`（Spring Boot + 数据库）
- [ ] CSV 导入静态游戏数据
- [ ] 实现 5 张数据表的只读 API 与前端页面
- [ ] 实现成熟时间计算器（纯前端）

### 阶段二：图鉴

- [ ] 图鉴列表与详情页
- [ ] 作物配图资源与占位逻辑
- [ ] 关联培育度、类型标签展示

### 阶段三：账号体系

- [ ] 用户注册 / 登录
- [ ] 王者账号、区服、农场档案 CRUD
- [ ] 账号管理页面与数据隔离

### 阶段四：打磨与扩展（按需）

- [ ] 农田种植状态、浇水提醒
- [ ] 收益计算、升级规划
- [ ] 变异倍率表、奖励表展示

---

## 9. 非功能需求

| 项 | 要求 |
|----|------|
| 响应式 | 支持桌面端为主，移动端基本可用 |
| 语言 | 界面与提示使用简体中文 |
| 代码目录/文件名 | 使用英文 |
| 数据更新 | 替换 `data/raw/farm_data.xls` 后运行 `scripts/xls_to_csv.py`，再重新导入 |
| 安全 | 密码加密存储；API 鉴权；防越权访问他人农场数据 |

---

## 10. 相关文档

| 文档 | 路径 |
|------|------|
| 玩法规则 | `docs/farm-gameplay-reference.md` |
| 初始需求 | 项目根目录 `用户需求.txt` |
| 静态数据 | `data/csv/*.csv` |
| 数据转换脚本 | `scripts/xls_to_csv.py` |

---

*文档版本：v1.0*

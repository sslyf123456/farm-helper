# Farm Helper 后端 API 文档

> **Base URL**：`http://localhost:8080`  
> **协议**：HTTP/1.1  
> **数据格式**：JSON（UTF-8）  
> **认证**：当前版本无需认证（全部为公开的只读静态数据接口）

---

## 目录

1. [总览](#总览)
2. [静态数据接口 `/api/static`](#静态数据接口-apistatic)
   - [GET /api/static/farm-levels](#get-apistaticfarm-levels)
   - [GET /api/static/stall](#get-apistaticstall)
   - [GET /api/static/land](#get-apistaticland)
   - [GET /api/static/crops](#get-apistaticcrops)
   - [GET /api/static/crops/{name}](#get-apistaticcropsname)
   - [GET /api/static/cultivation](#get-apistaticcultivation)
   - [GET /api/static/mutation-rates](#get-apistaticmutation-rates)
   - [GET /api/static/rewards](#get-apistaticrewards)
3. [数据来源说明](#数据来源说明)
4. [通用错误码](#通用错误码)

---

## 总览

所有接口均挂载在 `/api/static` 路径下，数据在服务启动时从 `data/csv/` 目录一次性加载到内存，后续请求直接返回内存缓存，**无数据库依赖**。

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/static/farm-levels` | GET | 农场等级升级表 |
| `/api/static/stall` | GET | 小摊升级表 |
| `/api/static/land` | GET | 农田开垦表 |
| `/api/static/crops` | GET | 农作物列表（全量） |
| `/api/static/crops/{name}` | GET | 单个农作物详情 |
| `/api/static/cultivation` | GET | 培育度表（支持按作物筛选） |
| `/api/static/mutation-rates` | GET | 变异倍率表 |
| `/api/static/rewards` | GET | 等级奖励表 |

---

## 静态数据接口 `/api/static`

---

### GET /api/static/farm-levels

获取农场等级升级所需费用、经验及解锁内容。

**请求**

```
GET /api/static/farm-levels
```

无请求参数。

**响应示例**

```json
[
  {
    "level": 2,
    "upgrade_cost": "12",
    "required_exp": "5",
    "unlock_content": "解锁胡萝卜，可开垦农田数量3-4，解锁桃霞变异"
  },
  {
    "level": 3,
    "upgrade_cost": "50",
    "required_exp": "36",
    "unlock_content": "解锁番茄，解锁幽蓝变异"
  }
]
```

> 注：`upgrade_cost`、`required_exp` 在较高级别时含中文单位（如 `"1.3万"`、`"1.03亿"`），因此保持字符串类型。

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| level | number | 农场等级（目标等级，从 2 开始） |
| upgrade_cost | string | 升级所需金币数量（可能含中文单位） |
| required_exp | string | 升级所需当前经验值（可能含中文单位） |
| unlock_content | string | 升至该等级后解锁的内容描述 |

---

### GET /api/static/stall

获取小摊升级所需费用、经验及售价加成。

**请求**

```
GET /api/static/stall
```

无请求参数。

**响应示例**

```json
[
  {
    "level": 2,
    "upgrade_cost": "1000",
    "gain_exp": "150",
    "required_farm_level": 5,
    "price_boost": "5%"
  },
  {
    "level": 3,
    "upgrade_cost": "3000",
    "gain_exp": "360",
    "required_farm_level": 7,
    "price_boost": "10%"
  }
]
```

> 注：`upgrade_cost`、`gain_exp` 在较高级别时含中文单位（如 `"1万"`、`"1.01亿"`），因此保持字符串类型。

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| level | number | 小摊升级后的目标等级 |
| upgrade_cost | string | 升级所需金币（可能含中文单位） |
| gain_exp | string | 升级后获得的农场经验（可能含中文单位） |
| required_farm_level | number | 执行升级所需的农场等级下限 |
| price_boost | string | 升级后售价加成百分比（累计） |

---

### GET /api/static/land

获取农田开垦所需费用、经验及解锁条件。

**请求**

```
GET /api/static/land
```

无请求参数。

**响应示例**

```json
[
  {
    "land_index": "第4块",
    "reclaim_cost": "10",
    "gain_exp": "20",
    "required_level": 2
  },
  {
    "land_index": "第5块",
    "reclaim_cost": "100",
    "gain_exp": "50",
    "required_level": 4
  }
]
```

> 注：`reclaim_cost`、`gain_exp` 在较高级别时含中文单位（如 `"1.6万"`、`"1.07亿"`），因此保持字符串类型。

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| land_index | string | 农田编号（如"第4块"） |
| reclaim_cost | string | 开垦所需金币（可能含中文单位） |
| gain_exp | string | 开垦后获得的农场经验（可能含中文单位） |
| required_level | number | 开垦所需的农场等级下限 |

---

### GET /api/static/crops

获取全部农作物列表，包含种植和收获的基础数据。

**请求**

```
GET /api/static/crops
```

无请求参数。

**响应示例**

```json
[
  {
    "unlock_level": 1,
    "name": "小麦",
    "seed_price": 0,
    "yield_qty": 3,
    "total_sell_price": 3,
    "exp_gain": 1,
    "harvest_time": 30,
    "mutation_limit": 1,
    "mutation_hero": ""
  },
  {
    "unlock_level": 2,
    "name": "胡萝卜",
    "seed_price": 1,
    "yield_qty": 5,
    "total_sell_price": 10,
    "exp_gain": 6,
    "harvest_time": 120,
    "mutation_limit": 1,
    "mutation_hero": ""
  }
]
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| unlock_level | number | 解锁该作物所需的农场等级 |
| name | string | 作物名称（唯一标识，也用作 URL 路径参数） |
| seed_price | number | 种子购买价格（金币），部分高级作物可能为 null |
| yield_qty | number | 每次收获的产出数量 |
| total_sell_price | number | 收获后出售的总金币收益 |
| exp_gain | number | 收获后获得的农场经验 |
| harvest_time | number | 从种下到成熟的时长，以秒为单位的整数（如 30=30秒, 120=2分钟, 115200=32小时） |
| mutation_limit | number | 可同时变异的最大块数 |
| mutation_hero | string | 触发变异所需的英雄名，为空表示无需特定英雄 |

---

### GET /api/static/crops/{name}

获取单个农作物的详细信息。

**请求**

```
GET /api/static/crops/{name}
```

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 作物名称，需 URL 编码（如 `胡萝卜` → `%E8%83%A1%E8%90%9D%E5%8D%9C`） |

**响应示例（200 OK）**

```json
{
  "unlock_level": 20,
  "name": "蓝莓",
  "seed_price": 675,
  "yield_qty": 20,
  "total_sell_price": 9000,
  "exp_gain": 7142,
  "harvest_time": 115200,
  "mutation_limit": 3,
  "mutation_hero": "澜"
}
```

**响应示例（404 Not Found）**

```
（空响应体，HTTP 状态码 404）
```

**响应字段**

字段同 [GET /api/static/crops](#get-apistaticcrops)。

---

### GET /api/static/cultivation

获取培育度升级数据。支持按作物名称筛选，返回培育度各等级所需积分。

**请求**

```
GET /api/static/cultivation
GET /api/static/cultivation?crop={cropName}
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| crop | string | 否 | 作物名称，指定后只返回该作物的培育度记录 |

**响应示例（不带 crop 参数，返回全量）**

```json
[
  {
    "crop": "小麦",
    "total": 1,
    "lv2": 1,
    "lv3": null,
    "lv4": null,
    "lv5": null,
    "lv6": null,
    "lv7": null,
    "lv8": null,
    "lv9": null,
    "lv10": null
  }
]
```

**响应示例（带 crop=蓝莓，返回单条或空数组）**

```json
[
  {
    "crop": "蓝莓",
    "total": 12345,
    "lv2": 2345,
    ...
  }
]
```

> 若指定 `crop` 但未找到对应记录，返回空数组 `[]`。

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| crop | string | 作物名称 |
| total | number | 满级（10级）所需培育积分总计 |
| lv2 ~ lv10 | number \| null | 各等级所需的培育积分，null 表示无需额外积分（即跳级或不解锁） |

---

### GET /api/static/mutation-rates

获取变异类型及对应的品质与倍率说明。

**请求**

```
GET /api/static/mutation-rates
```

无请求参数。

**响应示例**

```json
[
  {
    "mutation_type": "绿光",
    "quality": "蓝色",
    "multiplier": 1.5,
    "unlock_level": null
  },
  {
    "mutation_type": "桃霞",
    "quality": "蓝色",
    "multiplier": 1.5,
    "unlock_level": 2
  }
]
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| mutation_type | string | 变异特效名称 |
| quality | string | 变异品质等级 |
| multiplier | number | 售价倍率（相对普通作物） |
| unlock_level | number \| null | 解锁该变异所需的农场等级，null 表示初始可用 |

---

### GET /api/static/rewards

获取各等级任务的金币与经验奖励。

**请求**

```
GET /api/static/rewards
```

无请求参数。

**响应示例**

```json
[
  {
    "level": "新手任务1",
    "gold_reward": "5.0万",
    "exp_reward": "4800"
  },
  {
    "level": "新手任务2",
    "gold_reward": "12.0万",
    "exp_reward": "7200"
  }
]
```

> 注：`gold_reward`、`exp_reward` 均含中文单位（如 `"5.0万"`、`"1.03亿"`），保持字符串类型。

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| level | string | 任务名称或等级阶段 |
| gold_reward | string | 完成后获得的金币数量（可能含中文单位） |
| exp_reward | string | 完成后获得的农场经验（可能含中文单位） |

---

## 数据来源说明

所有数据从 `data/csv/` 目录下的 7 个 CSV 文件中读取，CSV 由 `scripts/xls_to_csv.py` 从 `data/raw/farm_data.xls` 导出，导出时自动将中文表头映射为英文字段名。

| 文件 | 对应接口 |
|------|---------|
| `farm_levels.csv` | `/api/static/farm-levels` |
| `stall.csv` | `/api/static/stall` |
| `land.csv` | `/api/static/land` |
| `crops.csv` | `/api/static/crops`、`/api/static/crops/{name}` |
| `cultivation.csv` | `/api/static/cultivation` |
| `mutation_rates.csv` | `/api/static/mutation-rates` |
| `rewards.csv` | `/api/static/rewards` |

- 文件编码：UTF-8（含 BOM，服务自动去除）
- 数据加载时机：应用启动时 `@PostConstruct` 一次性加载，运行时为只读内存缓存
- 培育度表的备注列在 CSV 导出时已过滤，API 不会返回

---

## 通用错误码

| HTTP 状态码 | 说明 |
|------------|------|
| 200 OK | 请求成功，返回 JSON 数据 |
| 404 Not Found | 资源不存在（目前仅 `/crops/{name}` 会返回 404） |
| 500 Internal Server Error | 服务启动时 CSV 文件缺失或格式错误 |

---

*文档最后更新：2026-06-13*

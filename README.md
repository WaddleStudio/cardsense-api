# CardSense API

以情境式卡片比較為核心的 deterministic recommendation API。在指定消費情境下比較各張卡的有效回饋，回傳可解釋的推薦結果。

## 職責

**負責**：
- 提供信用卡推薦 REST API
- 在指定情境下比較卡片的有效回饋與適配度
- 確定性回饋計算與排序邏輯
- 可解釋結果與可追溯 `promoVersionId`
- 異步審計日誌
- 每筆回應包含法律免責聲明

**不負責**：
- 資料擷取或爬蟲（由 cardsense-extractor 負責）
- Schema 定義（由 cardsense-contracts 負責）
- 前端展示

## 技術棧

- Java 21
- Spring Boot
- SQLite（透過 repository abstraction）
- Maven

## 快速開始

### 使用 mock 資料

```bash
cd cardsense-api
mvn spring-boot:run
```

預設讀取 `src/main/resources/promotions.json`。

### 使用 SQLite（real extractor 資料）

```bash
# 先在 extractor 匯入 JSONL
cd ../cardsense-extractor
uv run python jobs/import_jsonl_to_db.py \
  --input outputs/esun-v5-full.jsonl \
  --db data/cardsense.db

# 啟動 API
cd ../cardsense-api
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
    -Dcardsense.repository.mode=sqlite \
    -Dcardsense.repository.sqlite.path=D:/alan_self/cardsense/cardsense-extractor/data/cardsense.db"
```

也可用環境變數：

```bash
CARDSENSE_DB_PATH=/path/to/cardsense.db mvn spring-boot:run
```

### 端點

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/health` | 健康檢查 |
| GET | `/v1/banks` | 銀行列表 |
| GET | `/v1/cards?bank=&status=&scope=` | 卡片目錄（`scope=CATALOG_ONLY` 拉 catalog 型卡片） |
| POST | `/v1/recommendations/card` | 情境推薦 |

## 設計重點

### 推薦流程

1. 解析 top-level request 與 nested `scenario` 為單一比較情境
2. 過濾不符合情境條件的 promotion（停發卡、零回饋、非 `RECOMMENDABLE` 不進榜）
3. 計算每筆 promotion 的有效回饋與封頂後回饋
4. 依 `comparison.mode` 收斂成 card-level score
5. 以 effective return 降序排序，tie-breaker：到期日 → 年費 → 銀行 / 卡片 / promoVersionId

### 比較模式

| Mode | 說明 |
|------|------|
| `BEST_SINGLE_PROMOTION` | 每張卡取最佳單筆 promotion 做排名 |
| `STACK_ALL_ELIGIBLE` | 依 `promotion.stackability` metadata 解出可並存組合，計算 card-level total return |

### 推薦規則

- `requiresRegistration` 讀取 request 的 `registeredPromotionIds`
- `frequencyLimit` / `maxCashback` 讀取 request 的 `benefitUsage`
- `conditions` / `excludedConditions` 使用結構化 condition object

### 回饋計算

| 型別 | 計算方式 |
|------|----------|
| `PERCENT` | 交易金額 × 回饋百分比，依規則取整 |
| `FIXED` | 直接視為固定回饋金額 |
| `POINTS` | 目前沿用 `PERCENT` 路徑，尚未引入銀行別折現規則 |

### Break-Even 分析

比較 `FIXED` 與 `PERCENT` 時的交叉點金額：`F / (r / 100)`。實務上還需考慮 `maxCashback` 封頂、`minAmount` 門檻、`benefitUsage` 額度消耗。

### Repository 模式

| 模式 | 設定 | 資料來源 |
|------|------|----------|
| mock（預設） | 無需設定 | `promotions.json` |
| sqlite | `cardsense.repository.mode=sqlite` | extractor 匯入的 `promotion_current` |

## 與其他子專案的關係

- **cardsense-contracts**：提供 recommendation request / response schema 與列舉定義
- **cardsense-extractor**：提供匯入 SQLite 後的 `promotion_current` 資料

### 跨 repo 工作流

1. 在 extractor 執行 real extraction 產生 JSONL
2. 匯入 SQLite
3. 啟動 API 驗證 recommendation / catalog 行為

## 已知限制

- SQLite repository 從 `raw_payload_json` 還原 `stackability` metadata，尚未拆成顯式欄位
- `POINTS` 尚未引入銀行別點數折現規則
- Break-even 目前只處理代表 promotion 間的 `FIXED` vs `PERCENT` 比較
- `STACK_ALL_ELIGIBLE` 仍為 heuristic aggregation，待 `stackability` 標註完整後升級為 deterministic stacking

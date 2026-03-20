# CardSense API

以情境式卡片比較為核心的 deterministic recommendation API。

## 本 repo 的職責
- 提供信用卡推薦 REST API
- 在指定情境下比較卡片的有效回饋與適配度
- 確定性回饋計算與排序邏輯
- 可解釋結果與可追溯 `promoVersionId`
- 異步審計日誌
- 每筆回應包含法律免責聲明

## 產品方向
CardSense 的主要產品目標不是做單筆 promotion 排名，而是：

- 在單一消費情境下接收使用者已知條件
- 先過濾不符合條件的優惠與卡片
- 比較各張卡在該情境下的有效回饋
- 回傳適合的卡片清單與可解釋原因

這裡的「情境」包含但不限於：

- 消費類別
- 消費金額
- 日期
- 地點
- 已登錄活動
- 已使用回饋額度
- 指定卡片範圍

## 目前實作模型
目前 recommendation engine 已經支援兩種 card-level comparison mode：

- `BEST_SINGLE_PROMOTION`：先過濾 eligible promotions，再用每張卡最佳單筆 promotion 做卡片排名
- `STACK_ALL_ELIGIBLE`：先過濾 eligible promotions，再把同卡所有 eligible promotions 加總成 card-level total return

這代表 API 已不再只是單筆 promotion 排名，而是以卡片作為排序單位；目前 shared contracts 已定義 `promotion.stackability` metadata，但 recommendation engine 尚未完整消化這份 metadata，因此 `STACK_ALL_ELIGIBLE` 仍屬過渡期實作。

## 目前推薦規則重點
- 停發卡與零回饋 promotion 不進榜
- 僅 `recommendationScope=RECOMMENDABLE` 的 promotion 會進入 recommendation engine
- `requiresRegistration` 會讀取 request 的 `registeredPromotionIds`
- `frequencyLimit` / `maxCashback` 會讀取 request 的 `benefitUsage`
- `conditions` / `excludedConditions` 使用結構化 condition object，而非 magic string

## 已知限制
- `STACK_ALL_ELIGIBLE` 雖然已經有 shared contract 層級的 `stackability` 設計，但目前 engine 尚未依 `relationshipMode/groupId/requires/excludes` 做完整 deterministic resolve
- `POINTS` 目前沿用 `PERCENT` 的計算路徑，尚未引入銀行別點數折現規則
- break-even analysis 目前只處理代表 promotion 間的 `FIXED` vs `PERCENT/POINTS` 比較

## 核心端點
- `POST /v1/recommendations/card`
- `GET /v1/cards`
- `GET /v1/banks`
- `GET /health`

`GET /v1/cards` 支援 `bank`、`status`、`scope` 三個 query params。`scope=CATALOG_ONLY` 可讓前端只拉出 catalog 型卡片，同時 recommendation engine 仍只會吃 `RECOMMENDABLE` promotion。

## Promotion Repository 模式
- 預設使用 mock repository，資料來源為 `src/main/resources/promotions.json`
- 若要讀取 extractor 匯入後的 SQLite DB，設定：

```properties
cardsense.repository.mode=sqlite
cardsense.repository.sqlite.path=/absolute/path/to/cardsense.db
```

也可用環境變數：

```bash
CARDSENSE_DB_PATH=/absolute/path/to/cardsense.db
```

SQLite 模式會讀取 `promotion_current` table，因此建議先在 extractor repo 執行 JSONL 匯入 job。

### 啟動前準備
1. 在 extractor repo 產生或更新 JSONL
2. 匯入 SQLite

```bash
uv run python jobs/import_jsonl_to_db.py --input outputs/esun-v4-full.jsonl --db data/cardsense.db
```

3. 啟動 API

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dcardsense.repository.mode=sqlite -Dcardsense.repository.sqlite.path=D:/alan_self/cardsense/cardsense-extractor/data/cardsense.db"
```

### Smoke Test
- `SqliteApiSmokeTest` 會以 Spring context 真正啟動 API，並驗證 SQLite repository 可被注入且 recommendation 可讀到 DB 資料。

## Recommendation Request Example
```json
{
  "amount": 1200,
  "category": "DINING",
  "cardCodes": ["CTBC_LINEPAY", "ESUN_ALLONE"],
  "registeredPromotionIds": ["ver_789"],
  "benefitUsage": [
    {"promoVersionId": "ver_789", "consumedAmount": 120}
  ],
  "location": "台北市信義區",
  "date": "2026-03-18",
  "scenario": {
    "amount": 1200,
    "category": "DINING",
    "date": "2026-03-18",
    "location": "台北市信義區",
    "channel": "ONLINE",
    "merchantName": "FoodPanda",
    "paymentMethod": "APPLE_PAY",
    "installmentCount": 1,
    "newCustomer": false,
    "customerSegment": "MASS",
    "membershipTier": "GOLD",
    "tags": ["WEEKEND", "APP_CHECKOUT"],
    "attributes": {
      "merchantGroup": "DELIVERY",
      "campaignCode": "SPRING-2026"
    }
  },
  "comparison": {
    "mode": "STACK_ALL_ELIGIBLE",
    "includePromotionBreakdown": true,
    "includeBreakEvenAnalysis": true,
    "maxResults": 5,
    "compareCardCodes": ["CTBC_LINEPAY", "ESUN_ALLONE"]
  }
}
```

說明：

- 舊版 top-level `amount/category/location/date` 仍保留，以維持相容性
- 若 `scenario` 同時提供對應欄位，推薦引擎會以 `scenario` 為準
- `comparison.mode` 決定是保守的單優惠比較，還是多優惠並存加總模式
- `comparison.includeBreakEvenAnalysis=true` 時，response 會附帶固定回饋與比例回饋的 break-even 摘要

## Recommendation Response Example
```json
{
  "requestId": "req_abc123",
  "scenario": {
    "amount": 1200,
    "category": "DINING",
    "date": "2026-03-18",
    "location": "台北市信義區",
    "channel": "ONLINE",
    "merchantName": "FoodPanda",
    "paymentMethod": "APPLE_PAY",
    "installmentCount": 1,
    "newCustomer": false,
    "customerSegment": "MASS",
    "membershipTier": "GOLD",
    "tags": ["WEEKEND", "APP_CHECKOUT"],
    "attributes": {
      "merchantGroup": "DELIVERY",
      "campaignCode": "SPRING-2026"
    }
  },
  "comparison": {
    "mode": "STACK_ALL_ELIGIBLE",
    "evaluatedPromotionCount": 42,
    "eligiblePromotionCount": 5,
    "rankedCardCount": 2,
    "breakEvenEvaluated": true,
    "breakEvenAnalyses": [
      {
        "leftCardCode": "CTBC_LINEPAY",
        "rightCardCode": "ESUN_ALLONE",
        "leftPromoVersionId": "ver_fixed_001",
        "rightPromoVersionId": "ver_percent_003",
        "breakEvenAmount": 1667,
        "variableRewardCapAmount": 2000,
        "leftMinAmount": 0,
        "rightMinAmount": 1000,
        "summary": "中國信託 示例網購卡 與 玉山 Example 卡的理論 break-even 約在 1667 元。"
      }
    ],
    "notes": [
      "多優惠並存模式目前採用 heuristic aggregation；若未來要精準上線，需在 promotion schema 補可疊加關係。"
    ]
  },
  "recommendations": [
    {
      "cardCode": "CTBC_LINEPAY",
      "cardName": "中國信託 示例網購卡",
      "bankCode": "CTBC",
      "bankName": "中國信託",
      "cashbackType": "PERCENT",
      "cashbackValue": 3.0,
      "estimatedReturn": 86,
      "matchedPromotionCount": 2,
      "rankingMode": "STACK_ALL_ELIGIBLE",
      "reason": "中國信託 中國信託 示例網購卡 — 2 個可命中的優惠合計預估回饋 $86 元；代表優惠為 3%，優惠至 2026-06-30",
      "promotionId": "promo_456",
      "promoVersionId": "ver_789",
      "validUntil": "2026-06-30",
      "conditions": [
        {"type": "MIN_SPEND", "value": "1000", "label": "最低消費 1000 元"},
        {"type": "REGISTRATION_REQUIRED", "value": "true", "label": "需登錄活動"}
      ],
      "promotionBreakdown": [
        {
          "promotionId": "promo_456",
          "promoVersionId": "ver_789",
          "title": "主優惠 3% 回饋",
          "cashbackType": "PERCENT",
          "cashbackValue": 3.0,
          "estimatedReturn": 36,
          "cappedReturn": 36,
          "contributesToCardTotal": true,
          "assumedStackable": true,
          "validUntil": "2026-06-30",
          "conditions": [
            {"type": "MIN_SPEND", "value": "1000", "label": "最低消費 1000 元"}
          ],
          "reason": "計入卡片總回饋：預估回饋 $36 元，封頂後 $36 元。"
        },
        {
          "promotionId": "promo_456_bonus",
          "promoVersionId": "ver_790",
          "title": "加碼固定回饋 50 元",
          "cashbackType": "FIXED",
          "cashbackValue": 50,
          "estimatedReturn": 50,
          "cappedReturn": 50,
          "contributesToCardTotal": true,
          "assumedStackable": true,
          "validUntil": "2026-06-30",
          "conditions": [],
          "reason": "計入卡片總回饋：預估回饋 $50 元，封頂後 $50 元。"
        }
      ],
      "applyUrl": "https://affiliate.example.com/ctbc-linepay"
    }
  ],
  "generatedAt": "2026-03-18T14:30:00",
  "disclaimer": "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。"
}
```

目前 response 中仍保留 `promotionId` / `promoVersionId` 作為代表 promotion，以維持舊客戶端相容性；真正的 card-level 資訊應以 `estimatedReturn`、`matchedPromotionCount`、`promotionBreakdown` 與 `comparison` 為準。

## 回饋型別與邊界條件
在情境式卡片比較中，不能直接拿 `cashbackValue` 原值比較不同回饋型別；必須先換算成該交易金額下的實際回饋金額。

目前系統中的基本換算原則如下：

- `PERCENT`：以交易金額乘上回饋百分比，再依系統規則取整
- `FIXED`：直接視為固定回饋金額
- `POINTS`：目前暫時沿用與 `PERCENT` 相同的計算方式處理

在比較 `FIXED` 與 `PERCENT` 時，至少要考慮以下邊界條件：

- `minAmount` 是否使 promotion 尚未生效
- `maxCashback` 是否使百分比回饋提前封頂
- 固定金額是否本身也有使用門檻或次數限制
- 同一情境下是否還有其他可並存的加碼優惠

最基本的 break-even 概念如下：

- 若固定回饋為 `F`
- 百分比回饋為 `r%`
- 不考慮封頂與其他限制時
- 交叉點金額為 `F / (r / 100)`

例如：

- 固定回饋 `50` 元
- 百分比回饋 `3%`
- 則交叉點約為 `1666.67` 元

代表：

- 交易金額低於約 `1667` 元時，固定 `50` 元可能較優
- 交易金額高於約 `1667` 元時，`3%` 回饋可能較優

但實務上還要再疊加下列修正：

- 若 `3%` 有 `maxCashback=60`，則在高額交易下不會無限增長
- 若固定回饋需要滿 `2000` 元才生效，則低於門檻時實際回饋應視為 `0`
- 若某 promotion 已因 `benefitUsage` 用盡額度，則該筆回饋不應再參與比較

因此未來若推薦引擎升級為完整的情境式卡片比較，排序基準應是「該情境下的有效回饋金額」，而不是原始的 `cashbackValue` 數字大小。

## 目前排序邏輯
1. 先將 top-level request 與 nested `scenario` 解析成單一比較情境
2. 過濾不符合情境條件的 promotion
3. 先計算每筆 promotion 的有效回饋與封頂後回饋
4. 依 `comparison.mode` 收斂成 card-level score
5. 以 card-level effective return 降序、到期日升序、年費升序、銀行 / 卡片 / promoVersionId 作為穩定 tie-breaker

## 目標演進方向
未來 recommendation engine 還需要補完三件事：

- 把 `promotion.stackability` 真正接入引擎，讓 `STACK_ALL_ELIGIBLE` 從過渡期 heuristic 升級成 deterministic stacking
- 對 `POINTS` 導入銀行別點數折現或等值換算模型
- 把 break-even 從代表 promotion 間比較擴充為完整 card bundle 比較

目前這個版本已經是 card-level comparison engine，而不是過渡性的 promotion-only ranking engine。

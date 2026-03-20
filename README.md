# CardSense API

信用卡回饋最佳化的 deterministic recommendation API。

## 本 repo 的職責
- 提供信用卡推薦 REST API
- 確定性回饋計算與排序邏輯
- 可解釋結果與可追溯 `promoVersionId`
- 異步審計日誌
- 每筆回應包含法律免責聲明

## 目前推薦規則重點
- 同一卡只保留最佳 promotion 進榜
- 停發卡與零回饋 promotion 不進榜
- 僅 `recommendationScope=RECOMMENDABLE` 的 promotion 會進入 recommendation engine
- `requiresRegistration` 會讀取 request 的 `registeredPromotionIds`
- `frequencyLimit` / `maxCashback` 會讀取 request 的 `benefitUsage`
- `conditions` / `excludedConditions` 使用結構化 condition object，而非 magic string

## 核心端點
- `POST /v1/recommendations/card`
- `GET /v1/cards`
- `GET /v1/banks`
- `GET /health`

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
  "category": "DINING",
  "amount": 1200,
  "cardCodes": ["CTBC_LINEPAY", "ESUN_ALLONE"],
  "registeredPromotionIds": ["ver_789"],
  "benefitUsage": [
    {"promoVersionId": "ver_789", "consumedAmount": 120}
  ],
  "location": "台北市信義區",
  "date": "2026-03-18"
}
```

## Recommendation Response Example
```json
{
  "requestId": "req_abc123",
  "recommendations": [
    {
      "cardName": "中國信託 示例網購卡",
      "bankName": "中國信託",
      "cashbackType": "PERCENT",
      "cashbackValue": 3.0,
      "estimatedReturn": 36,
      "reason": "中國信託 中國信託 示例網購卡 — ONLINE 消費享 3% 回饋，預估回饋 $36 元，優惠至 2026-06-30",
      "promotionId": "promo_456",
      "promoVersionId": "ver_789",
      "validUntil": "2026-06-30",
      "conditions": [
        {"type": "MIN_SPEND", "value": "1000", "label": "最低消費 1000 元"},
        {"type": "REGISTRATION_REQUIRED", "value": "true", "label": "需登錄活動"}
      ],
      "applyUrl": "https://affiliate.example.com/ctbc-linepay"
    }
  ],
  "generatedAt": "2026-03-18T14:30:00",
  "disclaimer": "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。"
}
```

## 推薦排序邏輯
1. 封頂回饋金額降序
2. 到期日升序
3. 年費升序
4. 銀行 / 卡片 / promoVersionId 作為穩定 tie-breaker

同一卡若有多筆 promotion，僅保留排序後最佳的一筆。

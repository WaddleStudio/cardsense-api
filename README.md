# CardSense API

信用卡回饋最佳化的 Decision-as-a-Service API。

本 API 根據消費場景推薦最佳信用卡，使用**確定性、可解釋、可審計**的規則邏輯 — 用戶請求路徑零 LLM。

## 本 repo 的職責
- 提供信用卡推薦 REST API（`POST /v1/recommendations/card`）
- 確定性回饋計算與排序邏輯
- 可解釋結果（每筆推薦皆可追溯至 `promoVersionId`）
- API Key 認證 + 分層速率限制
- 異步審計日誌（每筆請求皆記錄）
- 每筆回應包含法律免責聲明

## 本 repo 不做的事
- 不包含爬蟲或資料擷取（屬於 `cardsense-extractor`）
- 不在請求路徑使用 LLM — 零容忍
- 不儲存用戶交易紀錄（MVP 階段）
- 不提供優惠原始文字查詢

## 為什麼刻意不用 LLM？
| 考量 | 決策 |
|------|------|
| 審計需求 | 規則邏輯 100% 可重現 |
| API ToS 風險 | 多數 LLM API 禁止用於金融建議 |
| 延遲 | 規則引擎 < 100ms；LLM > 500ms |
| 成本 | 規則引擎零邊際成本 |
| 法規合規 | 確定性輸出更容易向監管機構解釋 |

## 技術棧
| 元件 | 選擇 | 說明 |
|------|------|------|
| 語言 | Java 21 | 金融場景慣例 |
| 框架 | Spring Boot 4 | Security + JPA 內建 |
| 資料庫 | PostgreSQL (Supabase) | 與 Extractor 共用 |
| ORM | Spring Data JPA | Spring 生態整合 |
| 認證 | API Key + JWT | B2B 用 API Key；未來 B2C 用 JWT |
| 部署 | Railway | Spring Boot 友好 + PostgreSQL addon |
| CI/CD | GitHub Actions | 三 repo 統一 pipeline |

## 專案結構

```
cardsense-api/
├── src/main/java/com/cardsense/api/
│   ├── controller/         ← REST 端點
│   ├── service/
│   │   ├── DecisionEngine.java      ← 核心推薦邏輯
│   │   └── RewardCalculator.java    ← 回饋計算
│   ├── domain/             ← 內部模型 (ScoredPromotion)
│   ├── repository/         ← Spring Data JPA
│   ├── audit/              ← 異步審計日誌
│   ├── security/           ← API Key 過濾器 + 速率限制
│   └── config/             ← RBAC、速率限制設定
├── src/main/resources/
│   └── application.yml
├── build.gradle.kts        ← 依賴 cardsense-contracts
├── VIBE_SPEC.md
└── README.md
```

## API 端點

### `POST /v1/recommendations/card` — 核心推薦

請求範例：
```json
{
  "category": "DINING",
  "amount": 1200,
  "cardCodes": ["CTBC_LINEPAY", "ESUN_ALLONE"],
  "location": "台北市信義區",
  "date": "2026-03-18"
}
```

回應範例：
```json
{
  "requestId": "req_abc123",
  "recommendations": [
    {
      "cardName": "中信 LINE Pay 卡",
      "bankName": "中國信託",
      "cashbackType": "PERCENT",
      "cashbackValue": 3.00,
      "estimatedReturn": 36,
      "reason": "中國信託 中信 LINE Pay 卡 — 餐飲消費享 3.00% 回饋，預估回饋 $36 元，優惠至 2026-06-30",
      "promotionId": "promo_456",
      "promoVersionId": "ver_789",
      "validUntil": "2026-06-30",
      "conditions": ["每月回饋上限 $300", "需登錄活動"],
      "applyUrl": "https://affiliate.example.com/ctbc-linepay"
    }
  ],
  "generatedAt": "2026-03-18T14:30:00",
  "disclaimer": "CardSense 提供信用卡優惠比較資訊，不構成金融建議。實際回饋依各銀行公告為準，請以銀行官網資訊為最終依據。"
}
```

### `GET /v1/cards` — 查詢可用卡片
支援篩選：`?bank=CTBC&status=ACTIVE`

### `GET /v1/banks` — 查詢支援銀行

### `GET /health` — 健康檢查（DB 連線 + 運行狀態）

## 推薦排序邏輯（確定性）

三層排序，依序套用：
1. **封頂回饋金額**（降序）— 預估回饋最高者優先
2. **到期日**（升序）— 快到期的優先推薦
3. **年費**（升序）— 年費低者作為最終 tiebreaker

回傳前 5 筆結果。相同輸入永遠產生相同輸出。

## API 方案與速率限制

| 方案 | 每日上限 | 價格 |
|------|---------|------|
| FREE | 100 次 | $0 |
| STARTER | 5,000 次 | $29/月 |
| GROWTH | 50,000 次 | $99/月 |
| ENTERPRISE | 無限 + SLA | 客製 |

超過限制 → 回傳 `429 Too Many Requests`，附帶 `Retry-After` header。

## 效能目標

| 指標 | 目標 |
|------|------|
| P50 延遲 | < 100ms |
| P99 延遲 | < 300ms |
| 確定性 | 100%（相同輸入 = 相同輸出） |
| 可用性 | 99.5% |

## 執行方式

```bash
# 本地開發
./gradlew bootRun

# 健康檢查
curl http://localhost:8080/health

# 測試推薦
curl -X POST http://localhost:8080/v1/recommendations/card \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"category":"DINING","amount":1200}'
```

## 法律與合規
- 每筆回應皆包含強制法律免責聲明
- API 不收集用戶個資 — `api_calls` 表儲存 `client_id`（B2B 實體），非終端用戶身份
- 聯盟行銷連結在 `applyUrl` 欄位中明確標示
- 金融許可諮詢進行中（台灣的「信用卡比較」是否屬於需特許的金融業務？）

## 關聯 Repository
| Repo | 角色 | 關係 |
|------|------|------|
| [cardsense-contracts](https://github.com/skywalker6666/cardsense-contracts) | 共用 schema 和列舉 | ⬆️ 上游依賴 |
| [cardsense-extractor](https://github.com/skywalker6666/cardsense-extractor) | 優惠擷取 pipeline | 產出本 repo 讀取的資料 |
| [fleet-command](https://github.com/skywalker6666/fleet-command) | 專案規格書庫 | CardSense-Spec.md |

---

*Owner: Alan | 隸屬 [CardSense](https://github.com/skywalker6666?tab=repositories&q=cardsense) 平台*

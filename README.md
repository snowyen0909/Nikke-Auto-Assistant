# NIKKE Auto Assistant

《勝利女神：妮姬》（GODDESS OF VICTORY: NIKKE）Android 畫面辨識與點擊輔助工具。

> **非官方、實驗性專案。** 本工具不隸屬於遊戲開發或發行公司。自動化操作可能受到遊戲服務條款限制，使用者須自行評估風險。目前辨識演算法仍可能漏判或誤點，請勿在無人監看時依賴本工具。

## Android 版本與介面

本專案提供兩個使用相同辨識與自動化核心的 APK：

| 版本 | 介面 | Application ID |
| --- | --- | --- |
| **Modern** | 深色科技風 **Android 原生 UI** | `com.example.nikkeauto.modern` |
| **Classic** | 原始簡易設定頁，適合測試與除錯 | `com.example.nikkeauto.classic` |

**Modern 目前不使用 HTML/WebView 介面。** Modern 的原生 UI 程式位於 `ModernUi.kt`。兩個版本可同時安裝，但請避免同時啟用兩份無障礙自動化服務，以免重複點擊。

## 功能

- **劇情／結算**：偵測 SKIP、結算繼續畫面與部分升級獎勵彈窗，嘗試自動點擊。
- **戰鬥準備**：優先點擊可用的橘色「快速戰鬥」；不可用／沒有時，嘗試點擊藍色「進入戰鬥」。
- **野外尋敵（實驗性）**：偵測怪物紅色光圈、藍色中心紅圈、導航驚嘆號與 Boss 標記。
- **巡弋（實驗性，預設關閉）**：無目標時嘗試縮放、滑動尋敵。
- **失敗處理**：偵測 `OPERATION FAILED`／逾時後嘗試「返回」，並停止地圖尋敵及巡弋。
- **暫停／繼續／停止**：保留螢幕擷取授權的暫停與繼續；完全停止後下次啟動需要重新授權。

以上為目前程式設計行為，不代表每個遊戲畫面都已通過實機測試。

## 安裝與使用

1. 從 GitHub **Actions** 選擇成功完成的 `Build Android APK (Modern + Classic)` 工作流程。
2. 在 **Artifacts** 下載 `NIKKE-Automation-Modern-APK` 或 `NIKKE-Automation-Classic-APK`。
3. 安裝 APK（Android 10 以上，主要針對 Android 14 測試）。
4. 在 App 中開啟無障礙設定，啟用對應版本的服務。僅在信任自行編譯的 APK 時，才允許 Android 的「受限制的設定」。
5. 點選螢幕擷取授權，**選擇整個螢幕**，然後切換到遊戲。
6. 依需要開啟各個功能模組；先關閉實驗性巡弋，確認辨識結果後再啟用。
7. 遇到異常可暫停，返回 App 查看。

無障礙權限可執行螢幕點擊，螢幕擷取權限可讀取螢幕內容；請審慎授權。

## GitHub Actions 編譯

使用 Java 17 / Android Gradle Plugin，工作流程定義於 `.github/workflows/build-apk.yml`。

```bash
gradle --no-daemon :app:assembleModernDebug :app:assembleClassicDebug
```

APK 輸出：

```text
app/build/outputs/apk/modern/debug/app-modern-debug.apk
app/build/outputs/apk/classic/debug/app-classic-debug.apk
```

Debug APK 僅供測試，正式散布應配置合適的簽章及版本管理。

## 技術架構

- **Kotlin / Android**
- **AccessibilityService**：透過 `dispatchGesture` 執行點擊及滑動。
- **MediaProjection / ImageReader**：取得螢幕畫面。
- **Google ML Kit 中文文字辨識**：辨識 SKIP、戰鬥和結算相關文字。
- **像素色彩與圖像特徵分析**：尋找快速戰鬥按鈕、怪物紅圈等。
- **EventLog**：App 私有空間的操作紀錄。

主要程式位於 `app/src/main/java/com/example/nikkeauto/`：

| 檔案 | 用途 |
| --- | --- |
| `MainActivity.kt` | 雙版本介面入口與權限流程 |
| `ModernUi.kt` | Modern 原生 UI |
| `CaptureService.kt` | 擷取畫面、辨識流程與狀態控制 |
| `TapService.kt` | 無障礙觸控手勢 |
| `BattleDetector.kt` | 戰鬥按鈕辨識 |
| `MapDetector.kt` | 野外敵人及導航辨識 |
| `SkipDetector.kt` | SKIP 圖像辨識 |
| `Settings.kt` | 功能開關與執行狀態 |

## 已知限制

- 螢幕比例、遊戲更新、文字語系、特效與縮放可能造成辨識失準。
- 大地圖怪物、Boss、導航和巡弋邏輯仍屬實驗性。
- SKIP 和「返回」操作依賴當前畫面判斷，可能因 OCR 或畫面配置不同而無法觸發。
- 點擊派送成功不等於遊戲已接受操作；請以遊戲畫面變化確認。
- 本專案目前沒有宣稱支援完全無人值守的自動通關。

## 介面版本紀錄

- `881a23b`：本次指定的 Modern **原生 UI** 參考版本。
- 後續功能；本次只將介面入口恢復至上述 UI 實作。

## 免責聲明

本專案僅供個人學習 Android 畫面辨識、無障礙服務與自動化技術用途；所有遊戲相關名稱、圖像與商標均屬其各自權利人所有。

## 更新紀錄

- 修正部分 Redmi Note 14 Pro 結算畫面的繼續點擊辨識：增加 `STAGE CLEAR COUNT`、`OPERATION COMPLETE` 與 `REWARD` 結算判定；點擊結算畫面下方安全區域。
- 依需求移除 Modern / Classic 的日誌畫面、日誌儲存及複製日誌功能（保留即時執行狀態與計數）。

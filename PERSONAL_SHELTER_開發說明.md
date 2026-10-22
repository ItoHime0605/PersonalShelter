# Personal Shelter 改造原始碼 — 階段 1（未編譯）

本專案以使用者提供的 PersonalDimension v1.0.2 官方原始碼為基礎。保留原作者的 LICENSE。

## 本次實際修改
- `PersonalDimensionService.buildVoidPlatform`：原本 3×3 草地改為 64×64，地表 y=-1，下方 y=-2 到 -6 共五層泥土；先載入 4×4 個區塊再鋪設。
- `CreateDimensionPacket`：非 OP 禁止送出公共維度建立請求，且在消耗生成器之前檢查。
- `OpenCreateMenuPacket`：非 OP 禁止開啟公共維度建立介面。
- `PersonalDimensionService.createPublicDimension`：服務端第二層權限驗證。

## 尚未完成，請勿當作正式版本
- 土地邊界、升級許可、擴張 GUI、持久化等級、OP 管理指令、預設進入點及保護既有建築。
- 原版的 SKY_ISLAND 與 CUSTOM 預設仍存在，建立個人維度時若選擇它們，不會使用 64×64 草地。正式版需要統一入口。
- 尚未執行 NeoForge Gradle 編譯或 Minecraft 實機測試。

## 建置
此 ZIP 包含官方 Gradle 原始碼專案。於可連線下載 Gradle/NeoForge 相依套件的環境使用 Java 21：
`./gradlew :neoforge:build`

請先在備份世界測試，勿覆蓋正式伺服器原有模組與存檔。

# Personal Shelter — 第二階段開發紀錄

已修改：
- 修正重新進入時缺少傳送記憶點會再次執行 `buildVoidPlatform` 的問題。現在只查詢高度並傳送，不再覆蓋原地形。
- NeoForge 物品註冊新增 `personaldimension:shelter_permit_lv1` ～ `lv4`。
- 增加四張許可證的繁體中文及英文名稱、暫用原版紙張貼圖模型。
- 新增 `ShelterUpgradeRules`：土地尺寸、邊界及只允許下一級許可證的判定。

尚未完成（不可聲稱可使用）：
- 許可證兌換、消耗與 GUI 單格插槽。
- 升級等級的世界存檔、擴張新環狀土地、實際邊界阻擋。
- 自訂進入點與 OP 管理指令。
- NeoForge 編譯及 Minecraft 實機驗證。

注意：目前仍使用原模組 ID `personaldimension`，不可與原版 JAR 同時安裝。

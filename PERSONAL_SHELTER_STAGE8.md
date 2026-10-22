# Personal Shelter — Stage 8 (source only)

## 新增
- `ShelterPermitTransaction`：伺服器端驗證許可證 ID、等級順序、持有者授權結果及道具數量。
- 只有土地擴張回傳成功才扣除一張許可證；失敗時保留。
- 僅允許伺服器主執行緒呼叫，避免並行操作。
- 地形生成前先檢查維度高度是否容納 Y=-6..-1。

## 尚未完成（不可當成可安裝版本）
- GUI 的真正單格 Menu/Slot、關閉時退還道具、擁有者驗證呼叫端、動態邊界、持久化升級日誌。
- 未完成 Gradle/NeoForge 編譯及遊戲實測。
- `ShelterPermitTransaction` 是整合介面，尚未被 GUI 呼叫。

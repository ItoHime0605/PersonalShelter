# Personal Shelter 第三階段（開發中）

本階段實際修改 `PersonalDimensionData`：
- 每個獨立維度的 SavedData 增加 `ShelterLevel`（0～4），重啟後仍保存。
- 增加 `ShelterEntry`（X/Y/Z），可清除；設定時檢查是否位於目前土地範圍。
- 舊存檔沒有上述欄位時，預設等級 0、未設定進入點。
- 改變等級或進入點時呼叫 `setDirty()`，供原本維度資料儲存系統寫入。

**尚未接線：** 實際升級流程、區塊生成、許可證消耗、GUI、邊界事件、指令及自訂進入點傳送仍未接到本次新欄位。`setShelterLevel` 只是儲存 API，不應由玩家封包直接呼叫。

**編譯狀態：** 尚未執行 NeoForge 完整 Gradle 編譯與遊戲測試。請勿將此 ZIP 當作可安裝 JAR。

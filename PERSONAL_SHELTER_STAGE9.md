# Personal Shelter 階段 9 — 正方形邊界判定核心

新增 `ShelterBoundary.java`：支援 64/96/128/192/256 格完整正方形、方塊座標與玩家小數座標判定、邊界內座標修正及傳送目的地驗證。新增 `tests/ShelterBoundaryTest.java` 可單獨以 javac 執行，不依賴 Minecraft。

注意：本階段**尚未**將邊界判定掛上 NeoForge 事件，因此目前**不會實際阻擋**玩家越界、放置、活塞、流體或傳送；GUI 單格與伺服器封包也尚未完成。獨立測試通過不代表完整模組已編譯。下一步必須先確認 NeoForge 事件及原始維度所有權查詢 API，再掛接伺服器事件並編譯完整 JAR。

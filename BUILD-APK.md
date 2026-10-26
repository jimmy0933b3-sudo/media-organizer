# Media Organizer：Actions 建置、簽章與最新 Release

`Build signed APK` 在每次 push 與手動 `workflow_dispatch` 執行。所有分支都會執行邏輯測試、簽章 Release APK 建置及 Android 30 / 35 模擬器驗證；**只有 master 成功通過整套測試後才發布 GitHub Release**。

## GitHub Actions Secrets（必填）

請於 Repository → Settings → Secrets and variables → Actions → Repository secrets 配置下列 4 項，**不可直接提交憑證檔或密碼到 Git**：

| 項目 | 建議 Secret | 相容名稱 |
| --- | --- | --- |
| Release keystore 的整份檔案 Base64 | `ANDROID_KEYSTORE_BASE64` | `KEYSTORE_BASE64` |
| Keystore 密碼 | `ANDROID_KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` |
| Key alias | `ANDROID_KEY_ALIAS` | `KEY_ALIAS` |
| Key 密碼 | `ANDROID_KEY_PASSWORD` | `KEY_PASSWORD` |

產生 Base64 可使用 `base64 -w 0 release.keystore`（Linux），或 `base64 < release.keystore | tr -d '\n'`（macOS）。使用整份檔案原始二進位內容的 Base64，不是 alias 或密碼的 Base64。

缺任何項目、無法解碼、alias 不存在、密碼不正確、APK 未使用 Actions 金鑰，均讓 CI **直接失敗**；不會偷偷回退到倉庫中的公開 `signing/local-testing.p12`。本地 Debug 仍使用既有測試簽章。本地沒有提供簽章環境變數時，`assembleRelease` 可能只會產出未簽章 APK。

**更新安裝注意：** Android 只有在新 APK 使用與已安裝舊版相同的簽章憑證時才能直接覆蓋安裝。若 Actions 的金鑰不同於舊版測試簽章，就必須先卸載舊版，導致 App 本機快取及復原紀錄被移除。要保留資料，請在 Secrets 放入原本交付 APK 使用的同一把金鑰，或先規劃資料轉移。

## 產物與驗證

- `dist/Media-Organizer.apk`：Actions Secrets 金鑰簽署的 Release APK。
- `dist/Media-Organizer.apk.sha256`：APK SHA-256。
- `dist/apk-signature.txt`：APK 簽章資訊，CI 會和 Secrets 對應 keystore 的憑證指紋比對。
- `dist/apk-metadata.txt`：套件名稱、SDK 與版本資訊。
- `dist/test-results.txt`：邏輯測試輸出。
- `dist/build-info.txt`：來源 commit、CI run、模型資訊。

工作流程保留 Android SDK 35、Temurin JDK 17、Gradle 8.9，以及 API 30 / 35 的模擬器測試。產物另存 Actions artifact，可供非 master 分支檢查；不會自行推回 Git 倉庫，以免形成重複觸發的建置循環。

## Release 行為

每次成功的 master 建置：

1. 確認該次 `GITHUB_SHA` 仍是 master 最新 commit，避免過期工作覆寫最新版本。
2. 使用 `media-organizer-ci-<run_number>-<run_attempt>` 作為新 Tag，在該 Commit 建立 Release，附上經驗證的 APK、SHA-256、簽章、metadata 與來源資訊，並標示 Latest。
3. 確認新 Release 中確實包含 APK 後，移除所有其他 GitHub Releases，**只留下最新一筆**。
4. 清理本 CI 產生的舊 Tag（包含先前 `latest-build`）；其他人工建立的 Tag 保留，以免破壞既有版本參考。

如果測試、簽章、APK 上傳失敗，不會刪除既有 Release。Release 清除屬於永久操作，請確定此專案確實只需要一筆公開下載版本。

下載位置：[GitHub Releases](https://github.com/jimmy0933b3-sudo/media-organizer/releases/latest)。

> CI 的 Java 邏輯測試與模擬器啟動測試，不能代替真機的掃描、刪除、移動、復原及資料保存測試。

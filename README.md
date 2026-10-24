# Kboard 注音輸入法

一個對 TalkBack 友善的開源台灣注音輸入法。
A TalkBack-friendly, open source Zhuyin (Bopomofo) keyboard for Android.

[![Build](https://github.com/keyang556/Kboard/actions/workflows/build.yml/badge.svg)](https://github.com/keyang556/Kboard/actions/workflows/build.yml)

[English](#english) · [授權](#授權-license)

## 特色

- **注音輸入**：使用新酷音函式庫 [libchewing](https://codeberg.org/chewing/libchewing) 的智慧選詞，與[樸實注音](https://github.com/hiroshiyui/GuilelessBopomofo)、桌面版新酷音相同的引擎。
- **三種注音鍵盤排列**：大千（標準）、倚天 26 鍵、許氏。
- **英文、數字、符號**：英文字母（含 Shift、大寫鎖定、句首自動大寫）、數字與半形符號、全形中文標點，以及符號表（含表情符號）。
- **數字欄位**自動切換為數字鍵盤或電話鍵盤。
- **完整支援 TalkBack**，操作方式與 [HeliBoard](https://github.com/HeliBorg/HeliBoard) 相同（見下方）。
- **實體鍵盤**：中文模式下可直接以實體鍵盤打注音，Shift+空白鍵切換中英文，方向鍵↓開啟候選字、數字鍵選字。
- **隱私**：不需要任何權限、不連網；學到的詞彙只存在裝置上，且不會被備份。

## TalkBack 操作方式

Kboard 依照 AOSP 鍵盤與 HeliBoard 的做法，把每個按鍵提供給無障礙服務成為獨立節點（virtual view），並遵循 Android 官方無障礙規範。

| 動作 | 方式 |
| --- | --- |
| 聽按鍵 | 手指在鍵盤上滑動，觸碰到的按鍵會被唸出 |
| 輸入按鍵 | 抬起手指即輸入剛聽到的按鍵（lift to type） |
| 也可以 | 點兩下任何地方，輸入最後聽到的按鍵 |
| 長按功能 | 在按鍵上停留 3 秒、點兩下並按住，或從 TalkBack 動作選單選擇 |
| 選候選字 | 觸碰按鍵上方的候選字會唸出，點兩下選用 |
| 所有候選字 | 點兩下「顯示所有候選字」，候選字面板會報讀「第幾頁，共幾頁」 |

其他無障礙細節：

- 注音符號以 `LocaleSpan`（zh-TW）標示，系統語言即使是英文也會用中文語音唸出；聲調唸作「二聲、三聲…」，標點與符號唸出名稱（例如「頓號」「左書名號」）。
- 切換注音／英文、符號鍵盤、Shift 與大寫鎖定都會報讀；Shift 的狀態以 state description 提供。
- 空白鍵在注音尚未打完時唸作「空白鍵，一聲」。
- 密碼欄位中，未連接耳機時按鍵唸作「點」（可在設定中關閉）。
- 按鍵高度至少 48 dp，並提供「大」「特大」兩種尺寸；淺色與深色主題的文字對比皆達 WCAG AA 4.5:1，按鍵外框達 3:1。
- 候選字列與候選字面板以 collection 資訊標示數量與位置；設定畫面全部使用系統元件，標題皆標為 heading。

長按功能：

- 大千鍵盤第一排長按輸入數字 1～0。
- 「，」長按輸入「、」，「。」長按輸入「？」。
- 英文字母第一排長按輸入數字。
- 空白鍵與「中／英」鍵長按切換輸入法。

## 安裝與設定

1. 安裝 APK（F-Droid 上架前，可從 [GitHub Releases](https://github.com/keyang556/Kboard/releases) 或 [Actions](https://github.com/keyang556/Kboard/actions) 的建置成品取得）。
2. 開啟「Kboard」App，按「在系統設定中啟用 Kboard」。
3. 回到 App，按「選擇輸入法」並選擇 Kboard 注音。
4. 在 App 中的「試打看看」欄位測試。

## 建置

需要：

- JDK 17 以上（建議 21）
- Android SDK：platform `android-37.0`、build-tools `37.0.0`、NDK `30.0.16248370`、CMake `3.31.6`
- [rustup](https://rustup.rs/)：會依 `rust-toolchain.toml` 使用 Rust 1.95.0 與四個 Android target

```bash
git clone --recursive https://github.com/keyang556/Kboard.git
```

```bash
./gradlew assembleDebug
```

已經 clone 但沒有子模組時：

```bash
git submodule update --init --recursive
```

開發時只建置單一 ABI 可加快速度：

```bash
./gradlew assembleDebug -Pkboard.abi=arm64-v8a
```

建置流程：

1. Gradle 的 `build<Variant>ChewingData` 工作用 cargo 在本機建置 `chewing-cli`，把 libchewing 的詞庫 CSV 編譯成 `tsi.dat`、`word.dat`，連同 `swkb.dat`、`symbols.dat` 打包為 assets。
2. CMake 經由 [Corrosion](https://github.com/corrosion-rs/corrosion) 為各 ABI 編譯 libchewing（Rust），靜態連結進 JNI 函式庫 `libkboard_jni.so`。
3. App 第一次使用時把詞庫複製到 `noBackupFilesDir`，供 libchewing 讀取。

測試與檢查：

```bash
./gradlew testDebugUnitTest lintDebug
```

### 專案結構

```
app/src/main/
├── cpp/
│   ├── kboard_jni.cpp          JNI 橋接（UTF-8 ↔ UTF-16 轉換、候選字列舉）
│   ├── CMakeLists.txt
│   ├── libchewing/             子模組：新酷音（含詞庫資料子模組）
│   └── corrosion/              子模組：Rust ↔ CMake
└── java/io/github/keyang556/kboard/
    ├── engine/                 libchewing 包裝、詞庫安裝
    ├── keyboard/               按鍵模型、鍵盤配置、KeyboardView（自繪鍵盤）
    ├── accessibility/          TalkBack：virtual view 節點、觸控探索、報讀、按鍵描述
    ├── candidates/             候選字列與候選字面板
    ├── ime/                    InputMethodService
    └── settings/               設定與啟用引導畫面
```

## GitHub Actions

`.github/workflows/build.yml` 會在每次 push 與 pull request：

- 驗證 Gradle wrapper
- 安裝 Rust（依 `rust-toolchain.toml`）、NDK、CMake
- 執行單元測試與 lint，建置 debug 與 release APK，並上傳為 artifact

推送 `v*` 標籤（例如 `v0.1.0`）時會建立 GitHub Release 並附上 APK。若在 repository secrets 設定 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，release APK 會被簽署；否則為未簽署 APK。

## F-Droid

專案已針對 F-Droid 發布調整：

- 只依賴自由軟體（AndroidX Core、libchewing），沒有 Google Play 服務、追蹤或廣告，不需要任何權限。
- 所有原生程式碼都從原始碼建置：libchewing 與 Corrosion 為固定版本的 git 子模組，建置時不從網路下載 CMake 模組。
- 可重現建置（reproducible builds）：關閉 `dependenciesInfo` 與 `vcsInfo`、固定 Rust／NDK／CMake 版本、移除原生程式中的建置路徑（`-ffile-prefix-map`、`--remap-path-prefix`），詞庫編譯結果是確定性的。
- `fastlane/metadata/android/` 提供英文與正體中文的上架文字與圖示。
- `fdroid/io.github.keyang556.kboard.yml` 是送交 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 用的建置配方範本。

發布新版本：

1. 修改 `app/build.gradle.kts` 的 `versionCode` 與 `versionName`。
2. 新增 `fastlane/metadata/android/*/changelogs/<versionCode>.txt`。
3. 推送標籤 `v<versionName>`。

## English

Kboard is a Zhuyin keyboard for Traditional Chinese as written in Taiwan, built first of all for the TalkBack screen reader.

- **Zhuyin** with libchewing's phrase conversion (the engine of Guileless Bopomofo), in the Dachen, ETen 26-key and Hsu layouts.
- **English, numbers and symbols**: letters with Shift, Caps Lock and auto-capitalisation, digits and half-width symbols, full-width Chinese punctuation, and a symbol table with emoji. Number and phone fields get number pads.
- **TalkBack, the way HeliBoard does it**: every key is an accessibility node of its own; slide a finger to hear keys and lift it to type; double-tap types the last key heard; rest on a key for 3 seconds (or double-tap and hold) for its long press. Keyboard, Shift and Caps Lock changes are announced. Zhuyin is marked as Chinese with a `LocaleSpan`, so it is read with a Chinese voice whatever the system language; tones and symbols are read by name.
- **Following Android's accessibility guidelines**: touch targets of at least 48 dp, WCAG AA contrast in light and dark themes, state descriptions, collection info for the candidates, live-region page announcements, headings in the settings screen, and only platform widgets there.
- **Private**: no permissions, no network; learned phrases stay on the device and are excluded from backups.

To build, install JDK 17+, the Android SDK (platform 37.0, build-tools 37.0.0, NDK 30.0.16248370, CMake 3.31.6) and rustup, then:

```bash
git clone --recursive https://github.com/keyang556/Kboard.git
```

```bash
./gradlew assembleDebug
```

## 授權 License

Kboard 以 [GNU General Public License 第 3 版或更新版本](LICENSE)（GPL-3.0-or-later）釋出。
Kboard is released under the [GNU General Public License, version 3 or later](LICENSE) (GPL-3.0-or-later).

- [libchewing](https://codeberg.org/chewing/libchewing) 與其詞庫：GNU LGPL 2.1 或更新版本
- [Corrosion](https://github.com/corrosion-rs/corrosion)：MIT
- [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core)：Apache 2.0

## 致謝

- [新酷音 Chewing](https://chewing.im/) 與詞庫維護者
- [樸實注音 Guileless Bopomofo](https://github.com/hiroshiyui/GuilelessBopomofo)：Android 上使用 libchewing 的先行者，本專案的注音引擎整合方式以它為參考
- [HeliBoard](https://github.com/HeliBorg/HeliBoard) 與 AOSP LatinIME：TalkBack 支援的設計參考

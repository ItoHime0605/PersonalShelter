# Personal Dimension

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**[简体中文](README_zh.md)** | English

---

## Overview

A Minecraft mod that creates personal dimensions for each player, with custom terrain, time/weather control, friend access management, and more.

---

## Features

### Core Features
- **Personal Dimension System**: Each player has their own independent dimension, created and accessed via generator item
- **Time Control**: Freely adjust the time flow speed within the dimension, with option to follow Overworld time
- **Weather Control**: Switch between sunny/rainy/thunder at any time, with option to follow Overworld weather
- **Moon Phase Control**: Custom moon phase cycle, with option to follow Overworld moon phase

### Extended Features
- **Friend Access System**: Manage friend access permissions, set different permission groups
- **Teleporter Management**: Set multiple teleporters within personal dimension for quick travel
- **Biome Collector**: Collect Overworld biomes into personal dimension
- **Biome Modifier**: Modify biome characteristics in personal dimension
- **Lightning Interface**: Control lightning generation within dimension
- **Spawn Control**: Custom mob spawn rules and filter modes

### Configuration Options
- Block whitelist/blacklist mode
- Dimension terrain settings
- Mob spawn configuration

---

## Dependencies

| Dependency | Fabric | NeoForge |
|------------|--------|----------|
| Minecraft | 1.21.x | 1.21.x |
| Java | 21+ | 21+ |
| Architectury API | 13.0.8+ | 13.0.8+ |
| Fabric API | 0.116.14+ | - |

---

## Building

### Requirements
- JDK 21+
- Gradle 8.14.5+

### Commands

```bash
# Build all platforms
./gradlew build

# Build only Fabric version
./gradlew fabric:build

# Build only NeoForge version
./gradlew neoforge:build

# Run Fabric development environment
./gradlew fabric:runClient

# Run NeoForge development environment
./gradlew neoforge:runClient
```

Build artifacts are located in `fabric/build/libs/` and `neoforge/build/libs/`.

---

## Usage

### Create Personal Dimension
1. Right-click ground with **Personal Dimension Generator**
2. Set dimension parameters in the popup interface
3. Click create to automatically teleport to the new dimension

### Enter Personal Dimension
- Right-click ground while holding **Personal Dimension Generator**

### Control Dimension
- Use **Personal Dimension Controller** to open control panel
- Adjust directly via interface blocks (Time Interface, Weather Interface, Moon Phase Interface, etc.)

### Friend System
- Use Friend Visitor Teleporter to manage access permissions
- Set permission groups to control friend entry permissions

---

## Project Structure

```
personaldimension-1.21.1-Fabric-NeoForge/
├── common/              # Platform-agnostic code
│   ├── src/main/java/   # Java source code
│   └── src/main/resources/  # Resources (textures, models, lang, etc.)
├── fabric/              # Fabric platform code
│   ├── src/main/java/
│   └── src/main/resources/
├── neoforge/            # NeoForge platform code
│   ├── src/main/java/
│   └── src/main/resources/
├── scripts/             # Helper scripts
│   ├── translate.py     # Translation management script
│   └── translations.csv # Translation data source
├── gradle/              # Gradle Wrapper
├── build.gradle         # Root build configuration
├── gradle.properties    # Build properties
└── settings.gradle      # Module configuration
```

---

## Translation Support

This mod supports the following languages:
- English (en_us)
- 中文简体 (zh_cn)
- 中文繁体 (zh_tw)
- 日本語 (ja_jp)
- 한국어 (ko_kr)
- Deutsch (de_de)
- Français (fr_fr)
- Español (es_es)
- Русский (ru_ru)
- Italiano (it_it)
- Português (Brasil) (pt_br)
- Nederlands (nl_nl)
- Polski (pl_pl)
- العربية (ar_sa)
- Tiếng Việt (vi_vn)

### Translation Management

Use `scripts/translate.py` for translation management:

```bash
# Export all languages to CSV
python scripts/translate.py export

# Import from CSV and generate all JSON files
python scripts/translate.py import

# Validate key consistency across all language files
python scripts/translate.py validate
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

---

## Author

- **ruadou**

---

## Version History

### 1.0.2
- Fixed server-side Mixin class loading error (`Attempted to load class for invalid dist DEDICATED_SERVER`) by splitting client-side code into client-only Mixin classes

### 1.0.1
- Fixed item duplication exploit caused by drop item permission
- Added configuration options for friend permissions (enable/disable each permission, set default state for new permission groups)

### 1.0.0
- Initial release
- Support for both Fabric and NeoForge platforms
- Basic personal dimension functionality
- Time/weather/moon phase control
- Friend access system
- Multi-language support
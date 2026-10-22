# Personal Dimension

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

简体中文 | **[English](README.md)**

---

## 简介

为每位玩家创建独立的个人维度，支持自定义地形、时间天气控制、好友访问权限管理等功能。

---

## 功能特性

### 核心功能
- **个人维度系统**：每位玩家拥有专属的独立维度，通过生成器物品创建和进入
- **时间控制**：自由调节维度内的时间流逝速度，支持跟随主世界时间
- **天气控制**：随时切换晴天/雨天/雷暴，支持跟随主世界天气
- **月相控制**：自定义月相周期，支持跟随主世界月相

### 扩展功能
- **好友访问系统**：管理好友访问权限，设置不同权限组
- **传送点管理**：在个人维度内设置多个传送点，快速切换位置
- **群系收录器**：收录主世界群系到个人维度
- **群系修改器**：修改个人维度的群系特性
- **闪电接口**：控制维度内闪电生成
- **生物生成控制**：自定义生物生成规则和筛选模式

### 配置选项
- 方块白名单/黑名单模式
- 维度地形相关设置
- 生物生成相关配置

---

## 前置依赖

| 依赖 | Fabric | NeoForge |
|------|--------|----------|
| Minecraft | 1.21.x | 1.21.x |
| Java | 21+ | 21+ |
| Architectury API | 13.0.8+ | 13.0.8+ |
| Fabric API | 0.116.14+ | - |

---

## 构建说明

### 环境要求
- JDK 21+
- Gradle 8.14.5+

### 构建命令

```bash
# 构建所有平台
./gradlew build

# 仅构建 Fabric 版本
./gradlew fabric:build

# 仅构建 NeoForge 版本
./gradlew neoforge:build

# 运行 Fabric 开发环境
./gradlew fabric:runClient

# 运行 NeoForge 开发环境
./gradlew neoforge:runClient
```

构建产物位于 `fabric/build/libs/` 和 `neoforge/build/libs/` 目录。

---

## 使用方法

### 创建个人维度
1. 使用 **个人维度生成器**（Personal Dimension Generator）右键
2. 在弹出的界面中设置维度参数
3. 点击创建，自动传送至新维度

### 进入个人维度
- 手持 **个人维度生成器** 右键即可进入

### 控制维度
- 使用 **个人维度控制器**（Personal Dimension Controller）打开控制面板
- 通过界面方块（时间接口、天气接口、月相接口等）直接调节

### 好友系统
- 使用好友访客传送门管理访问权限
- 设置权限组，控制好友的进入权限

---

## 项目结构

```
personaldimension-1.21.1-Fabric-NeoForge/
├── common/              # 平台无关代码
│   ├── src/main/java/   # Java 源码
│   └── src/main/resources/  # 资源文件（材质、模型、语言等）
├── fabric/              # Fabric 平台代码
│   ├── src/main/java/
│   └── src/main/resources/
├── neoforge/            # NeoForge 平台代码
│   ├── src/main/java/
│   └── src/main/resources/
├── scripts/             # 辅助脚本
│   ├── translate.py     # 翻译管理脚本
│   └── translations.csv # 翻译数据源
├── gradle/              # Gradle Wrapper
├── build.gradle         # 根构建配置
├── gradle.properties    # 构建属性
└── settings.gradle      # 模块配置
```

---

## 翻译支持

本模组支持以下语言：
- English (en_us)
- 中文简体 (zh_cn)
- 中文繁体 (zh_tw)
- 日语 (ja_jp)
- 韩语 (ko_kr)
- 德语 (de_de)
- 法语 (fr_fr)
- 西班牙语 (es_es)
- 俄语 (ru_ru)
- 意大利语 (it_it)
- 葡萄牙语(巴西) (pt_br)
- 荷兰语 (nl_nl)
- 波兰语 (pl_pl)
- 阿拉伯语 (ar_sa)
- 越南语 (vi_vn)

### 翻译管理

使用 `scripts/translate.py` 管理翻译：

```bash
# 导出所有语言到 CSV
python scripts/translate.py export

# 从 CSV 导入并生成所有 JSON
python scripts/translate.py import

# 验证所有语言文件键的一致性
python scripts/translate.py validate
```

---

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

---

## 贡献

欢迎提交 Issue 和 Pull Request！

---

## 作者

- **ruadou**

---

## 版本历史

### 1.0.2
- 修复服务端 Mixin 类加载错误（`Attempted to load class for invalid dist DEDICATED_SERVER`），将客户端预测逻辑拆分到客户端专属 Mixin 类

### 1.0.1
- 修复丢弃物品权限导致的刷物品问题
- 新增好友权限的配置文件选项（可开关每项权限，并设置新建权限组时的默认开启状态）

### 1.0.0
- 初始版本
- 支持 Fabric 和 NeoForge 双平台
- 基础个人维度功能
- 时间/天气/月相控制
- 好友访问系统
- 多语言支持
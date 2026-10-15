# Day 01 学习笔记

## 今天完成了
- 梳理一期核心需求文档与微服务拆分边界
- 初始化 Maven 多模块父工程 `shopmind-parent`
- 搭建通用核心模块 `shopmind-common-core`（Result、ErrorCode、BizException）
- 搭建首个业务服务 `shopmind-merchant`，编写 `/ping` 健康检查
- 通过 Docker Compose 编排一键启动 MySQL 8.4、Redis 7.4、Nacos 3.0.3

## 遇到的问题与解决
| 问题 | 原因 | 解决 |
| :--- | :--- | :--- |
| | | |

## 踩坑实验记录
### D01-1 · utf8 存不了 emoji
- 现象：
- 原理：
- 修复：

### D01-2 · Public Key Retrieval is not allowed
- 现象：
- 原理：
- 修复：

## 面试题我的回答（用自己的话复述）
1. **微服务怎么拆？你的项目为什么这样拆？**
2. **`dependencyManagement` 和 `dependencies` 有什么区别？BOM 的 `import` 作用是什么？**
3. **MySQL 的 `utf8` 和 `utf8mb4` 有什么区别？**
4. **为什么选 Spring Boot 3.5 而不是 4.0？**
5. **Docker 里的 MySQL 初始化脚本为什么没生效？**

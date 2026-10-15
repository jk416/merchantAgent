# 智店云 ShopMind 系统架构文档

## 1. 服务拆分与端口规划

| 服务 | 端口 | 数据库 / 存储 | 说明 |
| :--- | :--- | :--- | :--- |
| `shopmind-gateway` | 9000 | — | Spring Cloud Gateway 网关路由与限流 |
| `shopmind-merchant`| 9101 | `shopmind_merchant` | 租户、商家、员工权限、套餐 |
| `shopmind-product` | 9102 | `shopmind_product` | SPU/SKU 商品、库存服务 |
| `shopmind-order`   | 9103 | `shopmind_order` | 订单、支付、退款状态机 |
| `shopmind-ai`      | 9104 | `shopmind_ai` + PGvector | AI 客服、知识库 RAG、Agent 工具链 |
| `MySQL`            | 3306 | 业务关系型存储 | MySQL 8.4 LTS |
| `Redis`            | 6379 | 缓存、分布式锁、会话 | Redis 7.4 |
| `Nacos`            | 8848/9848/8088 | 注册中心与配置中心 | Nacos 3.0.3 |

## 2. 版本选型与理由

- **JDK 21**：LTS 版本，具备虚拟线程（Virtual Threads），方便对比传统线程池与轻量级线程在高并发 I/O 下的差异。
- **Spring Boot 3.5.x** + **Spring Cloud 2025.0.0** + **Spring Cloud Alibaba 2025.0.0.0**：官方稳定配套基线。
- **MySQL 8.4 LTS** + **Redis 7.4** + **Nacos 3.0.3**。
- 不采用尚未在微服务生态完全成熟适配的 Boot 4.0，避免在依赖冲突上浪费时间。

## 3. 服务依赖拓扑
- 前端与外部请求统一通过 `shopmind-gateway`（9000）进入。
- `order` 与 `product` 跨服务交互，用于实践分布式事务（Seata AT / 事务消息）。
- `ai` 服务通过 Function Calling 反射调用 `order` 与 `product` 提供的工具接口。

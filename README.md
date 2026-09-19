# 黑马商城（HMall）

基于 Spring Boot 3、Spring Cloud Alibaba 的电商微服务示例项目，包含商品、用户、购物车、订单、支付、搜索、评论、秒杀、优惠券及 AI 导购模块。

## 模块

| 模块 | 说明 | 默认端口 |
| --- | --- | --- |
| `hm-gateway` | API 网关、JWT 鉴权与服务路由 | 8080 |
| `item-service` | 商品、库存与商品后台管理 | 8081 |
| `cart-service` | 购物车 | 8082 |
| `user-service` | 登录、地址、验证码与用户余额 | 8084 |
| `trade-service` | 订单、物流与订单状态流转 | 8085 |
| `pay-service` | 余额支付与支付单 | 8086 |
| `search-service` | Elasticsearch 商品搜索、筛选与联想 | 8089 |
| `review-service` | 商品评论 | 8087 |
| `seckill-service` | 秒杀活动与订单 | 8088 |
| `coupon-service` | 优惠券模板与领券 | 8091 |
| `hm-ai` | 基于 Spring AI 的商品导购 | 8088（请与秒杀服务错开配置） |

## 运行依赖

- JDK 17
- Maven 3.9+
- MySQL 8
- Redis
- RabbitMQ
- Nacos
- Elasticsearch 7.x

本地数据库至少导入 `hm-item`、`hm-user`、`hm-cart`、`hm-trade`、`hm-pay` 五个库的初始化 SQL。用户服务还需要导入 [`resources/hm-user-rbac.sql`](resources/hm-user-rbac.sql) 以创建角色与用户角色关系。

## 构建

```bash
mvn package -DskipTests
```

## 本地启动

先启动基础设施，再依次启动用户、搜索、商品、购物车、订单、支付和网关服务。服务通过 Nacos 注册发现；本机多项目并行时可用命令行覆盖端口，例如：

```bash
java -jar hm-gateway/target/hm-gateway.jar --server.port=19000
```

商品搜索服务首次启动会创建并填充 Elasticsearch `items` 索引。为避免本地内存峰值，索引按批次导入。

## 接口验证账号

```text
用户名：Jack
密码：123
```

该账号需要在 `hm-user` 库中具有 `USER` 和 `ADMIN` 角色。余额支付使用同一密码 `123`。

## 前端

静态前端位于同级目录 `../hmall-nginx/html`，由 Nginx 提供：

- 用户商城：`http://localhost:18080`
- 后台管理：`http://localhost:18082`

Nginx 的 `/api` 代理应指向网关端口；如果网关以 `19000` 运行，则对应修改 `../hmall-nginx/conf/nginx.conf`。

## 已验证链路

- 用户名密码登录与 JWT 鉴权
- ES 商品搜索、品牌/分类筛选和搜索联想
- 商品详情与后台商品分页
- 购物车读取
- 创建订单
- 余额支付，并同步回写订单为已支付

# EchoChat

本项目是基于Spring Cloud框架为主开发的即时通讯（IM）系统，基于 WebSocket 长连接实现实时消息推送，覆盖单聊、群聊、朋友圈等核心场景。核心设计目标：**消息不丢、不乱、不重**。

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot + Spring Cloud Alibaba | 3.2.10 / 2023.0.1.0 |
| 网关 | Spring Cloud Gateway | — |
| 注册/配置中心 | Nacos | 2.x |
| 远程调用 | OpenFeign | — |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全框架 | Spring Security + JWT (JJWT) | 0.12.6 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis（Lettuce） | 6.x |
| 消息队列 | RocketMQ | 5.x |
| WebSocket | Netty | 4.x |
| 搜索引擎 | Elasticsearch | 8.x |
| 对象存储 | MinIO | 8.5.10 |
| 前端 | React + TypeScript + Vite + Tailwind + Zustand | 19 / 8 / 5 |
| 构建工具 | Maven | 3.9+ |
| Java 版本 | JDK | 17 |

## 项目结构

```
EchoChat
├── echochat-common          # 公共模块：统一响应体、异常处理、工具类、雪花算法
├── echochat-gateway         # 网关：JWT 校验、限流、路由转发（端口 8080）
├── echochat-auth            # 认证服务：注册、登录、Token 刷新（端口 8001）
├── echochat-user            # 用户服务：用户信息、好友关系（端口 8002）
├── echochat-message         # 消息服务：消息收发、WebSocket 连接管理（端口 8003）
├── echochat-group           # 群组服务：群 CRUD、成员管理、角色权限（端口 8004）
├── echochat-moment          # 朋友圈服务：发布、点赞、评论、Feed 流（端口 8005）
├── echochat-file            # 文件服务：MinIO 上传/下载/缩略图（端口 8007）
├── echochat-search          # 搜索服务：ES 全文检索（端口 8008）
├── echochat-notification    # 通知服务：持久化通知 + WebSocket 推送（端口 8009）
├── echochat-frontend        # 前端项目（React）
└── docker                   # Docker Compose 编排及中间件配置
```

## 功能概要

### 用户与认证
- 邮箱注册（框架预留，当前直接注册）/ 登录，JWT 双 Token 机制（AccessToken 15min + RefreshToken 7d）
- 多端同时在线，设备管理（查看、远程踢下线）
- 修改密码（全设备 Token 失效）
- 个人资料管理（头像 / 昵称 / 签名等）

### 消息系统
- 单聊 / 群聊消息收发，支持 TEXT / IMAGE / FILE / VOICE / VIDEO / SYSTEM 类型
- 消息撤回（2 分钟内）、删除（软删除）、转发、引用回复、置顶
- 会话序列号（Seq）机制保证消息不乱序
- 客户端 ACK + 三级退避重试（5s / 30s / 5min）保证不丢
- 离线消息存储，上线后增量同步
- 已读回执（单聊双勾 + 群聊已读计数）
- 输入状态指示（Typing Indicator）

### 好友系统
- 好友申请 / 接受 / 拒绝（申请 3 天过期自动清理）
- 好友分组、备注别名、拉黑
- 非好友消息限制

### 群组系统
- 三级角色体系：Owner / Admin / Member
- 完整权限矩阵（禁言、踢人、全员禁言、转让群主等）
- @提及（支持 @某人 / @全体成员）
- 邀请链接、入群申请/审批
- 慢速模式
- 推拉结合扩散模型（≤500 人写扩散，>500 人读扩散）

### 朋友圈（Moments）
- 发布图文动态（最多 9 张图）
- 点赞 / 评论（二级回复）
- 推模式 Feed 时间线（写扩散到好友收件箱）
- 权限管理（不给谁看 / 展示时间范围）
- 90 天自动归档

### 文件系统
- MinIO 对象存储，支持预签名直传 + 代理上传/下载
- 图片自动生成缩略图
- 文件元信息管理

### 搜索
- Elasticsearch 全文检索
- 全局 / 指定用户聊天记录搜索
- 用户搜索、群搜索
- RocketMQ 异步索引同步

### 通知系统
- 持久化通知存储 + WebSocket 实时推送
- 消息去重（event_id 幂等）
- 支持浏览器桌面通知

### 安全风控
- Gateway 统一 JWT 鉴权 + Token 黑名单
- Redis + Lua 滑动窗口限流（全局 / 登录 / 注册 / 消息发送分级限流）
- IP 黑名单
- DFA 敏感词过滤
- XSS / SQL 注入防护

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose（用于中间件）
- Node.js 20+（前端）

### 1. 启动中间件

```bash
cd docker

# Linux / macOS
bash prepare.sh
# Windows (PowerShell)
# 手动创建目录: mkdir -p data/es; 或使用 WSL2 运行 prepare.sh

docker compose up -d
```

启动的中间件：MySQL (3306)、Redis (6379)、Nacos (8848)、RocketMQ (9876)、Elasticsearch (9200)、MinIO (9000)

### 2. 启动后端服务

```bash
# 编译所有模块
mvn clean package -DskipTests

# 按依赖顺序启动（或使用 IDE 启动各模块的 Application 类）
# 推荐启动顺序: echochat-common → echochat-gateway → 其余服务
mvn spring-boot:run -pl echochat-gateway
mvn spring-boot:run -pl echochat-auth
mvn spring-boot:run -pl echochat-user
mvn spring-boot:run -pl echochat-message
mvn spring-boot:run -pl echochat-group
mvn spring-boot:run -pl echochat-moment
mvn spring-boot:run -pl echochat-file
mvn spring-boot:run -pl echochat-search
mvn spring-boot:run -pl echochat-notification
```

### 3. 启动前端

```bash
cd echochat-frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`。

## API 设计规范

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1716710400000
}
```

### 错误码范围

| 范围 | 说明 |
|---|---|
| 200 | 成功 |
| 400-499 | 客户端错误 |
| 500-599 | 服务端错误 |
| 1000-1999 | 用户模块业务错误 |
| 2000-2999 | 消息模块业务错误 |
| 3000-3999 | 群组模块业务错误 |
| 4000-4999 | 朋友圈模块业务错误 |
| 5000-5999 | 通知模块业务错误 |

## 消息可靠性设计

```
客户端 ←→ WebSocket 长连接 ←→ Netty Server（echochat-message）
                                   │
                                   ├── 消息持久化（MySQL）
                                   ├── 消息入 RocketMQ（异步解耦）
                                   ├── 消费者：推送在线用户（WS）
                                   ├── 消费者：写离线消息表
                                   └── Redis 缓存会话当前最大 Seq
```

- **不丢**：消息先持久化再推送 + 客户端 ACK + 定时重推
- **不乱**：每会话维护单调递增 Seq，按 Seq 排序
- **不重**：客户端 Seq 去重 + 服务端幂等

## Redis Key 设计

| Key 模式 | 类型 | 说明 |
|---|---|---|
| `token:access:{uid}:{deviceId}` | String | AccessToken |
| `token:refresh:{uid}:{deviceId}` | String | RefreshToken |
| `token:blacklist:{hash}` | String | 失效 Token 黑名单 |
| `user:online:{uid}` | Set | 在线设备集合 |
| `seq:{sessionType}:{targetId}` | String | 会话当前最大 Seq（单聊键为 `seq:single:{minUid}:{maxUid}`） |
| `unread:{uid}:{sessionType}:{targetId}` | String | 实时未读数 |
| `rate:ip:{ip}` | String | IP 限流计数器（另有 `rate:login:{ip}`、`rate:register:{ip}`、`rate:msg:{uid}`、`rate:fwd:{uid}`） |

## 开发规范

本项目遵循《阿里巴巴 Java 开发手册》规范，并集成了 p3c-pmd 自动检测插件：

```bash
# 运行 p3c 代码规范检查
mvn pmd:check

# 跳过检查编译
mvn compile -Dpmd.skip=true
```

## License

MIT

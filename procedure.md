角色	技术框架/工具	说明
客户端	JavaFX	现代化Java桌面UI框架，写聊天界面
网络通信	Java WebSocket 或 Java Socket (TCP)	长连接，实时消息收发
后端	Spring Boot + Spring WebSocket	快速搭建服务端，处理连接和消息
数据库	MySQL 或 SQLite	存储用户信息、消息记录
序列化/消息格式	JSON (Jackson/Gson)	简单易用的消息格式
安全	TLS加密WebSocket，密码加密（BCrypt）	保障数据传输和存储安全
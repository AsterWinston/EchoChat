-- ============================================================
-- EchoChat 数据库初始化脚本
-- 包含全部 27 张业务表 DDL + 测试数据
-- 
-- 测试账号:
--   Alice:  uid=1000000000000000001, password=123456, email=alice@example.com
--   Bob:    uid=1000000000000000002, password=123456, email=bob@example.com

-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS echochat

DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE echochat;

-- 用户与身份

CREATE TABLE IF NOT EXISTS `user` (
    `uid`         BIGINT       NOT NULL COMMENT '用户唯一ID（雪花算法生成）',
    `nickname`    VARCHAR(64)  NOT NULL COMMENT '昵称',
    `email`       VARCHAR(128)          COMMENT '邮箱',
    `password`    VARCHAR(256) NOT NULL COMMENT 'BCrypt 加密密码',
    `avatar`      VARCHAR(512)          COMMENT '头像 URL',
    `signature`   VARCHAR(512)          COMMENT '个性签名',
    `gender`      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别: 0=未知, 1=男, 2=女',
    `age`         INT                   COMMENT '年龄',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '在线状态: 0=离线, 1=在线',
    `last_seen`   DATETIME              COMMENT '最后在线时间',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否注销: 0=正常, 1=已注销',
    PRIMARY KEY (`uid`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';


CREATE TABLE IF NOT EXISTS `user_device` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`             BIGINT       NOT NULL COMMENT '用户 UID',
    `device_id`       VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    `platform`        VARCHAR(32)           COMMENT '平台: web/ios/android',
    `ip`              VARCHAR(64)           COMMENT '登录 IP',
    `login_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `last_active_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    `push_token`      VARCHAR(512)          COMMENT '推送 Token',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_device` (`uid`, `device_id`),
    KEY `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设备表';

-- 好友与关系

CREATE TABLE IF NOT EXISTS `friend` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `user_uid`    BIGINT      NOT NULL COMMENT '用户 UID',
    `friend_uid`  BIGINT      NOT NULL COMMENT '好友 UID',
    `group_name`  VARCHAR(64)          COMMENT '好友分组名: 家人/同事/同学...',
    `memo`        VARCHAR(256)         COMMENT '好友备注名/别名',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_uid`, `friend_uid`),
    KEY `idx_user_uid` (`user_uid`),
    KEY `idx_friend_uid` (`friend_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';


CREATE TABLE IF NOT EXISTS `friend_request` (
    `id`         BIGINT      NOT NULL COMMENT '主键',
    `from_uid`   BIGINT      NOT NULL COMMENT '申请人 UID',
    `to_uid`     BIGINT      NOT NULL COMMENT '被申请人 UID',
    `message`    VARCHAR(255)         COMMENT '申请消息（仅文本）',
    `status`     ENUM('pending','accepted','rejected','expired') NOT NULL DEFAULT 'pending' COMMENT '状态',
    `expire_at`  DATETIME    NOT NULL COMMENT '过期时间（默认 +3天）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `handled_at` DATETIME             COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_from_uid` (`from_uid`),
    KEY `idx_to_uid` (`to_uid`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';


CREATE TABLE IF NOT EXISTS `blacklist` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `uid`         BIGINT   NOT NULL COMMENT '用户 UID',
    `blocked_uid` BIGINT   NOT NULL COMMENT '被拉黑用户 UID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_blocked` (`uid`, `blocked_uid`),
    KEY `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';

-- 消息与会话

CREATE TABLE IF NOT EXISTS `message` (
    `msg_id`           BIGINT      NOT NULL COMMENT '消息唯一 ID',
    `session_type`     ENUM('single','group') NOT NULL COMMENT '会话类型',
    `from_uid`         BIGINT      NOT NULL COMMENT '发送者 UID',
    `to_id`            VARCHAR(64) NOT NULL COMMENT '接收者 ID: 好友 uid / 群 gid',
    `msg_type`         ENUM('TEXT','IMAGE','FILE','VOICE','VIDEO','SYSTEM') NOT NULL DEFAULT 'TEXT' COMMENT '消息类型',
    `content`          TEXT                 COMMENT '消息内容（JSON 或文本）',
    `status`           TINYINT     NOT NULL DEFAULT 0 COMMENT '消息状态: 0=发送中, 1=已送达, 2=已读',
    `is_recalled`      TINYINT     NOT NULL DEFAULT 0 COMMENT '是否撤回: 0=否, 1=是',
    `is_forwarded`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否转发: 0=否, 1=是',
    `forward_from_uid` BIGINT               COMMENT '转发来源用户 UID',
    `reply_to_msg_id`  BIGINT               COMMENT '被引用消息 ID',
    `mentioned_uids`   JSON                 COMMENT '被 @ 的用户 UID 列表',
    `seq`              BIGINT      NOT NULL COMMENT '会话内自增消息序号',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`msg_id`),
    KEY `idx_session_seq` (`session_type`, `to_id`, `seq`),
    KEY `idx_single_pair` (`session_type`, `from_uid`, `to_id`, `seq`),
    KEY `idx_from_uid` (`from_uid`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';


CREATE TABLE IF NOT EXISTS `offline_message` (
    `id`         BIGINT   NOT NULL COMMENT '主键',
    `uid`        BIGINT   NOT NULL COMMENT '接收者 UID',
    `msg_id`     BIGINT   NOT NULL COMMENT '消息 ID',
    `seq`        BIGINT   NOT NULL COMMENT '会话内序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_msg` (`uid`, `msg_id`),
    KEY `idx_uid_seq` (`uid`, `seq`),
    KEY `idx_msg_id` (`msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线消息表';


CREATE TABLE IF NOT EXISTS `conversation` (
    `id`           BIGINT      NOT NULL COMMENT '主键',
    `uid`          BIGINT      NOT NULL COMMENT '用户 UID',
    `session_type` ENUM('single','group') NOT NULL COMMENT '会话类型',
    `target_id`    VARCHAR(64) NOT NULL COMMENT '目标 ID: 好友 uid / 群 gid',
    `last_msg_id`  BIGINT               COMMENT '最后一条消息 ID',
    `unread_count` INT         NOT NULL DEFAULT 0 COMMENT '未读消息数',
    `is_pinned`    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否置顶: 0=否, 1=是',
    `dnd`          TINYINT     NOT NULL DEFAULT 0 COMMENT '免打扰: 0=关, 1=开',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_session_target` (`uid`, `session_type`, `target_id`),
    KEY `idx_uid_updated` (`uid`, `updated_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';


CREATE TABLE IF NOT EXISTS `message_read` (
    `id`      BIGINT   NOT NULL COMMENT '主键',
    `msg_id`  BIGINT   NOT NULL COMMENT '消息 ID',
    `uid`     BIGINT   NOT NULL COMMENT '已读用户 UID',
    `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_uid` (`msg_id`, `uid`),
    KEY `idx_uid_read_at` (`uid`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息已读记录表';


CREATE TABLE IF NOT EXISTS `pinned_message` (
    `id`              BIGINT      NOT NULL COMMENT '主键',
    `session_type`    ENUM('single','group') NOT NULL COMMENT '会话类型',
    `target_id`       VARCHAR(64) NOT NULL COMMENT '目标 ID',
    `msg_id`          BIGINT      NOT NULL COMMENT '被置顶消息 ID',
    `pinned_by`       BIGINT      NOT NULL COMMENT '置顶操作者 UID',
    `content_summary` VARCHAR(255)          COMMENT '消息内容摘要（用于置顶条展示）',
    `pinned_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '置顶时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_target_msg` (`session_type`, `target_id`, `msg_id`),
    KEY `idx_session_target` (`session_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息置顶表';

-- 消息软删除

CREATE TABLE IF NOT EXISTS `message_deletion` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `msg_id`     BIGINT   NOT NULL COMMENT '消息 ID',
    `uid`        BIGINT   NOT NULL COMMENT '删除该消息的用户 UID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_uid` (`msg_id`, `uid`),
    KEY `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息软删除表';

-- 群组

CREATE TABLE IF NOT EXISTS `group_info` (
    `gid`                BIGINT       NOT NULL COMMENT '群唯一 ID（雪花算法生成）',
    `name`               VARCHAR(128) NOT NULL COMMENT '群名称',
    `avatar`             VARCHAR(512)          COMMENT '群头像 URL',
    `owner_uid`          BIGINT       NOT NULL COMMENT '群主 UID',
    `announcement`       TEXT                  COMMENT '群公告',
    `slow_mode_interval` INT          NOT NULL DEFAULT 0 COMMENT '慢速模式间隔（秒），0=关闭',
    `mute_all`           TINYINT      NOT NULL DEFAULT 0 COMMENT '全员禁言，0=关，1=开',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`gid`),
    KEY `idx_owner_uid` (`owner_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组信息表';


CREATE TABLE IF NOT EXISTS `group_member` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gid`        BIGINT   NOT NULL COMMENT '群 ID',
    `uid`        BIGINT   NOT NULL COMMENT '成员 UID',
    `role`       ENUM('owner','admin','member') NOT NULL DEFAULT 'member' COMMENT '角色',
    `mute_until` DATETIME          COMMENT '禁言截止时间（NULL=未禁言）',
    `joined_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入群时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gid_uid` (`gid`, `uid`),
    KEY `idx_uid` (`uid`),
    KEY `idx_gid_role` (`gid`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员表';


CREATE TABLE IF NOT EXISTS `group_invite` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gid`        BIGINT       NOT NULL COMMENT '群 ID',
    `code`       VARCHAR(64)  NOT NULL COMMENT '邀请码（唯一随机字符串）',
    `expire_at`  DATETIME     NOT NULL COMMENT '过期时间',
    `used`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已使用: 0=未使用, 1=已使用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_gid` (`gid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群邀请链接表';

-- 朋友圈

CREATE TABLE IF NOT EXISTS `moment` (
    `moment_id`  BIGINT      NOT NULL COMMENT '动态唯一 ID',
    `uid`        BIGINT      NOT NULL COMMENT '发布者 UID',
    `content`    TEXT                 COMMENT '文字内容',
    `media`      JSON                 COMMENT '媒体文件列表 [{url,thumb_url,type,width,height}]，最多 9 个',
    `visibility` ENUM('public','restricted') NOT NULL DEFAULT 'public' COMMENT '可见性: public=公开, restricted=指定不可见',
    `show_range` VARCHAR(32)          COMMENT '展示范围: 3d/30d/180d/all',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT     NOT NULL DEFAULT 0 COMMENT '是否删除: 0=正常, 1=已删除',
    PRIMARY KEY (`moment_id`),
    KEY `idx_uid_created` (`uid`, `created_at` DESC),
    KEY `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈动态表';


CREATE TABLE IF NOT EXISTS `moment_like` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `moment_id`  BIGINT   NOT NULL COMMENT '动态 ID',
    `uid`        BIGINT   NOT NULL COMMENT '点赞用户 UID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_moment_uid` (`moment_id`, `uid`),
    KEY `idx_moment_id` (`moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈点赞表';


CREATE TABLE IF NOT EXISTS `moment_comment` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `moment_id`    BIGINT   NOT NULL COMMENT '动态 ID',
    `uid`          BIGINT   NOT NULL COMMENT '评论者 UID',
    `reply_to_uid` BIGINT            COMMENT '被回复的用户 UID（二级回复）',
    `content`      TEXT     NOT NULL COMMENT '评论内容（仅文字）',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (`id`),
    KEY `idx_moment_id` (`moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈评论表';


CREATE TABLE IF NOT EXISTS `moment_privacy` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `moment_id`  BIGINT NOT NULL COMMENT '动态 ID',
    `block_uid`  BIGINT NOT NULL COMMENT '被限制查看的用户 UID',
    PRIMARY KEY (`id`),
    KEY `idx_moment_id` (`moment_id`),
    KEY `idx_block_uid` (`block_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈隐私限制表';


CREATE TABLE IF NOT EXISTS `feed_timeline` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_uid`  BIGINT   NOT NULL COMMENT 'Feed 所有者 UID',
    `moment_id`  BIGINT   NOT NULL COMMENT '动态 ID',
    `author_uid` BIGINT   NOT NULL COMMENT '动态作者 UID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动态发布时间',
    PRIMARY KEY (`id`),
    KEY `idx_owner_created` (`owner_uid`, `created_at` DESC),
    KEY `idx_moment_id` (`moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈 Feed 时间线表（推模式收件箱）';

-- 收藏

CREATE TABLE IF NOT EXISTS `favorite` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`         BIGINT       NOT NULL COMMENT '用户 UID',
    `msg_id`      BIGINT       NOT NULL COMMENT '被收藏消息 ID',
    `msg_type`    ENUM('TEXT','IMAGE','FILE','VOICE','VIDEO') NOT NULL COMMENT '消息类型',
    `msg_summary` TEXT         NOT NULL COMMENT '消息内容摘要',
    `collected_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    KEY `idx_uid_type` (`uid`, `msg_type`),
    KEY `idx_uid_time` (`uid`, `collected_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- 通知系统

CREATE TABLE IF NOT EXISTS `notification` (
    `id`         BIGINT       NOT NULL COMMENT '通知唯一 ID',
    `uid`        BIGINT       NOT NULL COMMENT '接收者 UID',
    `type`       VARCHAR(32)  NOT NULL COMMENT '通知类型: friend_request/group_invite/system',
    `title`      VARCHAR(256) NOT NULL COMMENT '通知标题',
    `content`    TEXT                  COMMENT '通知内容',
    `related_id` BIGINT                COMMENT '关联业务 ID（如好友申请 ID）',
    `event_id`   BIGINT                COMMENT '通知事件 ID（MQ 消费幂等键）',
    `is_read`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读: 0=未读, 1=已读',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_uid_read` (`uid`, `is_read`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- 文件元信息

CREATE TABLE IF NOT EXISTS `file_meta` (
    `file_id`      BIGINT       NOT NULL COMMENT '文件唯一 ID',
    `uid`          BIGINT       NOT NULL COMMENT '上传者 UID',
    `bucket`       VARCHAR(64)  NOT NULL COMMENT 'MinIO Bucket 名称',
    `object_path`  VARCHAR(512) NOT NULL COMMENT 'MinIO 对象路径',
    `original_name` VARCHAR(256)         COMMENT '原始文件名',
    `ext`          VARCHAR(16)           COMMENT '文件扩展名',
    `size`         BIGINT                COMMENT '文件大小（字节）',
    `width`        INT                   COMMENT '图片/视频宽度',
    `height`       INT                   COMMENT '图片/视频高度',
    `duration`     INT                   COMMENT '语音/视频时长（秒）',
    `thumbnail_path` VARCHAR(512)         COMMENT '缩略图路径',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`file_id`),
    KEY `idx_uid` (`uid`),
    KEY `idx_bucket` (`bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元信息表';

-- 测试数据初始化
-- 说明：以下 BCrypt 哈希为占位值，密码明文 = "123456"

INSERT INTO `user` (`uid`, `nickname`, `email`, `password`, `avatar`, `signature`, `gender`, `age`, `status`, `last_seen`, `created_at`, `updated_at`)
VALUES
(1000000000000000001, 'Alice', 'alice@example.com',
 '$2b$10$7XhVcQ/Vc4ADm6GZW4Rwve717xEUusvYfHDhkAKJSx9EAJOpicdqq',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Alice', 'Hello, I am Alice!', 2, 25, 0, NULL, NOW(), NOW()),

(1000000000000000002, 'Bob', 'bob@example.com',
 '$2b$10$7XhVcQ/Vc4ADm6GZW4Rwve717xEUusvYfHDhkAKJSx9EAJOpicdqq',
 'https://api.dicebear.com/7.x/avataaars/svg?seed=Bob', 'Hi, Bob here!', 1, 28, 0, NULL, NOW(), NOW());


-- Alice 和 Bob 互为好友
INSERT INTO `friend` (`id`, `user_uid`, `friend_uid`, `group_name`, `created_at`)
VALUES
(3000000000000000001, 1000000000000000001, 1000000000000000002, '同事', NOW()),
(3000000000000000002, 1000000000000000002, 1000000000000000001, '同事', NOW());


-- Alice 的会话（与 Bob 的单聊）
INSERT INTO `conversation` (`id`, `uid`, `session_type`, `target_id`, `last_msg_id`, `unread_count`, `is_pinned`, `created_at`, `updated_at`)
VALUES
(5000000000000000001, 1000000000000000001, 'single', '1000000000000000002', NULL, 0, 0, NOW(), NOW()),
(5000000000000000002, 1000000000000000002, 'single', '1000000000000000001', NULL, 0, 0, NOW(), NOW());


-- 几条测试消息（Alice <-> Bob）
-- seq 从 1 开始递增，同一个会话（single:1000000000000000002）共用同一个 seq 序列
INSERT INTO `message` (`msg_id`, `session_type`, `from_uid`, `to_id`, `msg_type`, `content`, `status`, `is_recalled`, `is_forwarded`, `forward_from_uid`, `reply_to_msg_id`, `mentioned_uids`, `seq`, `created_at`)
VALUES
(4000000000000000001, 'single', 1000000000000000001, '1000000000000000002', 'TEXT', 'Hello Bob!', 2, 0, 0, NULL, NULL, NULL, 1, NOW()),
(4000000000000000002, 'single', 1000000000000000002, '1000000000000000001', 'TEXT', 'Hi Alice! How are you?', 2, 0, 0, NULL, NULL, NULL, 2, NOW()),
(4000000000000000003, 'single', 1000000000000000001, '1000000000000000002', 'TEXT', 'I am fine, thanks!', 2, 0, 0, NULL, NULL, NULL, 3, NOW());


-- 更新 Alice 的会话的最后一条消息 ID 和 seq 为 3
UPDATE `conversation` SET `last_msg_id` = 4000000000000000003 WHERE `id` = 5000000000000000001;
-- 同样更新 Bob 的会话
UPDATE `conversation` SET `last_msg_id` = 4000000000000000003 WHERE `id` = 5000000000000000002;

-- 测试群组数据 (Alice 创建，Bob 为成员)
INSERT INTO `group_info` (`gid`, `name`, `avatar`, `owner_uid`, `announcement`, `slow_mode_interval`, `created_at`, `updated_at`)
VALUES (6000000000000000001, 'EchoChat 测试群', NULL, 1000000000000000001, '欢迎来到测试群！', 0, NOW(), NOW());

INSERT INTO `group_member` (`gid`, `uid`, `role`, `mute_until`, `joined_at`)
VALUES
(6000000000000000001, 1000000000000000001, 'owner', NULL, NOW()),
(6000000000000000001, 1000000000000000002, 'member', NULL, NOW());

-- 群组入群申请表

DROP TABLE IF EXISTS `group_join_request`;
CREATE TABLE `group_join_request` (
    `id` BIGINT NOT NULL COMMENT '雪花ID主键',
    `gid` BIGINT NOT NULL COMMENT '群组ID',
    `uid` BIGINT NOT NULL COMMENT '申请入群的用户ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    `processed_by` BIGINT DEFAULT NULL COMMENT '处理该申请的管理员/群主ID',
    `message` VARCHAR(200) DEFAULT NULL COMMENT '申请附言',
    `created_at` DATETIME NOT NULL COMMENT '申请时间',
    `processed_at` DATETIME DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (`id`),
    INDEX `idx_gid_status` (`gid`, `status`),
    INDEX `idx_gid_uid` (`gid`, `uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组入群申请表';

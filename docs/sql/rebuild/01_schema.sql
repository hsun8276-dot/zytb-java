-- =====================================================
-- 志愿填报系统(zytb) - 表结构(01)
-- 可重复执行(CREATE TABLE IF NOT EXISTS)
-- 依赖: 数据库 zytb (utf8mb4)
-- =====================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------
-- 1. 用户表
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL COMMENT '登录账号(手机号)',
    password     VARCHAR(100) NOT NULL COMMENT 'BCrypt密码',
    real_name    VARCHAR(50)  DEFAULT '' COMMENT '真实姓名',
    nickname     VARCHAR(50)  DEFAULT '' COMMENT '昵称',
    phone        VARCHAR(20)  DEFAULT '' COMMENT '手机号',
    email        VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    avatar       VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    signature    VARCHAR(255) DEFAULT '' COMMENT '个人签名',
    gender       TINYINT      DEFAULT 0 COMMENT '0未知 1男 2女',
    status       TINYINT      DEFAULT 1 COMMENT '0停用 1正常',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT  DEFAULT 0,
    UNIQUE KEY uk_username (username),
    KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------------------------------
-- 2. 角色表(admin/user)
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    code  VARCHAR(30) NOT NULL COMMENT '角色编码: admin/user',
    name  VARCHAR(30) NOT NULL COMMENT '角色名称',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------------------------------
-- 3. 用户角色关联表
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

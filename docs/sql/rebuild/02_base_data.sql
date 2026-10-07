-- =====================================================
-- 志愿填报系统(zytb) - 基础数据(02)
-- 可重复执行(NOT EXISTS 防重)
-- 依赖: 01_schema.sql
-- =====================================================

SET NAMES utf8mb4;

-- 角色: 只有管理员和用户
INSERT INTO sys_role (code, name)
SELECT 'admin', '管理员' WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code='admin');
INSERT INTO sys_role (code, name)
SELECT 'user', '用户' WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code='user');

-- 管理员账号 admin/123456(密码为BCrypt, 与仓库外明文对应)
INSERT INTO sys_user (username, password, real_name, phone, avatar, status)
SELECT 'admin', '$2a$10$WJ.dawZIONGidWy7h65Ome5hPoXBeLYy/7bfc7HbpWpKEFt1YHM4y', '管理员', '', '', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username='admin');

-- 管理员绑定 admin 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.code='admin'
WHERE u.username='admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id);

-- 说明: 普通用户(user)账号不预置, 全部由管理员在系统中开通

-- =====================================================
-- 系统配置表(管理端可维护)
-- 可重复执行
-- =====================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value VARCHAR(500) NOT NULL DEFAULT '' COMMENT '配置值',
    remark       VARCHAR(200) DEFAULT '' COMMENT '说明',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'system_name', '砚衡AI云数据升学规划系统', '系统名称(登录页/管理端展示)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'system_name');

INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'membership_years', '1', '开通/续费默认年数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'membership_years');

INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'default_password', '123456', '管理员开通账号时的默认初始密码说明'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'default_password');

INSERT INTO sys_config (config_key, config_value, remark)
SELECT 'contact_hint', '如忘记密码, 请联系管理员重置', '忘记密码页提示文案'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'contact_hint');

-- =====================================================
-- 操作日志表(写操作由 OperationLogInterceptor 异步写入)
-- 可重复执行
-- =====================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       DEFAULT NULL COMMENT '操作人用户ID, 未登录可为空',
    module         VARCHAR(64)  DEFAULT '' COMMENT '模块(Swagger Tag / 服务名)',
    action         VARCHAR(128) DEFAULT '' COMMENT '动作(Swagger Operation summary)',
    request_params VARCHAR(2000) DEFAULT NULL COMMENT '请求方法+路径(+查询串), 不含请求体',
    response_data  VARCHAR(2000) DEFAULT NULL COMMENT '失败时的提示; 成功为空',
    ip             VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    duration       INT          DEFAULT NULL COMMENT '耗时毫秒',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    KEY idx_sys_log_user (user_id),
    KEY idx_sys_log_time (create_time),
    KEY idx_sys_log_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

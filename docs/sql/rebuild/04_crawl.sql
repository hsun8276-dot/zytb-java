-- =====================================================
-- 志愿填报系统(zytb) - 数据采集台账(04)
-- 可重复执行(CREATE TABLE IF NOT EXISTS)
-- 作用: 记录每一份从各省教育考试院获取的原始文件(来源页/附件/本地缓存/入库行数),
--       保证数据可追溯, 重跑幂等, 并作为"哪省哪年哪批次已完成"的进度表
-- =====================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_source (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    province      VARCHAR(30)  NOT NULL COMMENT '省份',
    year          INT          NOT NULL COMMENT '高考年份',
    data_type     VARCHAR(20)  NOT NULL COMMENT '数据类型: score_ranking(一分一段)/score_line(投档线)/plan(招生计划)',
    batch         VARCHAR(30)  DEFAULT '' COMMENT '批次(本科批/专科批), 一分一段为空',
    subject_type  VARCHAR(20)  DEFAULT '' COMMENT '科类(物理类/历史类/综合/文科/理科), 混合文件为空',
    page_url      VARCHAR(500) DEFAULT '' COMMENT '考试院公告页URL',
    file_url      VARCHAR(500) DEFAULT '' COMMENT '附件直链(xlsx/pdf/图片)',
    local_file    VARCHAR(300) DEFAULT '' COMMENT '本地缓存路径(data_raw/下)',
    file_format   VARCHAR(10)  DEFAULT '' COMMENT 'xlsx/pdf/html/image',
    rows_loaded   INT          DEFAULT 0 COMMENT '入库行数',
    status        VARCHAR(20)  DEFAULT 'pending' COMMENT 'pending/downloaded/loaded/failed/skipped',
    note          VARCHAR(500) DEFAULT '' COMMENT '备注(跳过原因/解析说明)',
    fetched_at    DATETIME DEFAULT NULL COMMENT '下载时间',
    loaded_at     DATETIME DEFAULT NULL COMMENT '入库时间',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_src (province, year, data_type, batch, subject_type, file_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试院数据采集台账(来源可追溯/进度表)';

-- score_line 补列(MySQL 8 无 ADD COLUMN IF NOT EXISTS, 由 tools/crawler/apply_sql.py 执行并跳过已存在列)
-- subject_type: 物理类/历史类(新高考) 或 理科/文科(传统省份) 或 综合(3+3 省份)
-- remark: 考试院备注原文(如 中外合作办学/定向 等)
ALTER TABLE score_line ADD COLUMN subject_type VARCHAR(20) DEFAULT '' COMMENT '科类: 物理类/历史类/理科/文科/综合' AFTER batch;
ALTER TABLE score_line ADD COLUMN remark VARCHAR(500) DEFAULT '' COMMENT '考试院备注原文';
ALTER TABLE score_line ADD COLUMN group_code VARCHAR(20) DEFAULT '' COMMENT '院校专业组代码(专业组投档省份用, 河北这类专业+院校模式为空)' AFTER major_code;
ALTER TABLE score_line ADD COLUMN plan_count INT DEFAULT NULL COMMENT '计划数(部分省份投档表自带)';
ALTER TABLE score_line ADD COLUMN tiebreak VARCHAR(200) DEFAULT '' COMMENT '同分排序项原文(河北: 语数之和/语数最高/外语/首选/再选最高/再选次高/志愿号)';
ALTER TABLE score_line ADD INDEX idx_dim_subject (province, year, batch, subject_type);

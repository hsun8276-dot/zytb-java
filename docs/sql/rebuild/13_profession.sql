-- 综合查询工具 · 职业库（与填报无关）
-- 数据：docs/sql/tools/import_profession.py 从参考站 9003 拉取

CREATE TABLE IF NOT EXISTS profession (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    occ_id            INT          NOT NULL COMMENT '源站 occId',
    occ_name          VARCHAR(100) NOT NULL COMMENT '职业名称',
    industry          VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '行业',
    occ_type          VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '职业类别',
    position_salary   VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '岗位薪资',
    definition        TEXT         NULL COMMENT '职业定义',
    occ_introduction  TEXT         NULL COMMENT '职业介绍/工作内容',
    job_require       TEXT         NULL COMMENT '岗位要求',
    occ_trait         TEXT         NULL COMMENT '职业特点',
    development_space TEXT         NULL COMMENT '发展空间',
    work_contact      TEXT         NULL COMMENT '工作交往',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_occ_id (occ_id),
    KEY idx_industry (industry),
    KEY idx_occ_type (occ_type),
    KEY idx_name (occ_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·职业库';

CREATE TABLE IF NOT EXISTS profession_major (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    occ_id     INT          NOT NULL,
    major_code VARCHAR(20)  NOT NULL DEFAULT '',
    major_name VARCHAR(100) NOT NULL DEFAULT '',
    bachelor   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '授予学位',
    duration   VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '学制',
    UNIQUE KEY uk_occ_major (occ_id, major_code),
    KEY idx_occ (occ_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·职业关联专业';

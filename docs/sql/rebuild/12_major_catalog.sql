-- 综合查询工具 · 专业目录(普本/职本/专科)
-- 与填报用 major 表分离；数据可用 docs/sql/tools/import_major_catalog.py 从参考站拉取

CREATE TABLE IF NOT EXISTS major_catalog (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    edu_level        TINYINT      NOT NULL COMMENT '1普本 2职本 3专科',
    major_code       VARCHAR(20)  NOT NULL COMMENT '专业国标代码',
    major_name       VARCHAR(100) NOT NULL COMMENT '专业名称',
    category         VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '学科门类/大类',
    first_discipline VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '专业类',
    group_code       VARCHAR(10)  NOT NULL DEFAULT '' COMMENT '专业类国标码',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_level_code (edu_level, major_code),
    KEY idx_level_group (edu_level, group_code),
    KEY idx_level_cat (edu_level, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·专业目录(普本/职本/专科)';

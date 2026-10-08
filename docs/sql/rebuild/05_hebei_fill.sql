-- =====================================================
-- 志愿填报系统(zytb) - 河北志愿填报数据与填报会话表(05)
-- 可重复执行, 依赖 03_aspiration.sql
-- 数据来源(用户提供, 见 docs/功能说明.md 第7节):
--   recruit_major / recruit_major_history  ← 4 个河北志愿数据 xlsx(本科/专科 × 物理/历史)
--   control_line                           ← docs/sql/tools/data/hebei_control_lines.csv
--   score_ranking(03 已建)                 ← 2021-2026年 河北高考一分一档表.xlsx
--   recruit_major.pre_min_mark             ← score_line 2026 官方投档最低分(与参考系统 PreMinMark 609/609 一致)
-- 导入脚本: docs/sql/tools/hebei_import.py
-- =====================================================

SET NAMES utf8mb4;

-- 03 里预留的推荐物化两表从未写入数据, 由下面的 recruit_major + aspiration_recommend 取代
DROP TABLE IF EXISTS aspiration_result_history;
DROP TABLE IF EXISTS aspiration_result;

-- ----------------------------------------------------
-- 1. 招生专业表: 一行 = 当年(2026)一个"院校+专业"志愿单元, 四个 Tab 的数据都从这里出
--    recruit_major_id 规则与参考系统一致: {yy}冀{本|专}{理|文}{院校代码4位}-{专业代码2位}, 如 26冀本理0458-07
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS recruit_major (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    recruit_major_id   VARCHAR(30)  NOT NULL COMMENT '志愿单元ID, 如 26冀本理0458-07',
    uab_id             VARCHAR(20)  NOT NULL COMMENT '院校招生单元ID, 如 26冀本理0458',
    province           VARCHAR(30)  NOT NULL DEFAULT '河北' COMMENT '招生省份',
    year               INT          NOT NULL COMMENT '招生年份',
    batch              VARCHAR(20)  NOT NULL COMMENT '本科批/专科批',
    subject_type       VARCHAR(20)  NOT NULL COMMENT '物理类/历史类',

    /* ---- 院校维度 ---- */
    unv_code           CHAR(4)      NOT NULL COMMENT '院校代码(当年)',
    unv_name           VARCHAR(200) NOT NULL COMMENT '院校招生名称[性质], 优先取当年官方投档表写法, 如 广西财经学院(南宁市)[公办]',
    unv_short_name     VARCHAR(150) DEFAULT '' COMMENT '院校名称(不带城市与性质)',
    unv_nature         VARCHAR(30)  DEFAULT '' COMMENT '办学性质: 公办/民办/独立学院/中外合作办学/内地与港澳台地区合作办学...',
    unv_type           VARCHAR(20)  DEFAULT '' COMMENT '院校类型: 综合/理工/师范/财经...',
    unv_province       VARCHAR(20)  DEFAULT '' COMMENT '院校所在省份',
    unv_city           VARCHAR(30)  DEFAULT '' COMMENT '院校所在城市',
    city_level         VARCHAR(10)  DEFAULT '' COMMENT '城市评级: 一线/新一线/二线...',
    unv_rank           INT          DEFAULT NULL COMMENT '院校排名(无=NULL)',
    rk_rank            VARCHAR(60)  DEFAULT '' COMMENT '软科校排原文',
    authority          VARCHAR(100) DEFAULT '' COMMENT '主管部门',
    unv_level          VARCHAR(600) DEFAULT '' COMMENT '院校水平原文(/985/211/双一流大学/保研资格...)',
    unv_background     VARCHAR(300) DEFAULT '' COMMENT '院校背景',
    unv_attributes     VARCHAR(30)  DEFAULT '' COMMENT '院校性质标签(逗号分隔): 双一流,985,211',
    has_master         TINYINT      DEFAULT 0 COMMENT '院校有硕士点',
    has_doctor         TINYINT      DEFAULT 0 COMMENT '院校有博士点',
    can_recommend      TINYINT      DEFAULT 0 COMMENT '院校有保研资格',
    vr_url             VARCHAR(500) DEFAULT '' COMMENT '校园VR(地图街景按钮)',
    baike_url          VARCHAR(500) DEFAULT '' COMMENT '院校百科',
    charter_url        VARCHAR(500) DEFAULT '' COMMENT '招生章程',
    admission_url      VARCHAR(500) DEFAULT '' COMMENT '学校招生信息',
    employment_url     VARCHAR(500) DEFAULT '' COMMENT '就业质量',
    first_time_admission TINYINT    DEFAULT 0 COMMENT '首次在冀招生的院校(数据源 新招院校)',

    /* ---- 专业维度 ---- */
    recruit_major_code CHAR(2)      NOT NULL COMMENT '专业代码(当年志愿表里的2位代码)',
    major_name         VARCHAR(300) NOT NULL COMMENT '专业名称',
    major_note         VARCHAR(1500) DEFAULT '' COMMENT '专业简注',
    major_category     VARCHAR(200) DEFAULT '' COMMENT '专业类(如 /计算机类)',
    attention          VARCHAR(500) DEFAULT '' COMMENT '注意事项(色盲色弱限制等)',
    recruit_type       VARCHAR(60)  DEFAULT '' COMMENT '招生类型: 普通计划/中外合作办学/地方专项计划/八协计划...',
    plan_explain       VARCHAR(1600) DEFAULT '' COMMENT '专业解释, 拼成参考系统写法: (普通类)(简注)',
    major_plan         INT          DEFAULT NULL COMMENT '当年计划人数',
    school_length      INT          DEFAULT NULL COMMENT '学制(年)',
    tuition            INT          DEFAULT NULL COMMENT '学费(元/年), 待定=NULL, 免费=0',
    tuition_text       VARCHAR(20)  DEFAULT '' COMMENT '学费原文(待定/免费)',
    required_subjects  VARCHAR(30)  DEFAULT '' COMMENT '必选科目(逗号分隔, 含首选): 物理,化学',
    major_codes        VARCHAR(800) DEFAULT '' COMMENT '对应教育部专业代码(逗号分隔), 关联 major.major_code',
    major_grade        VARCHAR(5)   DEFAULT '' COMMENT '学科评估等级(A+/A/B...)',
    major_rank         INT          DEFAULT NULL COMMENT '软科专业排名(无=NULL)',
    major_level        VARCHAR(600) DEFAULT '' COMMENT '专业水平原文(国家级一流本科...)',
    master_count       INT          DEFAULT 0 COMMENT '相关硕士点数',
    master_majors      VARCHAR(1500) DEFAULT '' COMMENT '硕士专业',
    doctor_count       INT          DEFAULT 0 COMMENT '相关博士点数',
    doctor_majors      VARCHAR(3000) DEFAULT '' COMMENT '博士专业',
    tuimian_rate       VARCHAR(20)  DEFAULT '' COMMENT '推免率',
    heat               DECIMAL(8,2) DEFAULT NULL COMMENT '上年热度',
    is_new_major       TINYINT      DEFAULT 0 COMMENT '新招专业',

    /* ---- 预测维度(当年) ---- */
    pre_min_mark       INT          DEFAULT NULL COMMENT '预测最低分 = 当年官方投档最低分(score_line), 未投出=NULL',
    pre_min_rank       INT          DEFAULT NULL COMMENT '预测最低分位次(当年一分一段累计)',
    pre_min_percentile DECIMAL(8,4) DEFAULT NULL COMMENT '预测最低分线上百分位',
    pre_avg_mark       INT          DEFAULT NULL COMMENT '预测平均分(数据源无, 留空)',
    ref_mark           INT          DEFAULT NULL COMMENT '数据源 26参考分(仅留档对照)',
    ref_rank           INT          DEFAULT NULL COMMENT '数据源 26参考位',
    attention_type     VARCHAR(10)  DEFAULT 'No' COMMENT '预测分提示: Danger=无任何历史数据',

    /* ---- 院校级上年数据(数据源自带) ---- */
    unv_min_mark_last  INT          DEFAULT NULL COMMENT '上年院校最低分',
    unv_min_rank_last  INT          DEFAULT NULL COMMENT '上年院校最低位次',
    unv_avg_mark_last  INT          DEFAULT NULL COMMENT '上年院校平均分',
    unv_avg_rank_last  INT          DEFAULT NULL COMMENT '上年院校平均位次',

    source_file        VARCHAR(100) DEFAULT '' COMMENT '来源文件',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rid (recruit_major_id),
    KEY idx_recommend (province, year, batch, subject_type, pre_min_mark),
    KEY idx_uab (uab_id),
    KEY idx_unv_name (unv_name),
    KEY idx_major_name (major_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招生专业表(当年志愿单元+院校/专业属性+预测分)';

-- ----------------------------------------------------
-- 2. 招生专业历年数据: 每个志愿单元往年(2021~2025)的计划/最低分/平均分/位次 + 换算到当年的等效分
--    等效算法: 百分位 = (1 - 位次/当年本科线上累计) × 100, 等效分 = 目标年一分一段中百分位最接近的分数档
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS recruit_major_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    recruit_major_id    VARCHAR(30)  NOT NULL COMMENT 'recruit_major.recruit_major_id',
    year                INT          NOT NULL COMMENT '历史年份',
    equivalent_year     INT          NOT NULL COMMENT '等效换算目标年份',
    hist_recruit_major_id VARCHAR(30) DEFAULT NULL COMMENT '当年的志愿单元ID(需当年投档线追踪代码, 暂缺=NULL)',
    plan                INT          DEFAULT NULL COMMENT '当年计划人数',
    min_mark            INT          DEFAULT NULL COMMENT '原始最低分',
    min_mark_rank       INT          DEFAULT NULL COMMENT '最低分位次',
    min_mark_percentile DECIMAL(8,4) DEFAULT NULL COMMENT '最低分线上百分位',
    equivalent_min_mark INT          DEFAULT NULL COMMENT '等效最低分',
    avg_mark            INT          DEFAULT NULL COMMENT '原始平均分',
    avg_mark_rank       INT          DEFAULT NULL COMMENT '平均分位次(一分一段换算)',
    avg_mark_percentile DECIMAL(8,4) DEFAULT NULL COMMENT '平均分线上百分位',
    equivalent_avg_mark INT          DEFAULT NULL COMMENT '等效平均分',
    UNIQUE KEY uk_rid_year (recruit_major_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招生专业历年数据(含等效分)';

-- ----------------------------------------------------
-- 3. 省控制线(批次线): 等效分/百分位的分母(线上累计人数)与推荐下限
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS control_line (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    province      VARCHAR(30) NOT NULL COMMENT '省份',
    year          INT         NOT NULL COMMENT '年份',
    subject_type  VARCHAR(20) NOT NULL COMMENT '物理类/历史类',
    batch         VARCHAR(30) NOT NULL COMMENT '本科批/专科批/特殊类型招生',
    score         INT         NOT NULL COMMENT '控制分数线',
    cumulative    INT         DEFAULT NULL COMMENT '线上累计人数(一分一段该分数累计)',
    source        VARCHAR(200) DEFAULT '' COMMENT '来源说明',
    UNIQUE KEY uk_line (province, year, subject_type, batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='省控制线(批次线)';

-- ----------------------------------------------------
-- 4. 智能推荐结果(物化): 点「智能筛选」时按分数区间+三维筛选算出, 「智能专业推荐」Tab 读这里
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_recommend (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code  VARCHAR(36) NOT NULL COMMENT '方案码',
    recruit_major_id VARCHAR(30) NOT NULL COMMENT '志愿单元ID',
    sort_no          INT         NOT NULL COMMENT '推荐顺序(预测最低分降序)',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_rid (aspiration_code, recruit_major_id),
    KEY idx_code_sort (aspiration_code, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-智能推荐结果';

-- ----------------------------------------------------
-- 5. 已选志愿(志愿表): 「查看结果」Tab, 顺序即志愿顺序, 上限按省份(河北 96)
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_major (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code  VARCHAR(36) NOT NULL COMMENT '方案码',
    recruit_major_id VARCHAR(30) NOT NULL COMMENT '志愿单元ID',
    sort_no          INT         NOT NULL COMMENT '志愿顺序(1起)',
    is_new           TINYINT     DEFAULT 1 COMMENT '新添加高亮(清除高亮后置0)',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_rid (aspiration_code, recruit_major_id),
    KEY idx_code_sort (aspiration_code, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-已选志愿(志愿表)';

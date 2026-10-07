-- =====================================================
-- 志愿填报系统(zytb) - 志愿填报模块表结构(03)
-- 面向全国高考生志愿填报(3+1+2 模式), 业务: 填报师替学生做志愿方案
-- 可重复执行(CREATE TABLE IF NOT EXISTS)
-- 依赖: 01_schema.sql(sys_user 等账号表)
-- =====================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------
-- 1. 志愿方案表(aspiration): 一行=一个学生的志愿填报任务
--    点击「填报志愿」时生成 aspiration_code(UUID), 后续筛选/推荐/志愿表/交单全部挂在方案码下
--    字段分四段: ①学生核心信息(创建后不可改) ②志愿信息(可改) ③采集扩展信息(学生扫码数据采集二维码补全) ④推荐参数与交单状态
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code  VARCHAR(36)  NOT NULL COMMENT '方案码(UUID): 填报会话主键, 筛选条件/推荐结果/志愿表都挂在它下面',
    user_id          BIGINT       NOT NULL COMMENT '创建人(填报师)ID, 关联sys_user',

    /* ---- ①学生核心信息(创建后不可修改) ---- */
    student_name     VARCHAR(50)  NOT NULL COMMENT '学生姓名',
    gender           TINYINT      DEFAULT 1 COMMENT '性别 0未知 1男 2女',
    province         VARCHAR(30)  DEFAULT '河北' COMMENT '省份',
    year             INT          DEFAULT 2026 COMMENT '高考年份',
    batch            VARCHAR(30)  DEFAULT '本科批' COMMENT '批次(本科批/专科批)',
    group_name       VARCHAR(20)  DEFAULT '' COMMENT '组别(物理组/历史组), 由首选科目决定',
    score            INT          DEFAULT NULL COMMENT '高考分数',
    bonus_score      INT          DEFAULT 0 COMMENT '政策加分',
    first_subject    VARCHAR(10)  DEFAULT '物理' COMMENT '首选科目(3+1+2的1): 物理/历史',
    second_subjects  VARCHAR(50)  DEFAULT '' COMMENT '次选科目(3+1+2的2, 逗号分隔): 化学,生物 等',

    /* ---- ②志愿信息(可修改, 对应列表编辑按钮) ---- */
    aspiration_name  VARCHAR(100) DEFAULT '' COMMENT '志愿名称(列表中蓝色链接, 点击打开志愿录取情况报告)',
    high_school      VARCHAR(100) DEFAULT '' COMMENT '就读高中',
    contact_phone    VARCHAR(20)  DEFAULT '' COMMENT '联系电话',

    /* ---- ③采集扩展信息(数据采集二维码由学生/家长扫码补全) ---- */
    ethnicity        VARCHAR(20)  DEFAULT '' COMMENT '民族',
    is_fresh         TINYINT      DEFAULT 1 COMMENT '是否应届 0否 1是',
    chinese_score    INT          DEFAULT NULL COMMENT '语文成绩',
    math_score       INT          DEFAULT NULL COMMENT '数学成绩',
    english_score    INT          DEFAULT NULL COMMENT '外语成绩',
    elective1_score  INT          DEFAULT NULL COMMENT '选科第1科成绩(首选科目)',
    elective2_score  INT          DEFAULT NULL COMMENT '选科第2科成绩',
    elective3_score  INT          DEFAULT NULL COMMENT '选科第3科成绩',
    height_cm        DECIMAL(5,1) DEFAULT NULL COMMENT '身高(cm)',
    weight_kg        DECIMAL(5,1) DEFAULT NULL COMMENT '体重(kg)',
    color_blind      TINYINT      DEFAULT 0 COMMENT '是否色盲 0否 1是',
    color_weakness   TINYINT      DEFAULT 0 COMMENT '是否色弱 0否 1是',
    left_vision      VARCHAR(10)  DEFAULT '' COMMENT '左眼视力',
    right_vision     VARCHAR(10)  DEFAULT '' COMMENT '右眼视力',
    parent_name      VARCHAR(50)  DEFAULT '' COMMENT '父母姓名',
    father_job       VARCHAR(50)  DEFAULT '' COMMENT '父亲职业',
    mother_job       VARCHAR(50)  DEFAULT '' COMMENT '母亲职业',

    /* ---- ④推荐参数与交单状态 ---- */
    upper_param      INT          DEFAULT 20 COMMENT '智能推荐: 向上分数(滑杆), 如20=提档线最高到考生分+20',
    lower_param      INT          DEFAULT 60 COMMENT '智能推荐: 向下分数(滑杆), 如60=提档线最低到考生分-60',
    submit_status    TINYINT      DEFAULT 0 COMMENT '交单状态 0未交单(可填报志愿) 1已交单(志愿表锁定)',
    submitter        VARCHAR(50)  DEFAULT '' COMMENT '交单操作人账号(如交单员01)',
    submit_time      DATETIME     DEFAULT NULL COMMENT '交单时间',

    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT  DEFAULT 0,
    UNIQUE KEY uk_code (aspiration_code),
    KEY idx_user (user_id),
    KEY idx_student_name (student_name),
    KEY idx_batch (province, year, batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案表: 学生志愿填报任务(3+1+2)';

-- ----------------------------------------------------
-- 2. 专业筛选白名单表(对应「选择专业筛选条件」弹窗)
--    弹窗内添加的专业逐条持久化(参考系统 AddMajors 接口), 推荐计算时只保留白名单内专业
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_major_filter (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code VARCHAR(36) NOT NULL COMMENT '方案码',
    major_id        BIGINT      NOT NULL COMMENT '专业字典表major.id',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_major (aspiration_code, major_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-专业筛选白名单';

-- ----------------------------------------------------
-- 3. 院校筛选排除条件表(对应「选择院校筛选条件」弹窗)
--    参考系统存"未勾选"的排除数组(空=全包含), 故本表存被排除的值
--    filter_group: province=地区 / unv_type=院校类型 / unv_attribute=院校性质(985/211/双一流/其它) / unv_nature=院校属性(公办/民办/合作办学)
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_univ_filter (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code VARCHAR(36) NOT NULL COMMENT '方案码',
    filter_group    VARCHAR(20) NOT NULL COMMENT '筛选组: province/unv_type/unv_attribute/unv_nature',
    filter_value    VARCHAR(30) NOT NULL COMMENT '被排除的值, 如: 新疆/民办/中外合作办学',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_group_val (aspiration_code, filter_group, filter_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-院校筛选排除条件';

-- ----------------------------------------------------
-- 4. 其它筛选条件表(对应「其它筛选条件」弹窗, 一方案一行)
--    学制/学费区间/含学费未知/包含与不包含关键字(预设复选+自定义逗号分隔)
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_other_filter (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code VARCHAR(36)  NOT NULL COMMENT '方案码',
    school_length   INT          DEFAULT NULL COMMENT '学制(NULL=不限)',
    tuition_min     INT          DEFAULT NULL COMMENT '学费区间下限(NULL=不限)',
    tuition_max     INT          DEFAULT NULL COMMENT '学费区间上限(NULL=不限)',
    include_unknown_tuition TINYINT DEFAULT 1 COMMENT '是否包含学费未知的专业 0否 1是',
    preset_include  VARCHAR(100) DEFAULT '' COMMENT '专业包含预设关键字(逗号分隔): 中外合作,专项,预科,少数民族,八协,日语,俄语,法语',
    preset_exclude  VARCHAR(100) DEFAULT '' COMMENT '专业不包含预设关键字(逗号分隔)',
    custom_include  VARCHAR(255) DEFAULT '' COMMENT '其它包含关键字(逗号分隔, 匹配专业名与备注文本)',
    custom_exclude  VARCHAR(255) DEFAULT '' COMMENT '其它不包含关键字(逗号分隔)',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (aspiration_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-其它筛选条件';

-- ----------------------------------------------------
-- 5. 推荐物化结果表: 智能推荐/筛选后命中的"院校+专业"志愿单元
--    参考系统 GetRecommendMajors 的行结构(46字段精简); 数据规模按全国346万+专业设计
--    uab_id 规则(与参考一致): {年份}{省}{批次科类}{院校代码}-{专业代码}, 如 26冀本理1869-30
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_result (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    aspiration_code    VARCHAR(36)  NOT NULL COMMENT '方案码',
    unit_key           VARCHAR(60)  NOT NULL COMMENT '志愿单元键: {年}{省}{批次}{院校代码}-{专业代码}',
    year               INT          NOT NULL COMMENT '数据年份(招生年份)',
    province           VARCHAR(30)  DEFAULT '' COMMENT '省份',
    batch              VARCHAR(30)  DEFAULT '' COMMENT '批次',
    college_code       INT          DEFAULT NULL COMMENT '院校代码(数据源)',
    college_name       VARCHAR(150) DEFAULT '' COMMENT '院校名称[性质]',
    college_property   VARCHAR(20)  DEFAULT '' COMMENT '公办/民办/独立学院',
    college_type       VARCHAR(30)  DEFAULT '' COMMENT '综合/工科/财经...',
    city               VARCHAR(80)  DEFAULT '' COMMENT '所在城市',
    unv_rank           INT          DEFAULT NULL COMMENT '排名列: 考生位次与该校录取位次的差值',
    unv_plan           INT          DEFAULT NULL COMMENT '院校当年计划总数',
    has_master         TINYINT      DEFAULT 0 COMMENT '有硕士点',
    has_doctor         TINYINT      DEFAULT 0 COMMENT '有博士点',
    first_admission    TINYINT      DEFAULT 0 COMMENT '首次招生',
    major_code         VARCHAR(10)  DEFAULT '' COMMENT '专业代码(志愿表中的专业代码, 如07)',
    major_name         VARCHAR(200) DEFAULT '' COMMENT '专业名称',
    major_plan         INT          DEFAULT NULL COMMENT '该专业计划人数',
    school_length      INT          DEFAULT NULL COMMENT '学制(年)',
    tuition            INT          DEFAULT NULL COMMENT '学费(元/年), NULL=未知',
    plan_explain       VARCHAR(500) DEFAULT '' COMMENT '专业解释(普通类/中外合作说明/色盲限制等)',
    required_subjects  VARCHAR(50)  DEFAULT '' COMMENT '再选科目要求, 如: 物理,化学',
    probability        VARCHAR(20)  DEFAULT '未知' COMMENT '录取概率显示值(如2.1%/未知)',
    pre_min_mark       INT          DEFAULT NULL COMMENT '预测最低分',
    pre_avg_mark       INT          DEFAULT NULL COMMENT '预测平均分',
    pre_min_rank       INT          DEFAULT NULL COMMENT '预测最低分位次',
    pre_min_percentile DECIMAL(6,3) DEFAULT NULL COMMENT '预测最低分百分位',
    selected           TINYINT      DEFAULT 0 COMMENT '是否已加入志愿表 0候选 1已加入',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_code (aspiration_code),
    KEY idx_code_selected (aspiration_code, selected)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-推荐物化结果(候选专业池)';

-- ----------------------------------------------------
-- 6. 推荐结果历年等效数据表: 每个结果单元对应 2025/2024/2023 等多年历史
--    等效=按一分一段把往年原始分换算到目标年份(位次不变换算分数), 不同年份数据才可比
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS aspiration_result_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    result_id           BIGINT       NOT NULL COMMENT 'aspiration_result.id',
    year                INT          NOT NULL COMMENT '历史数据年份(如2025)',
    equivalent_year     INT          NOT NULL COMMENT '等效换算目标年份(如2026)',
    plan                INT          DEFAULT NULL COMMENT '当年计划人数',
    min_mark            INT          DEFAULT NULL COMMENT '当年原始最低分(提档线)',
    equivalent_min_mark INT          DEFAULT NULL COMMENT '等效最低分(换算到目标年份)',
    avg_mark            INT          DEFAULT NULL COMMENT '当年原始平均分',
    equivalent_avg_mark INT          DEFAULT NULL COMMENT '等效平均分',
    min_mark_rank       INT          DEFAULT NULL COMMENT '最低分位次',
    avg_mark_rank       INT          DEFAULT NULL COMMENT '平均分位次',
    min_percentile      DECIMAL(6,3) DEFAULT NULL COMMENT '最低分百分位',
    KEY idx_result (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案-推荐结果历年等效数据';

-- ----------------------------------------------------
-- 7. 专业字典表(用户拍板: 专业需独立数据表维护)
--    教育部专业目录: 门类(工学/经济学...) > 一级学科(力学类/机械类...) > 专业(080102工程力学)
--    「选择专业筛选条件」弹窗的勾选池与下拉都从本表出, 需要维护入口
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS major (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    major_code       VARCHAR(10)  NOT NULL COMMENT '专业代码(教育部, 如080102, 含T/K后缀)',
    major_name       VARCHAR(100) NOT NULL COMMENT '专业名称',
    first_discipline VARCHAR(50)  DEFAULT '' COMMENT '所属一级学科(如力学类)',
    category         VARCHAR(30)  DEFAULT '' COMMENT '所属学科门类(如工学)',
    remark           VARCHAR(255) DEFAULT '' COMMENT '备注',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (major_code),
    KEY idx_name (major_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业字典表(教育部专业目录, 需维护)';

-- ----------------------------------------------------
-- 8. 一分一段表: 位次/同分人数/等效分换算的数据基石
--    累计人数=该分数的位次; 同分人数=相邻累计人数差
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS score_ranking (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    province    VARCHAR(30) NOT NULL COMMENT '省份',
    year        INT         NOT NULL COMMENT '年份',
    subject_type VARCHAR(20) NOT NULL COMMENT '科类: 物理类/历史类(或理科/文科)',
    score       INT         NOT NULL COMMENT '分数',
    same_count  INT         NOT NULL DEFAULT 0 COMMENT '该分数人数',
    cumulative  INT         NOT NULL DEFAULT 0 COMMENT '累计人数(=该分数位次)',
    UNIQUE KEY uk_dim_score (province, year, subject_type, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一分一段表(位次/同分人数/等效分换算依据)';

-- ----------------------------------------------------
-- 9. 院校专业提档线表: 志愿录取情况报告的红黄绿标色 + 推荐分数区间匹配的数据源
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS score_line (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    province           VARCHAR(30)  NOT NULL COMMENT '省份(招生面向)',
    year               INT          NOT NULL COMMENT '年份',
    batch              VARCHAR(30)  DEFAULT '本科批' COMMENT '批次',
    college_code       INT          DEFAULT NULL COMMENT '院校代码(数据源)',
    college_name       VARCHAR(150) DEFAULT '' COMMENT '院校名称[性质]',
    major_code         VARCHAR(10)  DEFAULT '' COMMENT '专业代码(志愿表中的专业代码, 如07)',
    major_name         VARCHAR(200) DEFAULT '' COMMENT '专业名称',
    subject_requirement VARCHAR(50) DEFAULT '' COMMENT '选科要求, 如: 物理,化学',
    property           VARCHAR(20)  DEFAULT '' COMMENT '公办/民办',
    min_mark           INT          DEFAULT NULL COMMENT '提档线(最低投档分)',
    avg_mark           INT          DEFAULT NULL COMMENT '录取平均分',
    min_rank           INT          DEFAULT NULL COMMENT '最低分位次',
    avg_rank           INT          DEFAULT NULL COMMENT '平均分位次',
    KEY idx_dim (province, year, batch),
    KEY idx_college (college_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校专业提档线表(历年)';

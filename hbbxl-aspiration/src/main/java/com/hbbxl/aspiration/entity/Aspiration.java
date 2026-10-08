package com.hbbxl.aspiration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 志愿方案实体: 一行=一个学生的志愿填报任务(3+1+2) */
@Data
@TableName("aspiration")
public class Aspiration {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案码(UUID): 填报会话主键, 筛选/推荐/志愿表都挂在它下面 */
    private String aspirationCode;

    /** 创建人(填报师)ID, 关联sys_user */
    private Long userId;

    /* ---- ①学生核心信息(创建后不可修改) ---- */
    private String studentName;

    /** 性别 0未知 1男 2女 */
    private Integer gender;

    private String province;

    private Integer year;

    /** 批次(本科批/专科批) */
    private String batch;

    /** 组别(物理组/历史组), 由首选科目决定 */
    private String groupName;

    private Integer score;

    /** 政策加分 */
    private Integer bonusScore;

    /** 首选科目(3+1+2的1): 物理/历史 */
    private String firstSubject;

    /** 次选科目(3+1+2的2, 逗号分隔): 化学,生物 等 */
    private String secondSubjects;

    /* ---- ②志愿信息(可修改) ---- */
    private String aspirationName;

    private String highSchool;

    private String contactPhone;

    /* ---- ③采集扩展信息(学生/家长扫码补全) ---- */
    private String ethnicity;

    /** 是否应届 0否 1是 */
    private Integer isFresh;

    private Integer chineseScore;
    private Integer mathScore;
    private Integer englishScore;
    private Integer elective1Score;
    private Integer elective2Score;
    private Integer elective3Score;

    private BigDecimal heightCm;
    private BigDecimal weightKg;

    /** 是否色盲 0否 1是 */
    private Integer colorBlind;
    /** 是否色弱 0否 1是 */
    private Integer colorWeakness;

    private String leftVision;
    private String rightVision;

    private String parentName;
    private String fatherJob;
    private String motherJob;

    /* ---- ④推荐参数与交单状态 ---- */
    /** 智能推荐: 向上分数(滑杆) */
    private Integer upperParam;
    /** 智能推荐: 向下分数(滑杆) */
    private Integer lowerParam;

    /** 交单状态 0未交单(可填报) 1已交单(志愿表锁定) */
    private Integer submitStatus;
    /** 交单操作人(姓名) */
    private String submitter;
    private LocalDateTime submitTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}

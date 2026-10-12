package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 综合查询 · 专业目录(普本/职本/专科)，与填报用 major 表分离 */
@Data
@TableName("major_catalog")
public class MajorCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1普本 2职本 3专科 */
    private Integer eduLevel;

    private String majorCode;

    private String majorName;

    /** 学科门类 / 大类 */
    private String category;

    /** 专业类 */
    private String firstDiscipline;

    /** 专业类国标码，如 0101 */
    private String groupCode;
}

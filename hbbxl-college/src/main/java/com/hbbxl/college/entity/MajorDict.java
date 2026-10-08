package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 教育部专业目录(major) */
@Data
@TableName("major")
public class MajorDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String majorCode;

    private String majorName;

    /** 一级学科类, 如「计算机类」 */
    private String firstDiscipline;

    /** 学科门类, 如「工学」 */
    private String category;

    private String remark;
}

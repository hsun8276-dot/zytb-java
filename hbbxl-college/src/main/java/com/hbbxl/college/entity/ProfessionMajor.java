package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 综合查询 · 职业关联专业 */
@Data
@TableName("profession_major")
public class ProfessionMajor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer occId;

    private String majorCode;

    private String majorName;

    private String bachelor;

    private String duration;
}

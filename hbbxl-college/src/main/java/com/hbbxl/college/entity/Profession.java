package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 综合查询 · 职业库 */
@Data
@TableName("profession")
public class Profession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer occId;

    private String occName;

    private String industry;

    private String occType;

    private String positionSalary;

    private String definition;

    private String occIntroduction;

    private String jobRequire;

    private String occTrait;

    private String developmentSpace;

    private String workContact;
}

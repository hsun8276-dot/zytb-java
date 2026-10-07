package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 院校实体(数据源: 咕咕数据全国院校库) */
@Data
@TableName("college")
public class College {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源院校代码 */
    private Integer code;

    private String name;

    private String province;

    private String city;

    /** 主管部门 */
    private String department;

    /** 办学层次 */
    private String level;

    /** 院校类型 */
    private String collegeType;

    /** 办学性质(公办/民办) */
    private String property;

    /** 标签(双高计划等) */
    private String labels;

    /** 校徽URL */
    private String imgUrl;
}

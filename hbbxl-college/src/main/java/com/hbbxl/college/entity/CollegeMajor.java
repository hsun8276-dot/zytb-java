package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 院校开设专业(college_major) */
@Data
@TableName("college_major")
public class CollegeMajor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 院校内部编码, 对应 college.code */
    private Integer code;

    /** 院校名称(冗余) */
    private String name;

    /** 学科门类/类别 */
    private String category;

    /** 专业名称 */
    private String major;
}

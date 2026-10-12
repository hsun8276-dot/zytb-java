package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 第四轮学科评估 */
@Data
@TableName("college_estimate")
public class CollegeEstimate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer code;
    private String majors;
    private String level;
}

package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("college_graduate")
public class CollegeGraduate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer code;
    private Integer year;
    /** 推免人数 */
    private Integer num;
    private Integer masterNum;
    private Integer abroad;
    private Integer home;
    private Integer graduationNum;
    private String masterRate;
}

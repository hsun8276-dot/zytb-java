package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("college_report")
public class CollegeReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer code;
    private String newsName;
    private String newsUrl;
}

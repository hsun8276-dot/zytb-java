package com.hbbxl.college.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("college_rank")
public class CollegeRank {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer code;
    private String rankName;
    private String rankValue;
    private Integer year;
    private Integer numbers;
}

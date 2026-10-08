package com.hbbxl.college.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.college.entity.CollegeMajor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CollegeMajorMapper extends BaseMapper<CollegeMajor> {

    @Select("SELECT category, major FROM college_major " +
            "WHERE code = #{code} AND major IS NOT NULL AND major != '' " +
            "ORDER BY category, major")
    List<CollegeMajor> listByCollegeCode(@Param("code") Integer code);

    @Select("SELECT DISTINCT cm.code AS collegeCode, c.name AS collegeName, c.province, c.city " +
            "FROM college_major cm " +
            "INNER JOIN college c ON c.code = cm.code " +
            "WHERE cm.major LIKE CONCAT('%', #{majorName}, '%') " +
            "ORDER BY cm.code")
    List<Map<String, Object>> listCollegesByMajorName(@Param("majorName") String majorName);

    @Select("SELECT DISTINCT major FROM college_major " +
            "WHERE major LIKE CONCAT('%', #{keyword}, '%') " +
            "AND major NOT LIKE '%,%' AND major NOT LIKE '%｜%' " +
            "ORDER BY major LIMIT 200")
    List<String> searchMajorNames(@Param("keyword") String keyword);
}

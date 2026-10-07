package com.hbbxl.user.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 学生Mapper
 */
@Mapper
public interface StudentMapper {

    Map<String, Object> selectStudentCategory(@Param("userId") Long userId);

    List<Map<String, Object>> selectAllSubjects();

    List<Map<String, Object>> selectSubjectsByCategory(@Param("userId") Long userId);

    int insertStudentInfo(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
}
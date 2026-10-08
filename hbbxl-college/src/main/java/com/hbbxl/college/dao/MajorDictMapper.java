package com.hbbxl.college.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.college.entity.MajorDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MajorDictMapper extends BaseMapper<MajorDict> {

    @Select("SELECT DISTINCT category FROM major " +
            "WHERE category IS NOT NULL AND category != '' ORDER BY category")
    List<String> listCategories();

    @Select("SELECT DISTINCT first_discipline FROM major " +
            "WHERE category = #{category} AND first_discipline IS NOT NULL AND first_discipline != '' " +
            "ORDER BY first_discipline")
    List<String> listDisciplines(@Param("category") String category);
}

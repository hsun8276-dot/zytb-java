package com.hbbxl.college.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.college.entity.Profession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProfessionMapper extends BaseMapper<Profession> {

    @Select("SELECT DISTINCT industry FROM profession " +
            "WHERE industry IS NOT NULL AND industry != '' ORDER BY industry")
    List<String> listIndustries();

    @Select("SELECT DISTINCT occ_type FROM profession " +
            "WHERE industry = #{industry} AND occ_type IS NOT NULL AND occ_type != '' " +
            "ORDER BY occ_type")
    List<String> listOccTypes(@Param("industry") String industry);
}

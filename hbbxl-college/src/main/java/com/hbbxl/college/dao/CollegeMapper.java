package com.hbbxl.college.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.college.entity.College;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CollegeMapper extends BaseMapper<College> {

    /** 分省院校数量(排除无省份数据) */
    @Select("SELECT province AS name, COUNT(*) AS value FROM college " +
            "WHERE province IS NOT NULL AND province != '' " +
            "GROUP BY province ORDER BY value DESC")
    List<Map<String, Object>> countByProvince();

    @Select("SELECT COUNT(*) FROM college")
    long countColleges();

    @Select("SELECT COUNT(DISTINCT province) FROM college WHERE province IS NOT NULL AND province != ''")
    long countProvinces();

    /** 招生专业信息条数(河北当年志愿单元 recruit_major) */
    @Select("SELECT COUNT(*) FROM recruit_major")
    long countMajors();

    @Select("SELECT DISTINCT province FROM college " +
            "WHERE province IS NOT NULL AND province != '' " +
            "ORDER BY province")
    List<String> listProvinces();

    @Select("SELECT DISTINCT city FROM college " +
            "WHERE province = #{province} AND city IS NOT NULL AND city != '' " +
            "ORDER BY city")
    List<String> listCities(@Param("province") String province);
}

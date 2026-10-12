package com.hbbxl.college.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.college.entity.MajorCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MajorCatalogMapper extends BaseMapper<MajorCatalog> {

    @Select("SELECT category, MIN(group_code) AS minCode " +
            "FROM major_catalog WHERE edu_level = #{level} AND category != '' " +
            "GROUP BY category ORDER BY minCode")
    List<Map<String, Object>> listCategoriesOrdered(@Param("level") int level);

    @Select("SELECT first_discipline AS name, group_code AS code, MIN(major_code) AS minCode " +
            "FROM major_catalog WHERE edu_level = #{level} AND category = #{category} " +
            "AND first_discipline != '' AND group_code != '' " +
            "GROUP BY first_discipline, group_code ORDER BY group_code")
    List<Map<String, Object>> listGroupsByCategory(@Param("level") int level,
                                                   @Param("category") String category);

    @Select("SELECT DISTINCT group_code, first_discipline, category, edu_level " +
            "FROM major_catalog WHERE group_code = #{groupCode} LIMIT 1")
    MajorCatalog findGroupMeta(@Param("groupCode") String groupCode);
}

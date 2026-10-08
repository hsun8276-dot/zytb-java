package com.hbbxl.aspiration.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 只读查询sys_user(交单时取操作人姓名 / 填报师下拉) */
@Mapper
public interface SysUserDao {

    @Select("SELECT real_name FROM sys_user WHERE id = #{id} AND deleted = 0")
    String selectRealNameById(Long id);

    /** 出现在志愿方案创建人字段中的填报师(用于前台交单筛选下拉) */
    @Select("SELECT u.id AS userId, COALESCE(NULLIF(u.real_name,''), u.username) AS realName "
            + "FROM sys_user u "
            + "WHERE u.deleted = 0 AND u.id IN ("
            + "  SELECT DISTINCT a.user_id FROM aspiration a WHERE a.deleted = 0 AND a.user_id IS NOT NULL"
            + ") ORDER BY realName")
    List<Map<String, Object>> listAspirationCreators();
}

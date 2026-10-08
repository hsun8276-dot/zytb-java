package com.hbbxl.aspiration.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 只读查询sys_user(交单时取操作人姓名) */
@Mapper
public interface SysUserDao {

    @Select("SELECT real_name FROM sys_user WHERE id = #{id} AND deleted = 0")
    String selectRealNameById(Long id);
}

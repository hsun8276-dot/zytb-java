package com.hbbxl.user.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色关联 Mapper（用户服务）
 */
@Mapper
public interface SysUserRoleMapper {

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
package com.hbbxl.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserRoleMapper {

    /** 绑定用户角色 */
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /** 查用户角色编码 */
    String selectRoleCodeByUserId(@Param("userId") Long userId);
}

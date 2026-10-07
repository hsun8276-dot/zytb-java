package com.hbbxl.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 系统角色Mapper
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    Integer countUserRoleByRoleId(@Param("roleId") Long roleId);

    IPage<Map<String, Object>> selectUserRoleList(Page<?> page);

    List<Map<String, Object>> selectRolesByUserIds(@Param("userIds") List<Long> userIds);

    Integer countUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int insertUserRoleMapping(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}

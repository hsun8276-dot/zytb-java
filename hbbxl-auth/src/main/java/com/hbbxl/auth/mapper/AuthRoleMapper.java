package com.hbbxl.auth.mapper;

import com.hbbxl.auth.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper
 */
@Mapper
public interface AuthRoleMapper extends BaseMapper<SysRole> {
}
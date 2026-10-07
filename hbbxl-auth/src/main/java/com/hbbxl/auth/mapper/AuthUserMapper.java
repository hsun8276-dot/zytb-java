package com.hbbxl.auth.mapper;

import com.hbbxl.auth.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<SysUser> {
}
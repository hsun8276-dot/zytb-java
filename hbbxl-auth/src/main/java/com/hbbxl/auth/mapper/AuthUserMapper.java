package com.hbbxl.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbbxl.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserMapper extends BaseMapper<SysUser> {
}

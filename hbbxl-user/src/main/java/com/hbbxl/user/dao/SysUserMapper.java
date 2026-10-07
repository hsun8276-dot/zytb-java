package com.hbbxl.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 用户分页列表(带角色, keyword匹配用户名/姓名/手机号) */
    Page<Map<String, Object>> pageList(Page<Map<String, Object>> page,
                                       @Param("keyword") String keyword);

    /** 到期自动停用: expire_time已过仍启用的账号置停用 */
    int disableExpired();
}

package com.hbbxl.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthUserRoleMapper {

    String selectRoleCodeByUserId(@Param("userId") Long userId);

    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    List<Map<String, Object>> selectRolesByUserId(@Param("userId") Long userId);
}

package com.hbbxl.user.entity;

import com.hbbxl.common.core.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String password;

    private String realName;

    /** 昵称 */
    private String nickname;

    private String phone;

    private String email;

    private String avatar;

    /** 个人签名 */
    private String signature;

    /** 1-男 0-女 */
    private Integer gender;

    /** 1-超级管理员 0-普通用户 */
    private Integer isSuper;

    /** 0-正常 1-禁用 */
    private Integer status;
}

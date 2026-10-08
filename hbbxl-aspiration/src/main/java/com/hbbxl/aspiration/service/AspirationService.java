package com.hbbxl.aspiration.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/** 志愿方案服务: 学生管理(添加学生/列表/详情)与交单 */
public interface AspirationService {

    /** 方案分页列表: 填报师看自己创建的, 管理员看全部 */
    IPage<Map<String, Object>> pageList(long page, long size, String keyword, Integer submitStatus,
                                        Long currentUserId, String role);

    /** 创建方案(添加学生): 生成方案码UUID, 学生核心信息创建后不可改 */
    String create(Map<String, Object> body, Long currentUserId);

    /** 方案详情(学生详细信息弹窗) */
    Map<String, Object> detail(String aspirationCode, Long currentUserId, String role);

    /** 编辑志愿信息段(志愿名称/就读高中/联系电话), 已交单锁定 */
    void updateInfo(String aspirationCode, Map<String, Object> body, Long currentUserId, String role);

    /** 修改推荐滑杆参数(向上/向下分数), 已交单锁定 */
    void updateSlider(String aspirationCode, Integer upperParam, Integer lowerParam, Long currentUserId, String role);

    /** 交单(status=1)/撤销交单(status=0) */
    void submit(String aspirationCode, Integer status, Long currentUserId, String role);

    /** 删除方案(逻辑删), 填报师本人或管理员 */
    void delete(String aspirationCode, Long currentUserId, String role);
}

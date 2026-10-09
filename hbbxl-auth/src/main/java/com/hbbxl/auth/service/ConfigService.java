package com.hbbxl.auth.service;

import java.util.List;
import java.util.Map;

public interface ConfigService {

    /** 公开配置(键值 map) */
    Map<String, String> publicAll();

    /** 管理端完整列表 */
    List<Map<String, Object>> adminList();

    /** 批量保存(仅允许已有配置键) */
    void save(Map<String, String> body);
}

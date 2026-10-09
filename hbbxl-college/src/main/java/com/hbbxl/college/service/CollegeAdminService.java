package com.hbbxl.college.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface CollegeAdminService {

    IPage<Map<String, Object>> page(int page, int size, String province, String city, String keyword,
                                   String level, String collegeType, String property);

    /** 检索下拉: 层次 / 类型 / 性质去重列表 */
    Map<String, List<String>> filters();
}

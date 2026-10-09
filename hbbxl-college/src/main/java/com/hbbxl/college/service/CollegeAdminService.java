package com.hbbxl.college.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

public interface CollegeAdminService {

    IPage<Map<String, Object>> page(int page, int size, String province, String city, String keyword);
}

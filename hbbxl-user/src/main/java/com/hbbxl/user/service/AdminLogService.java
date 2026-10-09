package com.hbbxl.user.service;

import java.util.Map;

public interface AdminLogService {

    Map<String, Object> page(int page, int size, String keyword, Integer status, String module);
}

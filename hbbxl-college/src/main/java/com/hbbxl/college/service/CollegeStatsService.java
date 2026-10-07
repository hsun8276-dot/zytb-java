package com.hbbxl.college.service;

import java.util.Map;

public interface CollegeStatsService {

    /** 仪表板统计: 高校数/省份数/历史数据年数/专业条数/分省分布 */
    Map<String, Object> dashboardStats();
}

package com.hbbxl.college.service.impl;

import com.hbbxl.college.dao.CollegeMapper;
import com.hbbxl.college.service.CollegeStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 院校库统计服务 */
@Service
@RequiredArgsConstructor
public class CollegeStatsServiceImpl implements CollegeStatsService {

    /** 数据积累起始年份(院校库数据覆盖 2024 年至今) */
    private static final int DATA_START_YEAR = 2024;

    private final CollegeMapper collegeMapper;

    @Override
    public Map<String, Object> dashboardStats() {
        long collegeCount = collegeMapper.countColleges();
        long provinceCount = collegeMapper.countProvinces();
        long majorCount = collegeMapper.countMajors();
        List<Map<String, Object>> provinceStats = collegeMapper.countByProvince();
        int historyYears = java.time.LocalDate.now().getYear() - DATA_START_YEAR + 1;

        Map<String, Object> data = new HashMap<>();
        data.put("collegeCount", collegeCount);
        data.put("provinceCount", provinceCount);
        data.put("historyYears", historyYears);
        data.put("majorCount", majorCount);
        data.put("provinceStats", provinceStats);
        return data;
    }
}

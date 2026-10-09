package com.hbbxl.college.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.college.dao.CollegeMapper;
import com.hbbxl.college.entity.College;
import com.hbbxl.college.service.CollegeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CollegeAdminServiceImpl implements CollegeAdminService {

    private final CollegeMapper collegeMapper;

    @Override
    public IPage<Map<String, Object>> page(int page, int size, String province, String city, String keyword) {
        LambdaQueryWrapper<College> qw = new LambdaQueryWrapper<>();
        qw.select(College::getId, College::getCode, College::getName, College::getProvince, College::getCity,
                College::getLevel, College::getCollegeType, College::getProperty, College::getDepartment,
                College::getLabels, College::getHasMaster, College::getHasDoctor, College::getWebsite);
        String p = trim(province);
        String c = trim(city);
        String k = trim(keyword);
        if (p != null) {
            qw.and(w -> w.eq(College::getProvince, p).or().eq(College::getProvince, p + " "));
        }
        if (c != null) {
            qw.and(w -> w.eq(College::getCity, c).or().eq(College::getCity, c + " "));
        }
        if (k != null) {
            qw.and(w -> w.like(College::getName, k).or().eq(College::getCode, tryInt(k)));
        }
        qw.orderByAsc(College::getCode);
        IPage<College> raw = collegeMapper.selectPage(new Page<>(page, size), qw);
        return raw.convert(this::toRow);
    }

    private Map<String, Object> toRow(College col) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", col.getId());
        m.put("collegeCode", col.getCode());
        m.put("collegeName", col.getName());
        m.put("province", trimStr(col.getProvince()));
        m.put("city", trimStr(col.getCity()));
        m.put("level", col.getLevel());
        m.put("collegeType", col.getCollegeType());
        m.put("property", col.getProperty());
        m.put("department", col.getDepartment());
        m.put("labels", col.getLabels());
        m.put("hasMaster", col.getHasMaster() != null && col.getHasMaster() == 1);
        m.put("hasDoctor", col.getHasDoctor() != null && col.getHasDoctor() == 1);
        m.put("website", col.getWebsite());
        return m;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String trimStr(String s) {
        return s == null ? "" : s.trim();
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }
}

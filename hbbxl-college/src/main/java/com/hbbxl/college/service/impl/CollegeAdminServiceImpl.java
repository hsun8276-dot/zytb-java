package com.hbbxl.college.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.college.dao.CollegeMapper;
import com.hbbxl.college.entity.College;
import com.hbbxl.college.service.CollegeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CollegeAdminServiceImpl implements CollegeAdminService {

    private final CollegeMapper collegeMapper;
    private final JdbcTemplate jdbc;

    @Override
    public IPage<Map<String, Object>> page(int page, int size, String province, String city, String keyword,
                                           String level, String collegeType, String property) {
        LambdaQueryWrapper<College> qw = new LambdaQueryWrapper<>();
        qw.select(College::getId, College::getCode, College::getName, College::getProvince, College::getCity,
                College::getLevel, College::getCollegeType, College::getProperty, College::getDepartment,
                College::getLabels, College::getHasMaster, College::getHasDoctor, College::getWebsite);
        String p = trim(province);
        String c = trim(city);
        String k = trim(keyword);
        String lv = trim(level);
        String tp = trim(collegeType);
        String pr = trim(property);
        if (p != null) {
            qw.and(w -> w.eq(College::getProvince, p).or().eq(College::getProvince, p + " "));
        }
        if (c != null) {
            qw.and(w -> w.eq(College::getCity, c).or().eq(College::getCity, c + " "));
        }
        if (lv != null) {
            qw.and(w -> w.eq(College::getLevel, lv).or().eq(College::getLevel, lv + " "));
        }
        if (tp != null) {
            qw.and(w -> w.eq(College::getCollegeType, tp).or().eq(College::getCollegeType, tp + " "));
        }
        if (pr != null) {
            qw.and(w -> w.eq(College::getProperty, pr).or().eq(College::getProperty, pr + " "));
        }
        if (k != null) {
            qw.and(w -> w.like(College::getName, k).or().eq(College::getCode, tryInt(k)));
        }
        qw.orderByAsc(College::getCode);
        IPage<College> raw = collegeMapper.selectPage(new Page<>(page, size), qw);
        return raw.convert(this::toRow);
    }

    @Override
    public Map<String, List<String>> filters() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("levels", distinct("level"));
        out.put("collegeTypes", distinct("college_type"));
        out.put("properties", distinct("property"));
        return out;
    }

    private List<String> distinct(String column) {
        return jdbc.query(
                "SELECT DISTINCT TRIM(" + column + ") v FROM college "
                        + "WHERE " + column + " IS NOT NULL AND TRIM(" + column + ")<>'' ORDER BY v",
                (rs, i) -> rs.getString("v"));
    }

    private Map<String, Object> toRow(College col) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", col.getId());
        m.put("collegeCode", col.getCode());
        m.put("collegeName", col.getName());
        m.put("province", trimStr(col.getProvince()));
        m.put("city", trimStr(col.getCity()));
        m.put("level", trimStr(col.getLevel()));
        m.put("collegeType", trimStr(col.getCollegeType()));
        m.put("property", trimStr(col.getProperty()));
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

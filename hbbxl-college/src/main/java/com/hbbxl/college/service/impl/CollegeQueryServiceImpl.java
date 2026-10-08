package com.hbbxl.college.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.college.dao.CollegeMajorMapper;
import com.hbbxl.college.dao.CollegeMapper;
import com.hbbxl.college.dao.MajorDictMapper;
import com.hbbxl.college.entity.College;
import com.hbbxl.college.entity.CollegeMajor;
import com.hbbxl.college.entity.MajorDict;
import com.hbbxl.college.service.CollegeQueryService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/** 综合查询工具 · 院校/专业查询 */
@Service
@RequiredArgsConstructor
public class CollegeQueryServiceImpl implements CollegeQueryService {

    private final CollegeMapper collegeMapper;
    private final CollegeMajorMapper collegeMajorMapper;
    private final MajorDictMapper majorDictMapper;

    @Override
    public List<String> provinces() {
        // 历史脏数据省份名带尾空格, 去重后返回干净列表
        return collegeMapper.listProvinces().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> cities(String province) {
        if (!StringUtils.hasText(province)) {
            return List.of();
        }
        String p = province.trim();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(collegeMapper.listCities(p));
        // 兼容库内「省名+尾空格」脏数据
        set.addAll(collegeMapper.listCities(p + " "));
        return set.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> searchColleges(String province, String city, String name) {
        LambdaQueryWrapper<College> qw = new LambdaQueryWrapper<>();
        // 列表只需编码/名称, 切勿 select * —— college 含大量 TEXT 字段, 全量会拖垮远程库与 30s 前端超时
        qw.select(College::getCode, College::getName);
        String p = trimToNull(province);
        String c = trimToNull(city);
        String n = trimToNull(name);
        if (p != null) {
            // 兼容省份字段尾空格脏数据
            qw.and(w -> w.eq(College::getProvince, p).or().eq(College::getProvince, p + " "));
        }
        if (c != null) {
            qw.and(w -> w.eq(College::getCity, c).or().eq(College::getCity, c + " "));
        }
        if (n != null) {
            qw.like(College::getName, n);
        }
        qw.orderByAsc(College::getCode);
        // 无筛选时限制条数, 避免一次拉 3000+ 行
        if (p == null && c == null && n == null) {
            qw.last("LIMIT 200");
        }
        List<College> list = collegeMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (College col : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("collegeCode", col.getCode());
            row.put("collegeName", col.getName());
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Object> collegeDetail(Integer code) {
        College college = collegeMapper.selectOne(new LambdaQueryWrapper<College>()
                .eq(College::getCode, code));
        if (college == null) {
            throw new BizException("院校不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("collegeCode", college.getCode());
        m.put("collegeName", college.getName());
        m.put("collegeProvince", nullToEmpty(college.getProvince()));
        m.put("collegeCity", nullToEmpty(college.getCity()));
        m.put("collegeAdmin", nullToEmpty(college.getDepartment()));
        m.put("collegeLevel", nullToEmpty(college.getLevel()));
        m.put("collegeType", nullToEmpty(college.getCollegeType()));
        m.put("collegeProperty", nullToEmpty(college.getProperty()));
        m.put("collegeImg", nullToEmpty(college.getImgUrl()));
        m.put("collegeLabels", splitLabels(college.getLabels()));
        m.put("website", nullToEmpty(college.getWebsite()));
        m.put("recruitWebsite", nullToEmpty(college.getRecruitWebsite()));
        m.put("email", nullToEmpty(college.getEmail()));
        m.put("phone", splitPhones(college.getPhone()));
        m.put("address", nullToEmpty(college.getAddress()));
        m.put("collegeInfo", nullToEmpty(college.getCollegeInfo()));
        m.put("accommodation", nullToEmpty(college.getAccommodation()));
        m.put("advantage", nullToEmpty(college.getAdvantage()));
        m.put("recruitRule", nullToEmpty(college.getRecruitRule()));
        m.put("teachers", nullToEmpty(college.getTeachers()));
        m.put("scholarship", nullToEmpty(college.getScholarship()));
        m.put("transferMajor", nullToEmpty(college.getTransferMajor()));
        m.put("famousAlumni", nullToEmpty(college.getFamousAlumni()));
        m.put("partnerEnterprise", nullToEmpty(college.getPartnerEnterprise()));
        m.put("collegeTime", nullToEmpty(college.getCollegeTime()));
        m.put("keyLab", nullToEmpty(college.getKeyLab()));
        m.put("vrAddress", nullToEmpty(college.getVrAddress()));
        m.put("baiduBaike", nullToEmpty(college.getBaiduBaike()));
        m.put("hasMaster", college.getHasMaster() != null && college.getHasMaster() == 1);
        m.put("hasDoctor", college.getHasDoctor() != null && college.getHasDoctor() == 1);
        return m;
    }

    @Override
    public List<Map<String, Object>> collegeMajors(Integer code) {
        List<CollegeMajor> rows = collegeMajorMapper.listByCollegeCode(code);
        LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
        for (CollegeMajor row : rows) {
            String cat = StringUtils.hasText(row.getCategory()) ? row.getCategory() : "其他";
            String major = row.getMajor();
            if (!StringUtils.hasText(major)) {
                continue;
            }
            // 个别脏数据把多个专业用逗号/｜拼在一行, 拆开展示
            for (String part : major.split("[,，｜|]")) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(name);
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            List<String> majors = e.getValue().stream().distinct().collect(Collectors.toList());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", e.getKey());
            item.put("majors", majors);
            item.put("majorText", String.join("、", majors));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<String> majorCategories() {
        return majorDictMapper.listCategories();
    }

    @Override
    public List<String> majorDisciplines(String category) {
        if (!StringUtils.hasText(category)) {
            return List.of();
        }
        return majorDictMapper.listDisciplines(category.trim());
    }

    @Override
    public List<Map<String, Object>> searchMajorDict(String category, String discipline, String keyword) {
        LambdaQueryWrapper<MajorDict> qw = new LambdaQueryWrapper<>();
        String cat = trimToNull(category);
        String disc = trimToNull(discipline);
        String kw = trimToNull(keyword);
        if (cat != null) {
            qw.eq(MajorDict::getCategory, cat);
        }
        if (disc != null) {
            qw.eq(MajorDict::getFirstDiscipline, disc);
        }
        if (kw != null) {
            qw.like(MajorDict::getMajorName, kw);
        }
        qw.orderByAsc(MajorDict::getMajorCode);
        List<MajorDict> list = majorDictMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (MajorDict d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("majorCode", d.getMajorCode());
            m.put("majorName", d.getMajorName());
            m.put("firstDiscipline", nullToEmpty(d.getFirstDiscipline()));
            m.put("category", nullToEmpty(d.getCategory()));
            result.add(m);
        }
        return result;
    }

    @Override
    public List<String> searchMajorNames(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        return collegeMajorMapper.searchMajorNames(keyword.trim());
    }

    @Override
    public List<Map<String, Object>> collegesByMajor(String majorName) {
        if (!StringUtils.hasText(majorName)) {
            return List.of();
        }
        return collegeMajorMapper.listCollegesByMajorName(majorName.trim());
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> splitLabels(String labels) {
        if (!StringUtils.hasText(labels)) {
            return List.of();
        }
        return Arrays.stream(labels.split("[,，、]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> splitPhones(String phone) {
        if (!StringUtils.hasText(phone)) {
            return List.of();
        }
        return Arrays.stream(phone.split("[,，、;/]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}

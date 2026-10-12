package com.hbbxl.college.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.college.dao.CollegeEstimateMapper;
import com.hbbxl.college.dao.CollegeGraduateMapper;
import com.hbbxl.college.dao.CollegeMajorMapper;
import com.hbbxl.college.dao.CollegeMapper;
import com.hbbxl.college.dao.CollegeRankMapper;
import com.hbbxl.college.dao.CollegeReportMapper;
import com.hbbxl.college.dao.MajorCatalogMapper;
import com.hbbxl.college.dao.MajorDictMapper;
import com.hbbxl.college.dao.ProfessionMajorMapper;
import com.hbbxl.college.dao.ProfessionMapper;
import com.hbbxl.college.entity.College;
import com.hbbxl.college.entity.CollegeEstimate;
import com.hbbxl.college.entity.CollegeGraduate;
import com.hbbxl.college.entity.CollegeMajor;
import com.hbbxl.college.entity.CollegeRank;
import com.hbbxl.college.entity.CollegeReport;
import com.hbbxl.college.entity.MajorCatalog;
import com.hbbxl.college.entity.MajorDict;
import com.hbbxl.college.entity.Profession;
import com.hbbxl.college.entity.ProfessionMajor;
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

    /**
     * 对齐参考站 :9002 省份下拉：直辖市/港澳用短名且置顶，其余为库内全称。
     * 顺序摘自 http://39.107.86.41:9002/ 实测。
     */
    private static final List<String> PROVINCE_ORDER = List.of(
            "北京", "天津", "上海", "重庆", "香港",
            "四川省", "新疆维吾尔自治区", "内蒙古自治区", "安徽省", "陕西省", "贵州省", "河南省",
            "辽宁省", "吉林省", "河北省", "甘肃省", "广西壮族自治区", "云南省", "湖南省",
            "宁夏回族自治区", "山东省", "广东省", "江苏省", "黑龙江省", "山西省", "江西省",
            "湖北省", "福建省", "浙江省", "海南省", "西藏自治区", "青海省",
            "台湾省", "澳门");

    /** 下拉短名 → city 字段（直辖市/港澳院校常 province 为空） */
    private static final Map<String, String> MUNICIPALITY_CITY = new LinkedHashMap<>();
    static {
        MUNICIPALITY_CITY.put("北京", "北京市");
        MUNICIPALITY_CITY.put("天津", "天津市");
        MUNICIPALITY_CITY.put("上海", "上海市");
        MUNICIPALITY_CITY.put("重庆", "重庆市");
        MUNICIPALITY_CITY.put("香港", "香港特别行政区");
        MUNICIPALITY_CITY.put("澳门", "澳门特别行政区");
        MUNICIPALITY_CITY.put("北京市", "北京市");
        MUNICIPALITY_CITY.put("天津市", "天津市");
        MUNICIPALITY_CITY.put("上海市", "上海市");
        MUNICIPALITY_CITY.put("重庆市", "重庆市");
    }

    private final CollegeMapper collegeMapper;
    private final CollegeMajorMapper collegeMajorMapper;
    private final MajorDictMapper majorDictMapper;
    private final MajorCatalogMapper majorCatalogMapper;
    private final ProfessionMapper professionMapper;
    private final ProfessionMajorMapper professionMajorMapper;
    private final CollegeRankMapper collegeRankMapper;
    private final CollegeReportMapper collegeReportMapper;
    private final CollegeGraduateMapper collegeGraduateMapper;
    private final CollegeEstimateMapper collegeEstimateMapper;

    @Override
    public List<String> provinces() {
        Set<String> fromDb = collegeMapper.listProvinces().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 库内若有直辖市全称则去掉，统一用短名（与 :9002 一致）
        fromDb.removeAll(Set.of("北京市", "天津市", "上海市", "重庆市", "香港特别行政区", "澳门特别行政区"));
        fromDb.add("北京");
        fromDb.add("天津");
        fromDb.add("上海");
        fromDb.add("重庆");
        fromDb.add("香港");
        List<String> ordered = new ArrayList<>(fromDb.size());
        for (String p : PROVINCE_ORDER) {
            if (fromDb.remove(p)) {
                ordered.add(p);
            }
        }
        fromDb.stream().sorted().forEach(ordered::add);
        return ordered;
    }

    @Override
    public List<String> cities(String province) {
        if (!StringUtils.hasText(province)) {
            return List.of();
        }
        String p = province.trim();
        String muniCity = MUNICIPALITY_CITY.get(p);
        if (muniCity != null) {
            return List.of(muniCity);
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(collegeMapper.listCities(p));
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
            String muniCity = MUNICIPALITY_CITY.get(p);
            if (muniCity != null) {
                // 直辖市：province 可能为空，按 city 命中
                qw.and(w -> w.eq(College::getProvince, p)
                        .or().eq(College::getProvince, p + " ")
                        .or().eq(College::getProvince, muniCity)
                        .or().eq(College::getCity, muniCity)
                        .or().eq(College::getCity, muniCity + " "));
            } else {
                qw.and(w -> w.eq(College::getProvince, p).or().eq(College::getProvince, p + " "));
            }
        }
        if (c != null) {
            qw.and(w -> w.eq(College::getCity, c).or().eq(College::getCity, c + " "));
        }
        if (n != null) {
            qw.like(College::getName, n);
        }
        qw.orderByAsc(College::getCode);
        // 对齐参考站：无筛选时返回全库列表(仅 code/name)
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
        m.put("studentTeacherRatio", nullToEmpty(college.getStudentTeacherRatio()));
        m.put("doubleFirstClass", nullToEmpty(college.getDoubleFirstClass()));
        m.put("extraPic", nullToEmpty(college.getExtraPic()));
        m.put("hasMaster", college.getHasMaster() != null && college.getHasMaster() == 1);
        m.put("hasDoctor", college.getHasDoctor() != null && college.getHasDoctor() == 1);
        boolean canRecommend = splitLabels(college.getLabels()).stream()
                .anyMatch(lb -> lb.contains("推免") || lb.contains("保研"));
        m.put("canRecommend", canRecommend);

        // 扩展：排行 / 资讯 / 保研率 / 学科评估（库内已有，对齐源站详情侧栏与中部区块）
        List<CollegeRank> ranks = collegeRankMapper.selectList(new LambdaQueryWrapper<CollegeRank>()
                .eq(CollegeRank::getCode, code)
                .orderByAsc(CollegeRank::getId));
        List<Map<String, Object>> rankList = new ArrayList<>(ranks.size());
        for (CollegeRank r : ranks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rankName", nullToEmpty(r.getRankName()));
            row.put("rankValue", nullToEmpty(r.getRankValue()));
            row.put("year", r.getYear());
            rankList.add(row);
        }
        m.put("ranks", rankList);

        List<CollegeReport> reports = collegeReportMapper.selectList(new LambdaQueryWrapper<CollegeReport>()
                .eq(CollegeReport::getCode, code)
                .orderByAsc(CollegeReport::getId)
                .last("LIMIT 30"));
        List<Map<String, Object>> reportList = new ArrayList<>(reports.size());
        for (CollegeReport r : reports) {
            Map<String, Object> row = new LinkedHashMap<>();
            String name = nullToEmpty(r.getNewsName());
            String url = nullToEmpty(r.getNewsUrl());
            row.put("newsName", name);
            row.put("newsUrl", url);
            // 展示标题：院校名 + 类型（对齐源站「北京大学2025年招生章程」风格，库中无年份则只用类型）
            row.put("title", buildReportTitle(college.getName(), name, url));
            reportList.add(row);
        }
        m.put("reports", reportList);

        List<CollegeGraduate> grads = collegeGraduateMapper.selectList(new LambdaQueryWrapper<CollegeGraduate>()
                .eq(CollegeGraduate::getCode, code)
                .orderByDesc(CollegeGraduate::getYear));
        List<Map<String, Object>> gradList = new ArrayList<>(grads.size());
        for (CollegeGraduate g : grads) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("year", g.getYear());
            row.put("graduationNum", g.getGraduationNum());
            row.put("num", g.getNum());
            row.put("masterNum", g.getMasterNum());
            row.put("home", g.getHome());
            row.put("abroad", g.getAbroad());
            row.put("masterRate", nullToEmpty(g.getMasterRate()));
            gradList.add(row);
        }
        m.put("graduates", gradList);

        List<CollegeEstimate> estimates = collegeEstimateMapper.selectList(new LambdaQueryWrapper<CollegeEstimate>()
                .eq(CollegeEstimate::getCode, code)
                .orderByAsc(CollegeEstimate::getId));
        List<Map<String, Object>> estimateList = new ArrayList<>(estimates.size());
        for (CollegeEstimate e : estimates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", nullToEmpty(e.getLevel()));
            String majors = nullToEmpty(e.getMajors()).replace('｜', '、').replace('|', '、');
            row.put("majors", majors);
            estimateList.add(row);
        }
        m.put("estimates", estimateList);
        return m;
    }

    private static String buildReportTitle(String collegeName, String newsName, String url) {
        String base = (collegeName == null ? "" : collegeName) + (newsName == null ? "" : newsName);
        // URL 中若含年份则插入到名称前，尽量贴近源站展示
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(20\\d{2})").matcher(url == null ? "" : url);
        if (m.find() && newsName != null && !newsName.isEmpty()) {
            return (collegeName == null ? "" : collegeName) + m.group(1) + "年" + newsName;
        }
        return base.isEmpty() ? "相关资讯" : base;
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

    @Override
    public List<Map<String, Object>> majorLevels() {
        List<Map<String, Object>> list = new ArrayList<>(3);
        list.add(levelItem(1, "普本"));
        list.add(levelItem(2, "职本"));
        list.add(levelItem(3, "专科"));
        return list;
    }

    @Override
    public List<Map<String, Object>> majorTree(int level) {
        int lv = (level >= 1 && level <= 3) ? level : 1;
        List<Map<String, Object>> cats = majorCatalogMapper.listCategoriesOrdered(lv);
        List<Map<String, Object>> result = new ArrayList<>(cats.size());
        for (Map<String, Object> catRow : cats) {
            String category = String.valueOf(catRow.get("category"));
            List<Map<String, Object>> groupsRaw = majorCatalogMapper.listGroupsByCategory(lv, category);
            List<Map<String, Object>> groups = new ArrayList<>(groupsRaw.size());
            for (Map<String, Object> g : groupsRaw) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", nullToEmpty(Objects.toString(g.get("name"), "")));
                item.put("code", nullToEmpty(Objects.toString(g.get("code"), "")));
                groups.add(item);
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("category", category);
            node.put("groups", groups);
            result.add(node);
        }
        return result;
    }

    @Override
    public Map<String, Object> majorsByGroup(String groupCode) {
        String code = trimToNull(groupCode);
        if (code == null) {
            throw new BizException("专业类代码不能为空");
        }
        MajorCatalog meta = majorCatalogMapper.findGroupMeta(code);
        LambdaQueryWrapper<MajorCatalog> qw = new LambdaQueryWrapper<>();
        qw.eq(MajorCatalog::getGroupCode, code).orderByAsc(MajorCatalog::getMajorCode);
        List<MajorCatalog> list = majorCatalogMapper.selectList(qw);
        List<Map<String, Object>> majors = new ArrayList<>(list.size());
        for (MajorCatalog d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("majorCode", d.getMajorCode());
            m.put("majorName", d.getMajorName());
            majors.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupCode", code);
        result.put("majorGroup", meta != null ? nullToEmpty(meta.getFirstDiscipline()) : "");
        result.put("category", meta != null ? nullToEmpty(meta.getCategory()) : "");
        result.put("level", meta != null ? meta.getEduLevel() : null);
        result.put("majors", majors);
        return result;
    }

    private static Map<String, Object> levelItem(int id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    @Override
    public List<String> professionIndustries() {
        return professionMapper.listIndustries();
    }

    @Override
    public List<String> professionOccTypes(String industry) {
        if (!StringUtils.hasText(industry)) {
            return List.of();
        }
        return professionMapper.listOccTypes(industry.trim());
    }

    @Override
    public List<Map<String, Object>> searchProfessions(String industry, String occType, String occName) {
        LambdaQueryWrapper<Profession> qw = new LambdaQueryWrapper<>();
        String ind = trimToNull(industry);
        String typ = trimToNull(occType);
        String name = trimToNull(occName);
        if (ind != null) {
            qw.eq(Profession::getIndustry, ind);
        }
        if (typ != null) {
            qw.eq(Profession::getOccType, typ);
        }
        if (name != null) {
            qw.like(Profession::getOccName, name);
        }
        qw.orderByAsc(Profession::getOccId);
        List<Profession> list = professionMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Profession p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("occId", p.getOccId());
            m.put("occName", p.getOccName());
            m.put("industry", nullToEmpty(p.getIndustry()));
            m.put("occType", nullToEmpty(p.getOccType()));
            m.put("positionSalary", nullToEmpty(p.getPositionSalary()));
            result.add(m);
        }
        return result;
    }

    @Override
    public Map<String, Object> professionDetail(Integer occId) {
        if (occId == null || occId <= 0) {
            throw new BizException("职业ID无效");
        }
        Profession p = professionMapper.selectOne(new LambdaQueryWrapper<Profession>()
                .eq(Profession::getOccId, occId).last("LIMIT 1"));
        if (p == null) {
            throw new BizException("职业不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("occId", p.getOccId());
        m.put("occName", p.getOccName());
        m.put("industry", nullToEmpty(p.getIndustry()));
        m.put("occType", nullToEmpty(p.getOccType()));
        m.put("positionSalary", nullToEmpty(p.getPositionSalary()));
        m.put("definition", nullToEmpty(p.getDefinition()));
        m.put("occIntroduction", nullToEmpty(p.getOccIntroduction()));
        m.put("jobRequire", nullToEmpty(p.getJobRequire()));
        m.put("occTrait", nullToEmpty(p.getOccTrait()));
        m.put("developmentSpace", nullToEmpty(p.getDevelopmentSpace()));
        m.put("workContact", nullToEmpty(p.getWorkContact()));
        return m;
    }

    @Override
    public List<Map<String, Object>> professionMajors(Integer occId) {
        if (occId == null || occId <= 0) {
            return List.of();
        }
        List<ProfessionMajor> list = professionMajorMapper.selectList(
                new LambdaQueryWrapper<ProfessionMajor>()
                        .eq(ProfessionMajor::getOccId, occId)
                        .orderByAsc(ProfessionMajor::getMajorCode));
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (ProfessionMajor pm : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("majorCode", nullToEmpty(pm.getMajorCode()));
            m.put("majorName", nullToEmpty(pm.getMajorName()));
            m.put("bachelor", nullToEmpty(pm.getBachelor()));
            m.put("duration", nullToEmpty(pm.getDuration()));
            result.add(m);
        }
        return result;
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

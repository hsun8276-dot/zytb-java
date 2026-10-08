package com.hbbxl.aspiration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.aspiration.dao.AspirationMapper;
import com.hbbxl.aspiration.dao.FillDao;
import com.hbbxl.aspiration.entity.Aspiration;
import com.hbbxl.aspiration.service.FillService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 志愿填报子页面服务实现: 推荐区间/筛选/概率/明细/排序/报告/导出 的业务规则都在这里 */
@Service
@RequiredArgsConstructor
public class FillServiceImpl implements FillService {

    /** 各省普通类志愿个数上限 */
    private static final Map<String, Integer> UPPER_LIMITS = Map.of("河北", 96);
    /** 一键填报允许超出上限的余量(与参考系统一致) */
    private static final int ONE_CLICK_EXTRA = 20;
    private static final int SEARCH_UNV_LIMIT = 200;
    private static final int SEARCH_DEBUT_LIMIT = 300;
    private static final int MAJOR_CANDIDATE_LIMIT = 200;
    private static final int RECOMMEND_PAGE_SIZE = 100;
    private static final int RECOMMEND_PAGE_MAX = 500;

    /** 录取概率参数(参考系统 CalculateProbability) */
    private static final int UPPER_BOUND = 30;
    private static final int LOWER_BOUND = 20;
    private static final double MIN_MARK_PROBABILITY = 40;
    private static final double AVG_MARK_PROBABILITY = 60;
    private static final double UNKNOWN_PROBABILITY = 9999.0;

    private static final List<String> ALL_SUBJECTS = List.of("物理", "历史", "化学", "生物", "地理", "政治");

    public static final String G_PROVINCE = "province";
    public static final String G_UNV_TYPE = "unv_type";
    public static final String G_UNV_ATTRIBUTE = "unv_attribute";
    public static final String G_UNV_NATURE = "unv_nature";
    private static final String OTHER_ATTRIBUTE = "其它";

    private static final Map<String, List<String>> UNIV_OPTIONS = new LinkedHashMap<>();
    private static final Map<String, String> UNIV_EMPTY_MESSAGES = Map.of(
            G_PROVINCE, "未选省份", G_UNV_TYPE, "未选院校类型",
            G_UNV_ATTRIBUTE, "未选院校性质", G_UNV_NATURE, "未选院校属性");
    private static final Map<String, List<String>> UNIV_DEFAULT_EXCLUDED = Map.of(
            G_UNV_NATURE, List.of("内地与港澳台地区合作办学", "中外合作办学"));

    static {
        UNIV_OPTIONS.put(G_PROVINCE, List.of(
                "北京", "上海", "天津", "重庆", "江苏", "浙江", "广东", "湖北", "湖南", "四川",
                "河北", "山东", "河南", "安徽", "江西", "福建", "海南", "陕西", "山西", "辽宁",
                "吉林", "黑龙江", "云南", "广西", "贵州", "内蒙古", "甘肃", "宁夏", "青海", "新疆",
                "西藏", "台湾", "香港", "澳门"));
        UNIV_OPTIONS.put(G_UNV_TYPE, List.of(
                "综合", "工科", "师范", "农业", "林业", "医药", "语言", "财经", "政法", "体育", "艺术", "民族", "军事"));
        UNIV_OPTIONS.put(G_UNV_ATTRIBUTE, List.of("985", "211", OTHER_ATTRIBUTE, "双一流"));
        UNIV_OPTIONS.put(G_UNV_NATURE, List.of("公办", "民办", "内地与港澳台地区合作办学", "中外合作办学"));
    }

    /** 其它筛选的预设关键字; $ 分隔同义写法(任一命中即算命中) */
    private static final List<String> PRESET_KEYWORDS = List.of(
            "中外合作", "专项", "预科", "少数民族", "八协$八省区对等协作计划", "日语", "俄语", "法语");
    private static final String DEFAULT_PRESET_EXCLUDE = "专项,预科,少数民族,八协$八省区对等协作计划";
    /** 学制: 前端 -1=不限, 0=未知; 库里 NULL=不限, 0=未知 */
    private static final int SCHOOL_LENGTH_ANY = -1;
    private static final int SCHOOL_LENGTH_UNKNOWN = 0;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AspirationMapper aspirationMapper;
    private final FillDao fillDao;

    /* ======================== 顶部摘要 ======================== */

    @Override
    public Map<String, Object> header(String code, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("aspirationCode", a.getAspirationCode());
        h.put("studentName", a.getStudentName());
        h.put("gender", a.getGender());
        h.put("genderText", genderText(a.getGender()));
        h.put("province", a.getProvince());
        h.put("year", a.getYear());
        h.put("batch", a.getBatch());
        h.put("groupName", a.getGroupName());
        h.put("subjectType", subjectType(a));
        h.put("artsOrScience", artsOrScience(subjectType(a)));
        h.put("firstSubject", a.getFirstSubject());
        h.put("secondSubjects", secondSubjects(a));
        h.put("subjectsDisplay", String.join("、", studentSubjects(a)));
        h.put("score", a.getScore());
        h.put("bonusScore", a.getBonusScore());
        Map<String, Object> rk = fillDao.scoreRanking(a.getProvince(), a.getYear(), subjectType(a), a.getScore());
        h.put("rank", rk == null ? null : rk.get("cumulative"));
        h.put("sameCount", rk == null || !Objects.equals(toInt(rk.get("score")), a.getScore()) ? 0 : rk.get("same_count"));
        h.put("aspirationName", a.getAspirationName());
        h.put("highSchool", a.getHighSchool());
        h.put("contactPhone", a.getContactPhone());
        h.put("ethnicity", a.getEthnicity());
        h.put("isFresh", a.getIsFresh());
        h.put("chineseScore", a.getChineseScore());
        h.put("mathScore", a.getMathScore());
        h.put("englishScore", a.getEnglishScore());
        h.put("elective1Score", a.getElective1Score());
        h.put("elective2Score", a.getElective2Score());
        h.put("elective3Score", a.getElective3Score());
        h.put("heightCm", a.getHeightCm());
        h.put("weightKg", a.getWeightKg());
        h.put("leftVision", a.getLeftVision());
        h.put("rightVision", a.getRightVision());
        h.put("colorBlind", a.getColorBlind());
        h.put("colorWeakness", a.getColorWeakness());
        h.put("parentName", a.getParentName());
        h.put("fatherJob", a.getFatherJob());
        h.put("motherJob", a.getMotherJob());
        h.put("submitStatus", a.getSubmitStatus());
        h.put("upperParam", a.getUpperParam() == null ? 20 : a.getUpperParam());
        h.put("lowerParam", a.getLowerParam() == null ? 60 : a.getLowerParam());
        h.put("selectedCount", fillDao.countSelected(code));
        h.put("recommendCount", fillDao.countRecommend(code));
        h.put("upperLimit", upperLimit(a));
        Map<String, Object> line = fillDao.controlLine(a.getProvince(), a.getYear(), subjectType(a), a.getBatch());
        h.put("controlScore", line == null ? null : line.get("score"));
        Map<String, Object> prob = new LinkedHashMap<>();
        prob.put("upperBound", UPPER_BOUND);
        prob.put("lowerBound", LOWER_BOUND);
        prob.put("minMarkProbability", MIN_MARK_PROBABILITY);
        prob.put("avgMarkProbability", AVG_MARK_PROBABILITY);
        h.put("probabilityParams", prob);
        return h;
    }

    /* ======================== 智能专业推荐 ======================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recommend(String code, Integer upperParam, Integer lowerParam, Long uid, String role) {
        Aspiration a = loadEditable(code, uid, role);
        checkSlider(upperParam);
        checkSlider(lowerParam);
        ensureFilterDefaults(code);

        Map<String, Object> line = fillDao.controlLine(a.getProvince(), a.getYear(), subjectType(a), a.getBatch());
        int lineScore = line == null ? 0 : toInt(line.get("score"));
        int low = Math.max(a.getScore() - lowerParam, lineScore);
        int high = a.getScore() + upperParam;

        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(baseScope(a, p));
        where.append(" AND m.pre_min_mark BETWEEN :low AND :high");
        p.addValue("low", low).addValue("high", high);
        where.append(subjectCondition(a, p));
        where.append(univFilterCondition(code, p));
        where.append(otherFilterCondition(code, p));
        where.append(majorFilterCondition(code, p));

        fillDao.deleteRecommend(code);
        int count = fillDao.insertRecommend(code, where.toString(),
                "m.pre_min_mark DESC, m.unv_code, m.recruit_major_code", p);

        Aspiration up = new Aspiration();
        up.setId(a.getId());
        up.setUpperParam(upperParam);
        up.setLowerParam(lowerParam);
        aspirationMapper.updateById(up);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordCount", count);
        out.put("lowMark", low);
        out.put("highMark", high);
        return out;
    }

    @Override
    public Map<String, Object> recommendList(String code, Integer page, Integer size, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? RECOMMEND_PAGE_SIZE : Math.min(size, RECOMMEND_PAGE_MAX);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", fillDao.countRecommend(code));
        out.put("page", p);
        out.put("size", s);
        out.put("list", buildRows(fillDao.recommendPage(code, (p - 1) * s, s), a));
        return out;
    }

    /* ======================== 按高校检索 / 首次招生高校 ======================== */

    @Override
    public List<Map<String, Object>> searchUnv(String code, Map<String, Object> body, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(baseScope(a, p));
        where.append(subjectCondition(a, p));
        where.append(searchTextCondition(body, p));
        Integer maxMark = toIntOrNull(body.get("maxMark"));
        if (maxMark != null) {
            where.append(" AND m.pre_min_mark <= :maxMark");
            p.addValue("maxMark", maxMark);
        }
        where.append(" ORDER BY m.pre_min_mark IS NULL, m.pre_min_mark DESC, m.unv_code, m.recruit_major_code LIMIT ")
                .append(SEARCH_UNV_LIMIT);
        List<Map<String, Object>> rows = buildRowsFromMajors(fillDao.queryMajors(where.toString(), p), a);
        rows.sort(Comparator.comparingDouble((Map<String, Object> r) -> (Double) r.get("RecruitProbability"))
                .thenComparing(r -> -nz(r.get("PreMinMark")))
                .thenComparing(r -> -nz(r.get("PreAvgMark"))));
        return rows;
    }

    @Override
    public List<Map<String, Object>> searchDebut(String code, Map<String, Object> body, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(baseScope(a, p));
        where.append(" AND m.first_time_admission = 1");
        where.append(subjectCondition(a, p));
        where.append(searchTextCondition(body, p));
        where.append(" ORDER BY m.unv_code, m.recruit_major_code LIMIT ").append(SEARCH_DEBUT_LIMIT);
        return buildRowsFromMajors(fillDao.queryMajors(where.toString(), p), a);
    }

    /* ======================== 查看结果(已选志愿) ======================== */

    @Override
    public List<Map<String, Object>> selectedList(String code, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        List<Map<String, Object>> sel = fillDao.selected(code);
        List<Map<String, Object>> rows = buildRows(sel.stream().map(s -> (String) s.get("recruit_major_id")).toList(), a);
        Map<String, Map<String, Object>> byRid = sel.stream()
                .collect(Collectors.toMap(s -> (String) s.get("recruit_major_id"), Function.identity()));
        for (Map<String, Object> r : rows) {
            Map<String, Object> s = byRid.get((String) r.get("RecruitMajorID"));
            r.put("IsNew", toInt(s.get("is_new")) == 1);
            r.put("SortNo", toInt(s.get("sort_no")));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addSelected(String code, String rid, Long uid, String role) {
        Aspiration a = loadEditable(code, uid, role);
        if (rid == null || rid.isBlank()) {
            throw new BizException("志愿单元ID不能为空");
        }
        List<Map<String, Object>> majors = fillDao.majorsByRids(List.of(rid));
        if (majors.isEmpty()) {
            throw new BizException("招生专业不存在: " + rid);
        }
        checkScope(a, majors.get(0));
        boolean exists = fillDao.selected(code).stream().anyMatch(s -> rid.equals(s.get("recruit_major_id")));
        if (exists) {
            throw new BizException("该专业已在志愿表中");
        }
        int limit = upperLimit(a);
        if (fillDao.countSelected(code) >= limit) {
            throw new BizException(a.getProvince() + "限定填报数量为 " + limit + " 项, 已达上限");
        }
        fillDao.insertSelected(code, List.of(rid), fillDao.maxSelectedSort(code) + 1);
        return Map.of("selectedCount", fillDao.countSelected(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> removeSelected(String code, String rid, Long uid, String role) {
        loadEditable(code, uid, role);
        if (fillDao.deleteSelected(code, rid) == 0) {
            throw new BizException("该专业不在志愿表中");
        }
        resequence(code);
        return Map.of("selectedCount", fillDao.countSelected(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAllSelected(String code, Long uid, String role) {
        loadEditable(code, uid, role);
        fillDao.deleteAllSelected(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearHighlight(String code, Long uid, String role) {
        loadEditable(code, uid, role);
        fillDao.clearNew(code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrder(String code, List<String> rids, Long uid, String role) {
        loadEditable(code, uid, role);
        if (rids == null) {
            throw new BizException("排序列表不能为空");
        }
        Set<String> current = fillDao.selected(code).stream()
                .map(s -> (String) s.get("recruit_major_id")).collect(Collectors.toSet());
        if (rids.size() != current.size() || !current.equals(new HashSet<>(rids))) {
            throw new BizException("志愿表已发生变化, 请刷新后重新排序");
        }
        fillDao.updateSelectedSort(code, rids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> autoOrder(String code, Integer method, Long uid, String role) {
        Aspiration a = loadEditable(code, uid, role);
        List<Map<String, Object>> rows = new ArrayList<>(selectedList(code, uid, role));
        Comparator<Map<String, Object>> byPreMin = Comparator.comparing(
                (Map<String, Object> r) -> toIntOrNull(r.get("PreMinMark")), Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Map<String, Object>> cmp = switch (method == null ? 0 : method) {
            // 录取概率低的(冲)在前, 未知概率(9999)自然排到最后
            case 1 -> Comparator.comparingDouble((Map<String, Object> r) -> (Double) r.get("RecruitProbability"))
                    .thenComparing(byPreMin);
            case 2 -> byPreMin;
            case 3 -> Comparator.comparing((Map<String, Object> r) -> lastYearMinMark(r),
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byPreMin);
            default -> throw new BizException("排序方式不合法");
        };
        rows.sort(cmp.thenComparing(r -> toInt(r.get("SortNo"))));
        List<String> rids = rows.stream().map(r -> (String) r.get("RecruitMajorID")).toList();
        fillDao.updateSelectedSort(a.getAspirationCode(), rids);
        return rids;
    }

    @SuppressWarnings("unchecked")
    private Integer lastYearMinMark(Map<String, Object> row) {
        List<Map<String, Object>> hs = (List<Map<String, Object>>) row.get("MajorHistorys");
        return hs.isEmpty() ? null : toIntOrNull(hs.get(0).get("MinMark"));
    }

    /* ======================== 一键填报 ======================== */

    @Override
    public Map<String, Object> oneClickInfo(String code, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        int selected = fillDao.countSelected(code);
        int limit = upperLimit(a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("province", a.getProvince());
        out.put("selectedCount", selected);
        out.put("upperLimit", limit);
        out.put("max", Math.max(limit + ONE_CLICK_EXTRA - selected, 0));
        out.put("init", Math.max(limit - selected, 0));
        out.put("recommendCount", fillDao.countRecommend(code));
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> oneClick(String code, Integer count, Long uid, String role) {
        Aspiration a = loadEditable(code, uid, role);
        int selected = fillDao.countSelected(code);
        int max = Math.max(upperLimit(a) + ONE_CLICK_EXTRA - selected, 0);
        if (count == null || count < 0 || count > max) {
            throw new BizException("自动填报记录数必须在 0 到 " + max + " 之间");
        }
        if (fillDao.countRecommend(code) == 0) {
            throw new BizException("暂无推荐专业, 请先进行智能筛选");
        }
        List<String> picks = count == 0 ? List.of() : fillDao.recommendNotSelected(code, count);
        fillDao.insertSelected(code, picks, fillDao.maxSelectedSort(code) + 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", picks.size());
        out.put("selectedCount", fillDao.countSelected(code));
        return out;
    }

    /* ======================== 院校筛选 ======================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> univFilter(String code, Long uid, String role) {
        loadOwned(code, uid, role);
        ensureFilterDefaults(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("options", UNIV_OPTIONS);
        out.put("excluded", excludedUniv(code));
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUnivFilter(String code, Map<String, Object> body, Long uid, String role) {
        loadEditable(code, uid, role);
        ensureFilterDefaults(code);
        Map<String, List<String>> excluded = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> opt : UNIV_OPTIONS.entrySet()) {
            List<String> values = toStringList(body.get(opt.getKey()));
            for (String v : values) {
                if (!opt.getValue().contains(v)) {
                    throw new BizException("院校筛选取值不合法: " + v);
                }
            }
            List<String> distinct = values.stream().distinct().toList();
            if (distinct.size() >= opt.getValue().size()) {
                throw new BizException(UNIV_EMPTY_MESSAGES.get(opt.getKey()));
            }
            excluded.put(opt.getKey(), distinct);
        }
        fillDao.replaceUnivFilter(code, excluded);
    }

    /* ======================== 其它筛选 ======================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> otherFilter(String code, Long uid, String role) {
        loadOwned(code, uid, role);
        ensureFilterDefaults(code);
        Map<String, Object> f = fillDao.otherFilter(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("presetKeywords", PRESET_KEYWORDS);
        Integer sl = toIntOrNull(f.get("school_length"));
        out.put("schoolLength", sl == null ? SCHOOL_LENGTH_ANY : sl);
        out.put("tuitionMin", f.get("tuition_min"));
        out.put("tuitionMax", f.get("tuition_max"));
        out.put("includeUnknownTuition", toInt(f.get("include_unknown_tuition")) == 1);
        out.put("presetInclude", splitComma((String) f.get("preset_include")));
        out.put("presetExclude", splitComma((String) f.get("preset_exclude")));
        out.put("customInclude", nvl((String) f.get("custom_include")));
        out.put("customExclude", nvl((String) f.get("custom_exclude")));
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOtherFilter(String code, Map<String, Object> body, Long uid, String role) {
        loadEditable(code, uid, role);
        Integer sl = toIntOrNull(body.get("schoolLength"));
        if (sl == null) sl = SCHOOL_LENGTH_ANY;
        if (sl < SCHOOL_LENGTH_ANY || sl > 9) {
            throw new BizException("学制取值不合法");
        }
        Integer tmin = toIntOrNull(body.get("tuitionMin"));
        Integer tmax = toIntOrNull(body.get("tuitionMax"));
        if ((tmin != null && tmin < 0) || (tmax != null && tmax < 0)) {
            throw new BizException("学费不能为负数");
        }
        if (tmin != null && tmax != null && tmin > tmax) {
            throw new BizException("学费下限不能高于上限");
        }
        boolean includeUnknown = !Boolean.FALSE.equals(body.get("includeUnknownTuition"));
        List<String> pi = toStringList(body.get("presetInclude")).stream().distinct().toList();
        List<String> pe = toStringList(body.get("presetExclude")).stream().distinct().toList();
        for (String k : concat(pi, pe)) {
            if (!PRESET_KEYWORDS.contains(k)) {
                throw new BizException("预设关键字不合法: " + k);
            }
        }
        String ci = normalizeKeywords(body.get("customInclude"));
        String ce = normalizeKeywords(body.get("customExclude"));
        Set<String> inc = new HashSet<>(concat(pi, splitComma(ci)));
        for (String k : concat(pe, splitComma(ce))) {
            if (inc.contains(k)) {
                throw new BizException("关键字【" + k + "】不能同时出现在包含与不包含中");
            }
        }
        if (ci.length() > 255 || ce.length() > 255) {
            throw new BizException("自定义关键字过长(最多255字)");
        }
        fillDao.saveOtherFilter(code, sl == SCHOOL_LENGTH_ANY ? null : sl, tmin, tmax, includeUnknown,
                String.join(",", pi), String.join(",", pe), ci, ce);
    }

    /* ======================== 专业筛选(白名单) ======================== */

    @Override
    public List<Map<String, Object>> majorFilter(String code, Long uid, String role) {
        loadOwned(code, uid, role);
        return fillDao.majorFilter(code).stream().map(this::dictRow).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMajorFilter(String code, List<Long> majorIds, Long uid, String role) {
        loadEditable(code, uid, role);
        Set<Long> ids = majorIds == null ? Set.of() : new LinkedHashSet<>(majorIds);
        if (ids.isEmpty()) {
            throw new BizException("请至少选择一个专业");
        }
        if (fillDao.countMajorIds(ids) != ids.size()) {
            throw new BizException("包含不存在的专业");
        }
        fillDao.addMajorFilter(code, ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMajorFilter(String code, List<Long> majorIds, Long uid, String role) {
        loadEditable(code, uid, role);
        if (majorIds == null || majorIds.isEmpty()) {
            throw new BizException("请至少选择一个专业");
        }
        fillDao.removeMajorFilter(code, new LinkedHashSet<>(majorIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMajorFilter(String code, Long uid, String role) {
        loadEditable(code, uid, role);
        fillDao.clearMajorFilter(code);
    }

    @Override
    public Map<String, Object> majorDictOptions() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", fillDao.majorCategories());
        out.put("disciplines", fillDao.majorDisciplines());
        return out;
    }

    @Override
    public Map<String, Object> majorCandidates(String category, String discipline, String name) {
        List<Map<String, Object>> list = fillDao.majorCandidates(blankToNull(category), blankToNull(discipline),
                blankToNull(name), MAJOR_CANDIDATE_LIMIT + 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overflow", list.size() > MAJOR_CANDIDATE_LIMIT);
        out.put("list", list.stream().limit(MAJOR_CANDIDATE_LIMIT).map(this::dictRow).toList());
        return out;
    }

    /* ======================== 明细弹窗 ======================== */

    @Override
    public Map<String, Object> unvDetail(String code, String uabId, Long uid, String role) {
        loadOwned(code, uid, role);
        return unvDetailByUab(uabId);
    }

    @Override
    public Map<String, Object> unvDetailByUab(String uabId) {
        List<Map<String, Object>> majors = fillDao.majorsByUab(uabId);
        if (majors.isEmpty()) {
            throw new BizException("院校不存在: " + uabId);
        }
        Map<String, Object> m0 = majors.get(0);
        String st = (String) m0.get("subject_type");
        String batch = (String) m0.get("batch");
        String province = (String) m0.get("province");
        int year = toInt(m0.get("year"));

        Integer preMin = majors.stream().map(m -> toIntOrNull(m.get("pre_min_mark")))
                .filter(Objects::nonNull).min(Integer::compare).orElse(null);
        Integer preAvg = weightedAvg(majors.stream()
                .map(m -> new Integer[]{firstNonNull(toIntOrNull(m.get("pre_avg_mark")), toIntOrNull(m.get("pre_min_mark"))),
                        toIntOrNull(m.get("major_plan"))}).toList());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("UnvName", m0.get("unv_name"));
        info.put("UnvCode", m0.get("unv_code"));
        info.put("Year", year);
        info.put("Province", province);
        info.put("Batch", batch);
        info.put("ArtsOrScience", artsOrScience(st));
        info.put("PreAvgMark", preAvg);
        info.put("PreMinMark", preMin);
        Integer avgRank = rankOf(province, year, st, preAvg);
        Integer minRank = rankOf(province, year, st, preMin);
        info.put("PreAvgMarkRank", avgRank);
        info.put("PreMinMarkRank", minRank);
        info.put("PreAvgPercentile", percentileOf(province, year, st, batch, avgRank));
        info.put("PreMinPercentile", percentileOf(province, year, st, batch, minRank));

        List<Map<String, Object>> yearly = unvHistoryRows(majors, fillDao.historiesByUab(uabId), year);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("info", info);
        out.put("tracked", yearly);
        out.put("all", yearly);
        out.put("majors", majors.stream().map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("RecruitMajorID", m.get("recruit_major_id"));
            r.put("RecruitMajorCode", m.get("recruit_major_code"));
            r.put("MajorName", m.get("major_name"));
            r.put("FirstSubject", firstSubjectOf(st));
            r.put("SecondSubjects", secondRequirement(m, st));
            r.put("MajorPlan", m.get("major_plan"));
            r.put("PredictMethod", "常规预测");
            r.put("PreAvgMark", m.get("pre_avg_mark"));
            r.put("PreMinMark", m.get("pre_min_mark"));
            r.put("PreAvgPercentile", null);
            r.put("PreMinPercentile", toDouble(m.get("pre_min_percentile")));
            return r;
        }).toList());
        return out;
    }

    @Override
    public Map<String, Object> majorDetail(String code, String rid, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        List<String> one = List.of(rid == null ? "" : rid);
        List<Map<String, Object>> rows = buildRows(one, a);
        if (rows.isEmpty()) {
            throw new BizException("招生专业不存在: " + rid);
        }
        Map<String, Object> out = majorHistoryBundle(rid);
        out.put("info", rows.get(0));
        return out;
    }

    @Override
    public Map<String, Object> majorDetailByRid(String rid) {
        if (rid == null || rid.isBlank()) {
            throw new BizException("招生专业不存在");
        }
        List<Map<String, Object>> majors = fillDao.majorsByRids(List.of(rid));
        if (majors.isEmpty()) {
            throw new BizException("招生专业不存在: " + rid);
        }
        Map<String, Object> m = majors.get(0);
        String st = (String) m.get("subject_type");
        String province = (String) m.get("province");
        String batch = (String) m.get("batch");
        int year = toInt(m.get("year"));
        Integer preAvg = toIntOrNull(m.get("pre_avg_mark"));
        Integer preMin = toIntOrNull(m.get("pre_min_mark"));
        Integer avgRank = rankOf(province, year, st, preAvg);
        Integer minRank = firstNonNull(toIntOrNull(m.get("pre_min_rank")), rankOf(province, year, st, preMin));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("Year", year);
        info.put("Batch", batch);
        info.put("ArtsOrScience", artsOrScience(st));
        info.put("UnvCode", m.get("unv_code"));
        info.put("UnvName", m.get("unv_name"));
        info.put("RecruitMajorCode", m.get("recruit_major_code"));
        info.put("MajorName", m.get("major_name"));
        info.put("RecruitPlanExplain", m.get("plan_explain"));
        info.put("PreAvgMark", preAvg);
        info.put("PreMinMark", preMin);
        info.put("MajorPlan", m.get("major_plan"));
        info.put("PreAvgPercentile", percentileOf(province, year, st, batch, avgRank));
        Double preMinPct = toDouble(m.get("pre_min_percentile"));
        info.put("PreMinPercentile", preMinPct != null ? preMinPct
                : percentileOf(province, year, st, batch, minRank));
        info.put("SchoolLength", m.get("school_length"));
        info.put("PreAvgMarkRank", avgRank);
        info.put("PreMinMarkRank", minRank);
        info.put("TuitionText", tuitionText(m));

        Map<String, Object> out = majorHistoryBundle(rid);
        out.put("info", info);
        return out;
    }

    private Map<String, Object> majorHistoryBundle(String rid) {
        List<Map<String, Object>> majors = fillDao.majorsByRids(List.of(rid == null ? "" : rid));
        if (majors.isEmpty()) {
            throw new BizException("招生专业不存在: " + rid);
        }
        Map<String, Object> m = majors.get(0);
        String st = (String) m.get("subject_type");
        List<Map<String, Object>> histories = fillDao.historiesByRids(List.of(rid)).stream()
                .filter(h -> h.get("plan") != null || h.get("min_mark") != null || h.get("avg_mark") != null)
                .map(h -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("Year", h.get("year"));
                    r.put("Batch", m.get("batch"));
                    r.put("ArtsOrScience", artsOrScience(st));
                    r.put("UnvCode", h.get("hist_recruit_major_id") == null ? "-"
                            : unvCodeOf((String) h.get("hist_recruit_major_id")));
                    r.put("UnvName", m.get("unv_name"));
                    r.put("RecruitMajorCode", h.get("hist_recruit_major_id") == null ? "-"
                            : majorCodeOf((String) h.get("hist_recruit_major_id")));
                    r.put("MajorName", m.get("major_name"));
                    r.put("Plan", h.get("plan"));
                    r.put("AvgMark", h.get("avg_mark"));
                    r.put("MinMark", h.get("min_mark"));
                    r.put("AvgMarkPercentile", toDouble(h.get("avg_mark_percentile")));
                    r.put("MinMarkPercentile", toDouble(h.get("min_mark_percentile")));
                    r.put("AvgMarkRank", h.get("avg_mark_rank"));
                    r.put("MinMarkRank", h.get("min_mark_rank"));
                    return r;
                }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("histories", histories);
        return out;
    }

    @Override
    public List<Map<String, Object>> majorMapping(String code, String rid, Long uid, String role) {
        loadOwned(code, uid, role);
        List<Map<String, Object>> majors = fillDao.majorsByRids(List.of(rid == null ? "" : rid));
        if (majors.isEmpty()) {
            throw new BizException("招生专业不存在: " + rid);
        }
        Map<String, Object> m = majors.get(0);
        List<String> codes = splitComma((String) m.get("major_codes"));
        Map<String, Map<String, Object>> dict = fillDao.majorsByCodes(codes).stream()
                .collect(Collectors.toMap(d -> (String) d.get("major_code"), Function.identity(), (x, y) -> x));
        String level = "本科批".equals(m.get("batch")) ? "本科" : "专科";
        return codes.stream().map(c -> {
            Map<String, Object> d = dict.get(c);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("MajorCode", c);
            r.put("MajorName", d == null ? "" : d.get("major_name"));
            r.put("Level", level);
            r.put("DisciplineCode", c.length() >= 4 ? c.substring(0, 4) : c);
            r.put("DisciplineName", d == null ? "" : d.get("first_discipline"));
            return r;
        }).toList();
    }

    /* ======================== 报告 / 导出 ======================== */

    @Override
    public Map<String, Object> report(String code, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("header", header(code, uid, role));
        out.put("ranking", fillDao.rankingTable(a.getProvince(), a.getYear(), subjectType(a)).stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("score", r.get("score"));
            x.put("sameCount", r.get("same_count"));
            x.put("cumulative", r.get("cumulative"));
            return x;
        }).toList());
        out.put("majors", selectedList(code, uid, role));
        return out;
    }

    @Override
    public ExportFile export(String code, List<String> rids, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        List<Map<String, Object>> rows = selectedList(code, uid, role);
        if (rids != null && !rids.isEmpty()) {
            Set<String> pick = new HashSet<>(rids);
            rows = rows.stream().filter(r -> pick.contains((String) r.get("RecruitMajorID"))).toList();
        }
        if (rows.isEmpty()) {
            throw new BizException("没有可导出的志愿");
        }
        Map<String, Object> h = header(code, uid, role);
        String fileName = a.getStudentName() + "（" + a.getScore() + "分）" + a.getBatch()
                + "志愿表【" + LocalDate.now().format(YMD) + "】.xlsx";
        return new ExportFile(fileName, buildWorkbook(a, h, rows));
    }

    /* ======================== 私有: 方案加载与范围 ======================== */

    private Aspiration loadOwned(String code, Long uid, String role) {
        if (code == null || code.isBlank()) {
            throw new BizException("方案码不能为空");
        }
        Aspiration a = aspirationMapper.selectOne(new LambdaQueryWrapper<Aspiration>()
                .eq(Aspiration::getAspirationCode, code)
                .eq(Aspiration::getDeleted, 0));
        if (a == null) {
            throw new BizException("志愿方案不存在或已删除");
        }
        return a;
    }

    private Aspiration loadEditable(String code, Long uid, String role) {
        Aspiration a = loadOwned(code, uid, role);
        if (a.getSubmitStatus() != null && a.getSubmitStatus() == 1) {
            throw new BizException("该方案已交单, 志愿已锁定, 如需修改请先撤销交单");
        }
        return a;
    }

    private void checkSlider(Integer v) {
        if (v == null || v < 0 || v > 200) {
            throw new BizException("滑杆分数不合法(0-200)");
        }
    }

    private void checkScope(Aspiration a, Map<String, Object> m) {
        if (!Objects.equals(a.getProvince(), m.get("province")) || !Objects.equals(a.getYear(), toIntOrNull(m.get("year")))
                || !Objects.equals(a.getBatch(), m.get("batch")) || !Objects.equals(subjectType(a), m.get("subject_type"))) {
            throw new BizException("该专业不属于本方案的省份/年份/批次/科类");
        }
    }

    private int upperLimit(Aspiration a) {
        return UPPER_LIMITS.getOrDefault(a.getProvince(), 96);
    }

    private String subjectType(Aspiration a) {
        return a.getFirstSubject() + "类";
    }

    private List<String> secondSubjects(Aspiration a) {
        return splitComma(a.getSecondSubjects());
    }

    private List<String> studentSubjects(Aspiration a) {
        List<String> s = new ArrayList<>();
        s.add(a.getFirstSubject());
        s.addAll(secondSubjects(a));
        return s;
    }

    private void resequence(String code) {
        fillDao.updateSelectedSort(code, fillDao.selected(code).stream()
                .map(s -> (String) s.get("recruit_major_id")).toList());
    }

    /* ======================== 私有: 查询条件 ======================== */

    private String baseScope(Aspiration a, MapSqlParameterSource p) {
        p.addValue("province", a.getProvince()).addValue("year", a.getYear())
                .addValue("batch", a.getBatch()).addValue("subjectType", subjectType(a));
        return "m.province=:province AND m.year=:year AND m.batch=:batch AND m.subject_type=:subjectType";
    }

    /** 选科: 专业要求的每一科都必须在考生选科内 */
    private String subjectCondition(Aspiration a, MapSqlParameterSource p) {
        Set<String> mine = new HashSet<>(studentSubjects(a));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : ALL_SUBJECTS) {
            if (!mine.contains(s)) {
                sb.append(" AND FIND_IN_SET(:subj").append(i).append(", m.required_subjects)=0");
                p.addValue("subj" + i, s);
                i++;
            }
        }
        return sb.toString();
    }

    private String searchTextCondition(Map<String, Object> body, MapSqlParameterSource p) {
        StringBuilder sb = new StringBuilder();
        String unv = blankToNull(body.get("unvName"));
        if (unv != null) {
            sb.append(" AND m.unv_name LIKE :unvName");
            p.addValue("unvName", "%" + escapeLike(unv) + "%");
        }
        String major = blankToNull(body.get("majorName"));
        if (major != null) {
            sb.append(" AND m.major_name LIKE :majorName");
            p.addValue("majorName", "%" + escapeLike(major) + "%");
        }
        String city = blankToNull(body.get("city"));
        if (city != null) {
            sb.append(" AND (m.unv_province LIKE :city OR m.unv_city LIKE :city)");
            p.addValue("city", "%" + escapeLike(city) + "%");
        }
        return sb.toString();
    }

    private String univFilterCondition(String code, MapSqlParameterSource p) {
        Map<String, List<String>> ex = excludedUniv(code);
        StringBuilder sb = new StringBuilder();
        if (!ex.get(G_PROVINCE).isEmpty()) {
            sb.append(" AND m.unv_province NOT IN (:exProvince)");
            p.addValue("exProvince", ex.get(G_PROVINCE));
        }
        if (!ex.get(G_UNV_TYPE).isEmpty()) {
            sb.append(" AND m.unv_type NOT IN (:exType)");
            p.addValue("exType", ex.get(G_UNV_TYPE));
        }
        if (!ex.get(G_UNV_NATURE).isEmpty()) {
            sb.append(" AND m.unv_nature NOT IN (:exNature)");
            p.addValue("exNature", ex.get(G_UNV_NATURE));
        }
        if (!ex.get(G_UNV_ATTRIBUTE).isEmpty()) {
            // 院校带多个标签(双一流,985,211), 任一标签被勾选即保留; 无标签的院校归"其它"
            List<String> or = new ArrayList<>();
            int i = 0;
            for (String v : UNIV_OPTIONS.get(G_UNV_ATTRIBUTE)) {
                if (ex.get(G_UNV_ATTRIBUTE).contains(v)) continue;
                if (OTHER_ATTRIBUTE.equals(v)) {
                    or.add("COALESCE(m.unv_attributes,'')=''");
                } else {
                    or.add("FIND_IN_SET(:attr" + i + ", m.unv_attributes)>0");
                    p.addValue("attr" + i, v);
                    i++;
                }
            }
            sb.append(" AND (").append(String.join(" OR ", or)).append(")");
        }
        return sb.toString();
    }

    private String otherFilterCondition(String code, MapSqlParameterSource p) {
        Map<String, Object> f = fillDao.otherFilter(code);
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        Integer sl = toIntOrNull(f.get("school_length"));
        if (sl != null) {
            if (sl == SCHOOL_LENGTH_UNKNOWN) {
                sb.append(" AND m.school_length IS NULL");
            } else {
                sb.append(" AND m.school_length = :schoolLength");
                p.addValue("schoolLength", sl);
            }
        }
        Integer tmin = toIntOrNull(f.get("tuition_min"));
        Integer tmax = toIntOrNull(f.get("tuition_max"));
        boolean includeUnknown = toInt(f.get("include_unknown_tuition")) == 1;
        List<String> known = new ArrayList<>();
        if (tmin != null) {
            known.add("m.tuition >= :tuitionMin");
            p.addValue("tuitionMin", tmin);
        }
        if (tmax != null) {
            known.add("m.tuition <= :tuitionMax");
            p.addValue("tuitionMax", tmax);
        }
        String knownCond = known.isEmpty() ? "m.tuition IS NOT NULL"
                : "(m.tuition IS NOT NULL AND " + String.join(" AND ", known) + ")";
        if (includeUnknown) {
            if (!known.isEmpty()) sb.append(" AND (m.tuition IS NULL OR ").append(knownCond).append(")");
        } else {
            sb.append(" AND ").append(knownCond);
        }

        String text = "CONCAT_WS('', m.major_name, m.plan_explain, m.recruit_type)";
        List<String> include = concat(splitComma((String) f.get("preset_include")), splitComma((String) f.get("custom_include")));
        List<String> exclude = concat(splitComma((String) f.get("preset_exclude")), splitComma((String) f.get("custom_exclude")));
        int i = 0;
        if (!include.isEmpty()) {
            List<String> or = new ArrayList<>();
            for (String kw : include) {
                for (String alt : kw.split("\\$")) {
                    if (alt.isBlank()) continue;
                    or.add(text + " LIKE :kwIn" + i);
                    p.addValue("kwIn" + i, "%" + escapeLike(alt.trim()) + "%");
                    i++;
                }
            }
            if (!or.isEmpty()) sb.append(" AND (").append(String.join(" OR ", or)).append(")");
        }
        for (String kw : exclude) {
            for (String alt : kw.split("\\$")) {
                if (alt.isBlank()) continue;
                sb.append(" AND ").append(text).append(" NOT LIKE :kwEx").append(i);
                p.addValue("kwEx" + i, "%" + escapeLike(alt.trim()) + "%");
                i++;
            }
        }
        return sb.toString();
    }

    /** 专业白名单非空时, 招生专业对应的教育部专业代码与白名单有交集才保留 */
    private String majorFilterCondition(String code, MapSqlParameterSource p) {
        List<String> codes = fillDao.majorFilterCodes(code);
        if (codes.isEmpty()) return "";
        List<String> or = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            or.add("FIND_IN_SET(:mf" + i + ", m.major_codes)>0");
            p.addValue("mf" + i, codes.get(i));
        }
        return " AND (" + String.join(" OR ", or) + ")";
    }

    /** 首次进入筛选时写入参考系统的默认筛选(不含中外合作/港澳台合作办学, 不含专项/预科/少数民族/八协) */
    private void ensureFilterDefaults(String code) {
        if (fillDao.otherFilter(code) != null) return;
        fillDao.saveOtherFilter(code, null, null, null, true, "", DEFAULT_PRESET_EXCLUDE, "", "");
        Map<String, List<String>> ex = new LinkedHashMap<>();
        UNIV_DEFAULT_EXCLUDED.forEach(ex::put);
        fillDao.replaceUnivFilter(code, ex);
    }

    private Map<String, List<String>> excludedUniv(String code) {
        Map<String, List<String>> ex = new LinkedHashMap<>();
        UNIV_OPTIONS.keySet().forEach(k -> ex.put(k, new ArrayList<>()));
        for (Map<String, Object> r : fillDao.univFilter(code)) {
            List<String> l = ex.get((String) r.get("filter_group"));
            if (l != null) l.add((String) r.get("filter_value"));
        }
        return ex;
    }

    /* ======================== 私有: 行数据(与参考系统 GetRecommendMajors 字段同名) ======================== */

    private List<Map<String, Object>> buildRows(List<String> ridsInOrder, Aspiration a) {
        if (ridsInOrder.isEmpty()) return new ArrayList<>();
        Map<String, Map<String, Object>> byRid = fillDao.majorsByRids(ridsInOrder).stream()
                .collect(Collectors.toMap(m -> (String) m.get("recruit_major_id"), Function.identity()));
        List<Map<String, Object>> majors = ridsInOrder.stream().map(byRid::get).filter(Objects::nonNull).toList();
        return buildRowsFromMajors(majors, a);
    }

    private List<Map<String, Object>> buildRowsFromMajors(List<Map<String, Object>> majors, Aspiration a) {
        if (majors.isEmpty()) return new ArrayList<>();
        Map<String, List<Map<String, Object>>> hist = fillDao.historiesByRids(
                        majors.stream().map(m -> (String) m.get("recruit_major_id")).toList()).stream()
                .collect(Collectors.groupingBy(h -> (String) h.get("recruit_major_id")));
        List<Map<String, Object>> out = new ArrayList<>(majors.size());
        for (Map<String, Object> m : majors) {
            out.add(toRow(m, hist.getOrDefault((String) m.get("recruit_major_id"), List.of()), a.getScore()));
        }
        return out;
    }

    private Map<String, Object> toRow(Map<String, Object> m, List<Map<String, Object>> hist, int userScore) {
        Map<String, Object> r = new LinkedHashMap<>();
        String st = (String) m.get("subject_type");
        int year = toInt(m.get("year"));
        r.put("uabID", m.get("uab_id"));
        r.put("UnvCode", m.get("unv_code"));
        r.put("UnvName", m.get("unv_name"));
        r.put("UnvShortName", m.get("unv_short_name"));
        r.put("MapUrl", nvl((String) m.get("vr_url")));
        r.put("BaikeUrl", nvl((String) m.get("baike_url")));
        r.put("CharterUrl", nvl((String) m.get("charter_url")));
        r.put("ToolTipText", "");
        r.put("NewName", "");
        r.put("HasMasterUnit", toInt(m.get("has_master")) == 1);
        r.put("HasDoctorUnit", toInt(m.get("has_doctor")) == 1);
        r.put("CanRecommend", toInt(m.get("can_recommend")) == 1);
        r.put("HotUrl", "");
        r.put("Year", year);
        r.put("Batch", m.get("batch"));
        r.put("ArtsOrScience", artsOrScience(st));
        r.put("Province", m.get("unv_province"));
        r.put("City", m.get("unv_city"));
        r.put("UnvRank", m.get("unv_rank"));
        r.put("UnvAttributes", splitComma((String) m.get("unv_attributes")));
        r.put("UnvNature", m.get("unv_nature"));
        r.put("UnvType", m.get("unv_type"));
        r.put("RecruitMajorID", m.get("recruit_major_id"));
        r.put("RecruitMajorCode", m.get("recruit_major_code"));
        r.put("MajorName", m.get("major_name"));
        r.put("MajorPlan", m.get("major_plan"));
        r.put("SchoolLength", m.get("school_length"));
        r.put("Tuition", m.get("tuition"));
        r.put("TuitionText", tuitionText(m));
        r.put("MajorDirection", "");
        r.put("RecruitPlanExplain", nvl((String) m.get("plan_explain")));
        r.put("MajorGrade", blankToNull(m.get("major_grade")));
        r.put("MajorRank", m.get("major_rank"));
        List<String> req = splitComma((String) m.get("required_subjects"));
        r.put("RequiredSubjects", req);
        r.put("ElectiveSubjects", List.of());
        r.put("MajorCodes", splitComma((String) m.get("major_codes")));
        r.put("UnvFirstTimeAdmission", toInt(m.get("first_time_admission")) == 1);
        r.put("MajorAttendtionType", nvl((String) m.get("attention_type")).isEmpty() ? "No" : m.get("attention_type"));
        Integer preAvg = toIntOrNull(m.get("pre_avg_mark"));
        Integer preMin = toIntOrNull(m.get("pre_min_mark"));
        r.put("PreAvgMark", preAvg);
        r.put("PreMinMark", preMin);
        r.put("PreAvgMarkRank", null);
        r.put("PreMinMarkRank", m.get("pre_min_rank"));
        r.put("PreAvgPercentile", null);
        r.put("PreMinPercentile", toDouble(m.get("pre_min_percentile")));
        double prob = probability(userScore, preAvg, preMin);
        r.put("RecruitProbability", prob);
        r.put("Probability", prob == UNKNOWN_PROBABILITY ? "未知"
                : BigDecimal.valueOf(prob).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%");

        Map<Integer, Map<String, Object>> byYear = hist.stream()
                .collect(Collectors.toMap(h -> toInt(h.get("year")), Function.identity(), (x, y) -> x));
        List<Map<String, Object>> histories = new ArrayList<>(3);
        for (int y = year - 1; y >= year - 3; y--) {
            Map<String, Object> h = byYear.get(y);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("RecruitMajorID", h == null ? null : h.get("hist_recruit_major_id"));
            e.put("Year", y);
            e.put("EquivalentYear", h == null ? year : h.get("equivalent_year"));
            e.put("Plan", h == null ? null : h.get("plan"));
            e.put("AvgMark", h == null ? null : h.get("avg_mark"));
            e.put("MinMark", h == null ? null : h.get("min_mark"));
            e.put("EquivalentAvgMark", h == null ? null : h.get("equivalent_avg_mark"));
            e.put("EquivalentMinMark", h == null ? null : h.get("equivalent_min_mark"));
            e.put("AvgMarkRank", h == null ? null : h.get("avg_mark_rank"));
            e.put("MinMarkRank", h == null ? null : h.get("min_mark_rank"));
            e.put("AvgMarkPercentile", h == null ? null : toDouble(h.get("avg_mark_percentile")));
            e.put("MinMarkPercentile", h == null ? null : toDouble(h.get("min_mark_percentile")));
            histories.add(e);
        }
        r.put("MajorHistorys", histories);
        return r;
    }

    /** 参考系统 CalculateProbability: 两个预测分都缺=9999(未知), 缺一个用另一个顶 */
    private double probability(int userMark, Integer preAvg, Integer preMin) {
        if (preAvg == null && preMin == null) return UNKNOWN_PROBABILITY;
        double avg = preAvg == null ? preMin : preAvg;
        double min = preMin == null ? preAvg : preMin;
        double p;
        if (userMark > avg + UPPER_BOUND) {
            p = 99.9;
        } else if (userMark > avg) {
            p = AVG_MARK_PROBABILITY + (userMark - avg) * (99.9 - AVG_MARK_PROBABILITY) / UPPER_BOUND;
        } else if (userMark > min) {
            p = MIN_MARK_PROBABILITY + (userMark - min) * (AVG_MARK_PROBABILITY - MIN_MARK_PROBABILITY) / (avg - min);
        } else if (userMark > min - LOWER_BOUND) {
            p = 0.1 + (userMark - (min - LOWER_BOUND)) * (MIN_MARK_PROBABILITY - 0.1) / LOWER_BOUND;
        } else {
            p = 0.1;
        }
        return BigDecimal.valueOf(p).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 院校级历年汇总: 上年用数据源自带院校级分数, 其余年份由各专业汇总(最低分取最小, 平均分按计划加权) */
    private List<Map<String, Object>> unvHistoryRows(List<Map<String, Object>> majors, List<Map<String, Object>> hist, int year) {
        Map<String, Object> m0 = majors.get(0);
        String province = (String) m0.get("province");
        String st = (String) m0.get("subject_type");
        String batch = (String) m0.get("batch");
        Map<Integer, List<Map<String, Object>>> byYear = hist.stream()
                .collect(Collectors.groupingBy(h -> toInt(h.get("year"))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int y = year - 1; y >= year - 4; y--) {
            List<Map<String, Object>> hs = byYear.getOrDefault(y, List.of());
            Integer plan = hs.stream().map(h -> toIntOrNull(h.get("plan"))).filter(Objects::nonNull)
                    .reduce(Integer::sum).orElse(null);
            Integer minMark = hs.stream().map(h -> toIntOrNull(h.get("min_mark"))).filter(Objects::nonNull)
                    .min(Integer::compare).orElse(null);
            Integer avgMark = weightedAvg(hs.stream()
                    .map(h -> new Integer[]{toIntOrNull(h.get("avg_mark")), toIntOrNull(h.get("plan"))}).toList());
            Integer minRank = null;
            Integer avgRank = null;
            if (y == year - 1) {
                Integer lm = toIntOrNull(m0.get("unv_min_mark_last"));
                Integer la = toIntOrNull(m0.get("unv_avg_mark_last"));
                if (lm != null) {
                    minMark = lm;
                    minRank = toIntOrNull(m0.get("unv_min_rank_last"));
                }
                if (la != null) {
                    avgMark = la;
                    avgRank = toIntOrNull(m0.get("unv_avg_rank_last"));
                }
            }
            if (plan == null && minMark == null && avgMark == null) continue;
            if (minRank == null) minRank = rankOf(province, y, st, minMark);
            if (avgRank == null) avgRank = rankOf(province, y, st, avgMark);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("Year", y);
            r.put("Batch", batch);
            r.put("ArtsOrScience", artsOrScience(st));
            r.put("GroupCode", "-");
            r.put("UnvName", m0.get("unv_name"));
            r.put("FirstSubject", firstSubjectOf(st));
            r.put("SecondSubjects", "-");
            r.put("Plan", plan);
            r.put("AvgMark", avgMark);
            r.put("MinMark", minMark);
            r.put("AvgMarkPercentile", percentileOf(province, y, st, batch, avgRank));
            r.put("MinMarkPercentile", percentileOf(province, y, st, batch, minRank));
            r.put("AvgMarkRank", avgRank);
            r.put("MinMarkRank", minRank);
            out.add(r);
        }
        return out;
    }

    private Integer rankOf(String province, int year, String st, Integer mark) {
        if (mark == null) return null;
        Map<String, Object> r = fillDao.scoreRanking(province, year, st, mark);
        return r == null ? null : toIntOrNull(r.get("cumulative"));
    }

    /** 百分位 = (1 - 位次/当年该批次控制线累计) × 100 */
    private Double percentileOf(String province, int year, String st, String batch, Integer rank) {
        if (rank == null) return null;
        Map<String, Object> line = fillDao.controlLine(province, year, st, batch);
        Integer n = line == null ? null : toIntOrNull(line.get("cumulative"));
        if (n == null || n == 0) return null;
        return BigDecimal.valueOf((1 - rank / (double) n) * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private Integer weightedAvg(List<Integer[]> markAndWeight) {
        double sum = 0;
        double w = 0;
        for (Integer[] mw : markAndWeight) {
            if (mw[0] == null) continue;
            double weight = mw[1] == null || mw[1] <= 0 ? 1 : mw[1];
            sum += mw[0] * weight;
            w += weight;
        }
        return w == 0 ? null : (int) Math.round(sum / w);
    }

    private Map<String, Object> dictRow(Map<String, Object> d) {
        String c = (String) d.get("major_code");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", d.get("id"));
        r.put("majorCode", c);
        r.put("majorName", d.get("major_name"));
        r.put("disciplineCode", c.length() >= 4 ? c.substring(0, 4) : c);
        r.put("discipline", d.get("first_discipline"));
        r.put("categoryCode", c.length() >= 2 ? c.substring(0, 2) : c);
        r.put("category", d.get("category"));
        return r;
    }

    /* ======================== 私有: Excel ======================== */

    private byte[] buildWorkbook(Aspiration a, Map<String, Object> h, List<Map<String, Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("志愿单");
            CellStyle title = style(wb, true, HorizontalAlignment.LEFT, 12);
            CellStyle head = style(wb, true, HorizontalAlignment.CENTER, 11);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle center = style(wb, false, HorizontalAlignment.CENTER, 10);
            CellStyle left = style(wb, false, HorizontalAlignment.LEFT, 10);

            Row r0 = sh.createRow(0);
            r0.setHeightInPoints(24);
            cell(r0, 0, a.getStudentName() + "；" + genderText(a.getGender()) + "，" + a.getProvince() + "，" + a.getBatch()
                    + "；分数：" + a.getScore() + ",位次:" + (h.get("rank") == null ? "-" : h.get("rank")), title);
            for (int c = 1; c < 20; c++) cell(r0, c, "", title);
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 19));

            int y = a.getYear();
            Row r1 = sh.createRow(1);
            String[][] groups = {{"0", "10", y + "当年招生数据"}, {"11", "13", "等效最低分数"},
                    {"14", "16", "录取平均分/最低分"}, {"17", "19", "平均分位次/最低分位次"}};
            for (int c = 0; c < 20; c++) cell(r1, c, "", head);
            for (String[] g : groups) {
                int from = Integer.parseInt(g[0]);
                int to = Integer.parseInt(g[1]);
                cell(r1, from, g[2], head);
                sh.addMergedRegion(new CellRangeAddress(1, 1, from, to));
            }
            Row r2 = sh.createRow(2);
            List<String> cols = new ArrayList<>(List.of("序号", "院校代码", "院校", "专业名称", "专业备注", "选科要求",
                    "计划", "学制", "学费", "排名", "等级"));
            for (int k = 0; k < 3; k++) cols.add(String.valueOf(y - 1 - k));
            for (int k = 0; k < 3; k++) cols.add(String.valueOf(y - 1 - k));
            for (int k = 0; k < 3; k++) cols.add(String.valueOf(y - 1 - k));
            for (int c = 0; c < cols.size(); c++) cell(r2, c, cols.get(c), head);

            int rowIdx = 3;
            int serial = 1;
            for (Map<String, Object> m : rows) {
                Row row = sh.createRow(rowIdx++);
                row.setHeightInPoints(32);
                boolean debut = Boolean.TRUE.equals(m.get("UnvFirstTimeAdmission"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> hs = (List<Map<String, Object>>) m.get("MajorHistorys");
                @SuppressWarnings("unchecked")
                List<String> req = (List<String>) m.get("RequiredSubjects");
                cell(row, 0, String.valueOf(serial++), center);
                cell(row, 1, str(m.get("UnvCode")), center);
                cell(row, 2, m.get("UnvName") + "\n（" + nvl(str(m.get("Province"))) + "，" + nvl(str(m.get("City"))) + ")", left);
                cell(row, 3, "【" + m.get("RecruitMajorCode") + "】" + m.get("MajorName"), left);
                cell(row, 4, nvl(str(m.get("RecruitPlanExplain"))), left);
                cell(row, 5, req.isEmpty() ? "不限" : String.join("、", req), center);
                cell(row, 6, dash(m.get("MajorPlan")), center);
                cell(row, 7, dash(m.get("SchoolLength")), center);
                cell(row, 8, dash(m.get("TuitionText")), center);
                cell(row, 9, dash(m.get("UnvRank")), center);
                cell(row, 10, dash(m.get("MajorGrade")), center);
                for (int k = 0; k < 3; k++) {
                    Map<String, Object> e = hs.get(k);
                    cell(row, 11 + k, debut ? "-" : dash(e.get("EquivalentMinMark")), center);
                    cell(row, 14 + k, debut ? "-" : dash(e.get("AvgMark")) + " / " + dash(e.get("MinMark")), center);
                    cell(row, 17 + k, debut ? "-" : dash(e.get("AvgMarkRank")) + " / " + dash(e.get("MinMarkRank")), center);
                }
            }
            int[] widths = {6, 9, 30, 30, 40, 14, 6, 6, 9, 7, 7, 8, 8, 8, 12, 12, 12, 18, 18, 18};
            for (int c = 0; c < widths.length; c++) sh.setColumnWidth(c, widths[c] * 256);
            sh.createFreezePane(0, 3);
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BizException("导出志愿表失败: " + e.getMessage());
        }
    }

    private CellStyle style(Workbook wb, boolean bold, HorizontalAlignment align, int size) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(bold);
        f.setFontHeightInPoints((short) size);
        f.setFontName("宋体");
        s.setFont(f);
        s.setAlignment(align);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private void cell(Row row, int col, String v, CellStyle s) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(s);
    }

    /* ======================== 私有: 小工具 ======================== */

    private String tuitionText(Map<String, Object> m) {
        Integer t = toIntOrNull(m.get("tuition"));
        String txt = nvl((String) m.get("tuition_text"));
        if (t == null) return txt.isEmpty() ? null : txt;
        return t == 0 && !txt.isEmpty() ? txt : String.valueOf(t);
    }

    private String secondRequirement(Map<String, Object> m, String st) {
        String first = firstSubjectOf(st);
        List<String> rest = splitComma((String) m.get("required_subjects")).stream().filter(s -> !s.equals(first)).toList();
        return rest.isEmpty() ? "不限" : String.join("、", rest);
    }

    private String firstSubjectOf(String subjectType) {
        return subjectType == null ? "" : subjectType.replace("类", "");
    }

    private String artsOrScience(String subjectType) {
        return "物理类".equals(subjectType) ? "理科" : "文科";
    }

    private String genderText(Integer g) {
        return g != null && g == 2 ? "女" : (g != null && g == 0 ? "未知" : "男");
    }

    /** 26冀本理0458-07 → 0458 */
    private String unvCodeOf(String rid) {
        int dash = rid.lastIndexOf('-');
        return dash < 4 ? "-" : rid.substring(dash - 4, dash);
    }

    private String majorCodeOf(String rid) {
        int dash = rid.lastIndexOf('-');
        return dash < 0 ? "-" : rid.substring(dash + 1);
    }

    private String normalizeKeywords(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        return String.join(",", splitComma(s.replace('，', ',').replace('、', ',')));
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<String> splitComma(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private List<String> toStringList(Object o) {
        if (o == null) return new ArrayList<>();
        if (!(o instanceof Collection<?> col)) {
            throw new BizException("参数格式不正确, 需要数组");
        }
        return col.stream().map(x -> String.valueOf(x).trim()).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    private String blankToNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String dash(Object o) {
        return o == null || String.valueOf(o).isBlank() ? "-" : String.valueOf(o);
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private int nz(Object o) {
        Integer v = toIntOrNull(o);
        return v == null ? 0 : v;
    }

    private int toInt(Object o) {
        Integer v = toIntOrNull(o);
        return v == null ? 0 : v;
    }

    private Integer toIntOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof Boolean b) return b ? 1 : 0;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BizException("数值参数不合法: " + s);
        }
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return Double.valueOf(String.valueOf(o));
    }
}

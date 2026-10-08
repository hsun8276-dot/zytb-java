package com.hbbxl.aspiration.service.impl;

import com.hbbxl.aspiration.dao.FillDao;
import com.hbbxl.aspiration.service.FillService;
import com.hbbxl.aspiration.service.QueryRecruitService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 综合查询 · 高校录取数据(招生信息 3+1+2), 数据来自 recruit_major */
@Service
@RequiredArgsConstructor
public class QueryRecruitServiceImpl implements QueryRecruitService {

    private final FillDao fillDao;
    private final FillService fillService;

    @Override
    public Map<String, Object> pageUnivs(String province, Integer year, String batch, String groupName,
                                         String unvProvince, String unvCity, String unvName, String majorName,
                                         long page, long size) {
        String p = blankTo(province, "河北");
        int y = year == null || year <= 0 ? 2026 : year;
        String b = blankTo(batch, "本科批");
        String st = toSubjectType(groupName);
        long pg = Math.max(1, page);
        long sz = size <= 0 ? 10 : Math.min(size, 50);
        int total = fillDao.countUnivs(p, y, b, st, unvProvince, unvCity, unvName, majorName);
        int offset = (int) ((pg - 1) * sz);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : fillDao.pageUnivs(p, y, b, st, unvProvince, unvCity, unvName, majorName, offset, (int) sz);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("uabId", r.get("uab_id"));
            out.put("unvCode", r.get("unv_code"));
            out.put("unvName", r.get("unv_name"));
            out.put("unvProvince", r.get("unv_province"));
            out.put("unvCity", r.get("unv_city"));
            out.put("planTotal", toInt(r.get("plan_total")));
            out.put("batch", r.get("batch"));
            out.put("groupName", toGroupName((String) r.get("subject_type")));
            out.put("subjectType", r.get("subject_type"));
            out.put("year", r.get("year"));
            out.put("examProvince", r.get("exam_province"));
            records.add(out);
        }
        Map<String, Object> pageOut = new LinkedHashMap<>();
        pageOut.put("records", records);
        pageOut.put("total", total);
        pageOut.put("size", sz);
        pageOut.put("current", pg);
        pageOut.put("pages", sz == 0 ? 0 : (total + sz - 1) / sz);
        return pageOut;
    }

    @Override
    public List<Map<String, Object>> univMajors(String uabId, String keyword) {
        if (uabId == null || uabId.isBlank()) {
            throw new BizException("院校单元不能为空");
        }
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> majors = fillDao.majorsByUab(uabId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : majors) {
            String name = String.valueOf(m.get("major_name") == null ? "" : m.get("major_name"));
            String code = String.valueOf(m.get("recruit_major_code") == null ? "" : m.get("recruit_major_code"));
            String explain = String.valueOf(m.get("plan_explain") == null ? "" : m.get("plan_explain"));
            if (!kw.isEmpty() && !(name.contains(kw) || code.contains(kw) || explain.contains(kw))) {
                continue;
            }
            String st = (String) m.get("subject_type");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recruitMajorId", m.get("recruit_major_id"));
            row.put("recruitMajorCode", code);
            row.put("majorName", name);
            row.put("schoolLength", m.get("school_length"));
            Object tuitionText = m.get("tuition_text");
            row.put("tuition", tuitionText != null && !String.valueOf(tuitionText).isBlank()
                    ? tuitionText : m.get("tuition"));
            row.put("majorPlan", m.get("major_plan"));
            row.put("requiredSubjects", subjectRequirement(m, st));
            row.put("majorRank", m.get("major_rank"));
            row.put("majorGrade", m.get("major_grade"));
            row.put("planExplain", explain);
            out.add(row);
        }
        return out;
    }

    @Override
    public Map<String, Object> unvDetail(String uabId) {
        return fillService.unvDetailByUab(uabId);
    }

    @Override
    public Map<String, Object> majorDetail(String rid) {
        return fillService.majorDetailByRid(rid);
    }

    private String subjectRequirement(Map<String, Object> m, String st) {
        String first = st == null ? "" : st.replace("类", "");
        String raw = m.get("required_subjects") == null ? "" : String.valueOf(m.get("required_subjects"));
        List<String> rest = new ArrayList<>();
        for (String s : raw.split("[,，、]")) {
            String t = s.trim();
            if (!t.isEmpty() && !t.equals(first)) {
                rest.add(t);
            }
        }
        return rest.isEmpty() ? "不限" : String.join("、", rest);
    }

    private String toSubjectType(String groupName) {
        if (groupName == null || groupName.isBlank()) return "物理类";
        String g = groupName.trim();
        if (g.contains("历史") || "文科".equals(g)) return "历史类";
        return "物理类";
    }

    private String toGroupName(String subjectType) {
        return "历史类".equals(subjectType) ? "历史组" : "物理组";
    }

    private String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

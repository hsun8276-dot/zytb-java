package com.hbbxl.aspiration.service.impl;

import com.hbbxl.aspiration.dao.FillDao;
import com.hbbxl.aspiration.service.QueryControlLineService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 综合查询 · 历年省控线查询(参考 ProvinceMarkLine.aspx) */
@Service
@RequiredArgsConstructor
public class QueryControlLineServiceImpl implements QueryControlLineService {

    private final FillDao fillDao;

    @Override
    public List<String> provinces() {
        List<String> list = fillDao.controlLineProvinces();
        return list == null ? List.of() : list;
    }

    @Override
    public List<Integer> years(String province) {
        String p = requireProvince(province);
        List<Integer> list = fillDao.controlLineYears(p);
        return list == null ? List.of() : list;
    }

    @Override
    public Map<String, Object> page(String province, Integer year, long page, long size) {
        String p = requireProvince(province);
        if (year == null || year <= 0) {
            throw new BizException("请选择年份");
        }
        long pg = Math.max(1, page);
        long sz = size <= 0 ? 20 : Math.min(size, 50);
        int total = fillDao.countControlLines(p, year);
        int offset = (int) ((pg - 1) * sz);
        List<Map<String, Object>> src = total == 0 ? List.of()
                : fillDao.pageControlLines(p, year, offset, (int) sz);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : src) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("province", r.get("province"));
            row.put("year", r.get("year"));
            row.put("batch", r.get("batch"));
            row.put("groupName", toGroupName((String) r.get("subject_type")));
            row.put("score", r.get("score"));
            records.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("size", sz);
        out.put("current", pg);
        out.put("pages", sz == 0 ? 0 : (total + sz - 1) / sz);
        return out;
    }

    private String requireProvince(String province) {
        if (province == null || province.isBlank() || "请选择省...".equals(province.trim())) {
            throw new BizException("请选择省份");
        }
        return province.trim();
    }

    private String toGroupName(String subjectType) {
        return "历史类".equals(subjectType) ? "历史组" : "物理组";
    }
}

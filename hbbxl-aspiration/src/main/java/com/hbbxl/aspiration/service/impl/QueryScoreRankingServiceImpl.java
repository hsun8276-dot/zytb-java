package com.hbbxl.aspiration.service.impl;

import com.hbbxl.aspiration.dao.FillDao;
import com.hbbxl.aspiration.service.QueryScoreRankingService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** 综合查询 · 一分一档表查询(参考 PersonsPerPoint.aspx) */
@Service
@RequiredArgsConstructor
public class QueryScoreRankingServiceImpl implements QueryScoreRankingService {

    private final FillDao fillDao;

    @Override
    public List<String> provinces() {
        List<String> list = fillDao.scoreRankingProvinces();
        return list == null ? List.of() : list;
    }

    @Override
    public List<Integer> years(String province) {
        String p = requireProvince(province);
        List<Integer> list = fillDao.scoreRankingYears(p);
        return list == null ? List.of() : list;
    }

    @Override
    public List<String> groups(String province, Integer year) {
        String p = requireProvince(province);
        int y = requireYear(year);
        List<String> subjects = fillDao.scoreRankingSubjects(p, y);
        List<String> out = new ArrayList<>();
        if (subjects == null) return out;
        for (String st : subjects) {
            out.add(toGroupName(st));
        }
        // 物理组优先展示
        out.sort((a, b) -> {
            if (Objects.equals(a, b)) return 0;
            if ("物理组".equals(a)) return -1;
            if ("物理组".equals(b)) return 1;
            return a.compareTo(b);
        });
        return out;
    }

    @Override
    public Map<String, Object> page(String province, Integer year, String groupName, String tableType,
                                    long page, long size) {
        String p = requireProvince(province);
        int y = requireYear(year);
        String st = toSubjectType(groupName);
        String type = normalizeType(tableType);
        Integer ugLine = undergradLine(p, y, st);
        // 本科/专科依赖省控线切分; 无线则无对应分段数据
        if (("本科".equals(type) || "专科".equals(type)) && ugLine == null) {
            return emptyPage(page, size);
        }

        long pg = Math.max(1, page);
        long sz = size <= 0 ? 20 : Math.min(size, 100);
        int total = fillDao.countScoreRanking(p, y, st, type, ugLine);
        int offset = (int) ((pg - 1) * sz);
        List<Map<String, Object>> src = total == 0 ? List.of()
                : fillDao.pageScoreRanking(p, y, st, type, ugLine, offset, (int) sz);

        List<Map<String, Object>> records = mapRows(src, type);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("size", sz);
        out.put("current", pg);
        out.put("pages", sz == 0 ? 0 : (total + sz - 1) / sz);
        return out;
    }

    @Override
    public ExportFile export(String province, Integer year, String groupName, String tableType) {
        String p = requireProvince(province);
        int y = requireYear(year);
        String st = toSubjectType(groupName);
        String type = normalizeType(tableType);
        Integer ugLine = undergradLine(p, y, st);
        if (("本科".equals(type) || "专科".equals(type)) && ugLine == null) {
            throw new BizException("没有可导出的数据");
        }
        List<Map<String, Object>> rows = mapRows(fillDao.listScoreRankingAll(p, y, st, type, ugLine), type);
        if (rows.isEmpty()) {
            throw new BizException("没有可导出的数据");
        }
        String fileName = p + y + toGroupName(st) + "一分一档表【" + type + "】.xlsx";
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("一分一档");
            CellStyle head = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            head.setFont(hf);
            String[] cols = {"省份", "年份", "一分一档表类型", "文理科/组别", "分数", "人数", "累计人数"};
            Row hr = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(head);
            }
            int rIdx = 1;
            for (Map<String, Object> row : rows) {
                Row r = sheet.createRow(rIdx++);
                r.createCell(0).setCellValue(str(row.get("province")));
                r.createCell(1).setCellValue(toInt(row.get("year")));
                r.createCell(2).setCellValue(str(row.get("tableType")));
                r.createCell(3).setCellValue(str(row.get("groupName")));
                r.createCell(4).setCellValue(toInt(row.get("score")));
                r.createCell(5).setCellValue(toInt(row.get("sameCount")));
                r.createCell(6).setCellValue(toInt(row.get("cumulative")));
            }
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(bos);
            return new ExportFile(fileName, bos.toByteArray());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    private Map<String, Object> emptyPage(long page, long size) {
        long pg = Math.max(1, page);
        long sz = size <= 0 ? 20 : Math.min(size, 100);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", List.of());
        out.put("total", 0);
        out.put("size", sz);
        out.put("current", pg);
        out.put("pages", 0);
        return out;
    }

    private List<Map<String, Object>> mapRows(List<Map<String, Object>> src, String type) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : src) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("province", r.get("province"));
            row.put("year", r.get("year"));
            row.put("tableType", type);
            row.put("groupName", toGroupName((String) r.get("subject_type")));
            row.put("score", r.get("score"));
            row.put("sameCount", r.get("same_count"));
            row.put("cumulative", r.get("cumulative"));
            records.add(row);
        }
        return records;
    }

    private Integer undergradLine(String province, int year, String subjectType) {
        Map<String, Object> line = fillDao.controlLine(province, year, subjectType, "本科批");
        if (line == null || line.get("score") == null) return null;
        return toInt(line.get("score"));
    }

    private String requireProvince(String province) {
        if (province == null || province.isBlank() || "请选择省...".equals(province.trim())) {
            throw new BizException("请选择省份");
        }
        return province.trim();
    }

    private int requireYear(Integer year) {
        if (year == null || year <= 0) {
            throw new BizException("请选择年份");
        }
        return year;
    }

    private String toSubjectType(String groupName) {
        if (groupName == null || groupName.isBlank() || groupName.contains("请选择")) {
            throw new BizException("请选择文理科/组别");
        }
        String g = groupName.trim();
        if (g.contains("历史") || "文科".equals(g)) return "历史类";
        return "物理类";
    }

    private String toGroupName(String subjectType) {
        return "历史类".equals(subjectType) ? "历史组" : "物理组";
    }

    private String normalizeType(String tableType) {
        if (tableType == null || tableType.isBlank()) return "全部";
        String t = tableType.trim();
        if ("本科".equals(t) || "专科".equals(t) || "全部".equals(t)) return t;
        return "全部";
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
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

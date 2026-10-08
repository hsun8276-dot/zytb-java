package com.hbbxl.aspiration.service;

import java.util.List;
import java.util.Map;

/** 综合查询 · 一分一档表 */
public interface QueryScoreRankingService {

    List<String> provinces();

    List<Integer> years(String province);

    List<String> groups(String province, Integer year);

    Map<String, Object> page(String province, Integer year, String groupName, String tableType,
                             long page, long size);

    ExportFile export(String province, Integer year, String groupName, String tableType);

    record ExportFile(String fileName, byte[] content) {}
}

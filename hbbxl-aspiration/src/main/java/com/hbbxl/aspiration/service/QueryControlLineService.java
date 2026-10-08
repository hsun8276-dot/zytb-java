package com.hbbxl.aspiration.service;

import java.util.List;
import java.util.Map;

/** 综合查询 · 历年省控线 */
public interface QueryControlLineService {

    List<String> provinces();

    List<Integer> years(String province);

    Map<String, Object> page(String province, Integer year, long page, long size);
}

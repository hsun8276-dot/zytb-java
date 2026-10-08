package com.hbbxl.aspiration.service;

import java.util.List;
import java.util.Map;

/** 综合查询 · 招生信息(3+1+2) */
public interface QueryRecruitService {

    Map<String, Object> pageUnivs(String province, Integer year, String batch, String groupName,
                                 String unvProvince, String unvCity, String unvName, String majorName,
                                 long page, long size);

    List<Map<String, Object>> univMajors(String uabId, String keyword);

    Map<String, Object> unvDetail(String uabId);

    Map<String, Object> majorDetail(String rid);
}

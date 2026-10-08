package com.hbbxl.college.service;

import java.util.List;
import java.util.Map;

/** 综合查询工具 · 院校/专业查询 */
public interface CollegeQueryService {

    List<String> provinces();

    List<String> cities(String province);

    List<Map<String, Object>> searchColleges(String province, String city, String name);

    Map<String, Object> collegeDetail(Integer code);

    List<Map<String, Object>> collegeMajors(Integer code);

    List<String> majorCategories();

    List<String> majorDisciplines(String category);

    List<Map<String, Object>> searchMajorDict(String category, String discipline, String keyword);

    List<String> searchMajorNames(String keyword);

    List<Map<String, Object>> collegesByMajor(String majorName);
}

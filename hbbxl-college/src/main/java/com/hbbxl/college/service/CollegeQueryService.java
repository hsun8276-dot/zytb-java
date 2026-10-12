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

    /** 专业查询 TransferPage · 层次列表(普本/职本/专科) */
    List<Map<String, Object>> majorLevels();

    /**
     * 专业查询 TransferPage · 左侧树
     * @param level 1普本 2职本 3专科
     * @return [{category, groups:[{name, code}]}] 按国标码排序
     */
    List<Map<String, Object>> majorTree(int level);

    /**
     * 专业查询 TransferPage · 某专业类下的包含专业
     * @param groupCode 专业类国标码，如 0101
     */
    Map<String, Object> majorsByGroup(String groupCode);

    /** 职业查询 · 行业列表 */
    List<String> professionIndustries();

    /** 职业查询 · 某行业下的职业类别 */
    List<String> professionOccTypes(String industry);

    /** 职业查询 · 列表检索 */
    List<Map<String, Object>> searchProfessions(String industry, String occType, String occName);

    /** 职业查询 · 详情 */
    Map<String, Object> professionDetail(Integer occId);

    /** 职业查询 · 匹配专业 */
    List<Map<String, Object>> professionMajors(Integer occId);
}

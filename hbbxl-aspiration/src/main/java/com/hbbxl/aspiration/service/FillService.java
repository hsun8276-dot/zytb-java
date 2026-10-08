package com.hbbxl.aspiration.service;

import java.util.List;
import java.util.Map;

/** 志愿填报子页面(四个Tab + 筛选/明细/排序/一键填报/报告/导出) */
public interface FillService {

    record ExportFile(String fileName, byte[] content) {
    }

    Map<String, Object> header(String code, Long uid, String role);

    /** 智能筛选: 按分数区间+选科+三类筛选物化到 aspiration_recommend, 同时保存滑杆值 */
    Map<String, Object> recommend(String code, Integer upperParam, Integer lowerParam, Long uid, String role);

    /** 推荐列表分页(按推荐顺序), 返回 total/page/size/list */
    Map<String, Object> recommendList(String code, Integer page, Integer size, Long uid, String role);

    List<Map<String, Object>> searchUnv(String code, Map<String, Object> body, Long uid, String role);

    List<Map<String, Object>> searchDebut(String code, Map<String, Object> body, Long uid, String role);

    List<Map<String, Object>> selectedList(String code, Long uid, String role);

    Map<String, Object> addSelected(String code, String rid, Long uid, String role);

    Map<String, Object> removeSelected(String code, String rid, Long uid, String role);

    void removeAllSelected(String code, Long uid, String role);

    void clearHighlight(String code, Long uid, String role);

    void saveOrder(String code, List<String> rids, Long uid, String role);

    /** 系统自动排序并保存: 1=按录取概率 2=按预测最低分 3=按上年录取最低分, 返回新顺序 */
    List<String> autoOrder(String code, Integer method, Long uid, String role);

    Map<String, Object> oneClickInfo(String code, Long uid, String role);

    Map<String, Object> oneClick(String code, Integer count, Long uid, String role);

    Map<String, Object> univFilter(String code, Long uid, String role);

    void saveUnivFilter(String code, Map<String, Object> body, Long uid, String role);

    Map<String, Object> otherFilter(String code, Long uid, String role);

    void saveOtherFilter(String code, Map<String, Object> body, Long uid, String role);

    List<Map<String, Object>> majorFilter(String code, Long uid, String role);

    void addMajorFilter(String code, List<Long> majorIds, Long uid, String role);

    void removeMajorFilter(String code, List<Long> majorIds, Long uid, String role);

    void clearMajorFilter(String code, Long uid, String role);

    Map<String, Object> majorDictOptions();

    Map<String, Object> majorCandidates(String category, String discipline, String name);

    Map<String, Object> unvDetail(String code, String uabId, Long uid, String role);

    /** 院校明细(不绑志愿方案, 供综合查询复用) */
    Map<String, Object> unvDetailByUab(String uabId);

    Map<String, Object> majorDetail(String code, String rid, Long uid, String role);

    /** 专业明细(不绑志愿方案, 供综合查询复用) */
    Map<String, Object> majorDetailByRid(String rid);

    List<Map<String, Object>> majorMapping(String code, String rid, Long uid, String role);

    Map<String, Object> report(String code, Long uid, String role);

    ExportFile export(String code, List<String> rids, Long uid, String role);
}

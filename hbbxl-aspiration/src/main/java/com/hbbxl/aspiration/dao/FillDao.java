package com.hbbxl.aspiration.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.*;

/** 志愿填报子页面数据访问: recruit_major / 一分一段 / 控制线 / 推荐物化 / 已选志愿 / 三类筛选 */
@Repository
@RequiredArgsConstructor
public class FillDao {

    /** 列表行需要的 recruit_major 字段 */
    public static final String MAJOR_COLUMNS = "m.recruit_major_id, m.uab_id, m.province, m.year, m.batch, m.subject_type,"
            + " m.unv_code, m.unv_name, m.unv_short_name, m.unv_nature, m.unv_type, m.unv_province, m.unv_city,"
            + " m.unv_rank, m.unv_attributes, m.has_master, m.has_doctor, m.can_recommend, m.vr_url, m.baike_url,"
            + " m.charter_url, m.first_time_admission, m.recruit_major_code, m.major_name, m.recruit_type,"
            + " m.plan_explain, m.major_plan, m.school_length, m.tuition, m.tuition_text, m.required_subjects,"
            + " m.major_codes, m.major_grade, m.major_rank, m.pre_min_mark, m.pre_min_rank, m.pre_min_percentile,"
            + " m.pre_avg_mark, m.attention_type, m.unv_min_mark_last, m.unv_min_rank_last, m.unv_avg_mark_last,"
            + " m.unv_avg_rank_last";

    private final NamedParameterJdbcTemplate jdbc;

    /* ---------------- 一分一段 / 控制线 ---------------- */

    /** 分数所在档(同分人数+累计位次); 高于最高档取最高档, 不在表内取上一档 */
    public Map<String, Object> scoreRanking(String province, int year, String subjectType, int score) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("p", province).addValue("y", year).addValue("s", subjectType).addValue("sc", score);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT score, same_count, cumulative FROM score_ranking"
                + " WHERE province=:p AND year=:y AND subject_type=:s AND score>=:sc ORDER BY score LIMIT 1", p);
        if (rows.isEmpty()) {
            rows = jdbc.queryForList("SELECT score, same_count, cumulative FROM score_ranking"
                    + " WHERE province=:p AND year=:y AND subject_type=:s ORDER BY score DESC LIMIT 1", p);
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> rankingTable(String province, int year, String subjectType) {
        return jdbc.queryForList("SELECT score, same_count, cumulative FROM score_ranking"
                        + " WHERE province=:p AND year=:y AND subject_type=:s ORDER BY score DESC",
                new MapSqlParameterSource().addValue("p", province).addValue("y", year).addValue("s", subjectType));
    }

    public Map<String, Object> controlLine(String province, int year, String subjectType, String batch) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT score, cumulative FROM control_line"
                        + " WHERE province=:p AND year=:y AND subject_type=:s AND batch=:b",
                new MapSqlParameterSource().addValue("p", province).addValue("y", year)
                        .addValue("s", subjectType).addValue("b", batch));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 综合查询-省控线: 有数据的省份 */
    public List<String> controlLineProvinces() {
        return jdbc.queryForList("SELECT DISTINCT province FROM control_line ORDER BY province",
                new MapSqlParameterSource(), String.class);
    }

    /** 综合查询-省控线: 某省有数据的年份(降序) */
    public List<Integer> controlLineYears(String province) {
        return jdbc.queryForList("SELECT DISTINCT year FROM control_line WHERE province=:p ORDER BY year DESC",
                new MapSqlParameterSource("p", province), Integer.class);
    }

    /** 综合查询-省控线列表(仅本科批/专科批, 对齐参考站展示) */
    public int countControlLines(String province, Integer year) {
        MapSqlParameterSource p = controlLineListParams(province, year);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM control_line WHERE " + controlLineListWhere(year), p,
                Integer.class);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> pageControlLines(String province, Integer year, int offset, int limit) {
        MapSqlParameterSource p = controlLineListParams(province, year);
        p.addValue("o", offset).addValue("l", limit);
        return jdbc.queryForList("SELECT province, year, batch, subject_type, score FROM control_line WHERE "
                + controlLineListWhere(year)
                + " ORDER BY year DESC,"
                + " CASE batch WHEN '本科批' THEN 1 WHEN '专科批' THEN 2 ELSE 3 END,"
                + " CASE subject_type WHEN '物理类' THEN 1 WHEN '历史类' THEN 2 ELSE 3 END"
                + " LIMIT :o, :l", p);
    }

    private static String controlLineListWhere(Integer year) {
        String w = "province=:p AND batch IN ('本科批','专科批')";
        if (year != null && year > 0) {
            w += " AND year=:y";
        }
        return w;
    }

    private static MapSqlParameterSource controlLineListParams(String province, Integer year) {
        MapSqlParameterSource p = new MapSqlParameterSource("p", province);
        if (year != null && year > 0) {
            p.addValue("y", year);
        }
        return p;
    }

    /* ---------------- 招生专业 ---------------- */

    public List<Map<String, Object>> queryMajors(String whereAndOrder, SqlParameterSource p) {
        return jdbc.queryForList("SELECT " + MAJOR_COLUMNS + " FROM recruit_major m WHERE " + whereAndOrder, p);
    }

    public List<Map<String, Object>> majorsByRids(Collection<String> rids) {
        if (rids.isEmpty()) return new ArrayList<>();
        return jdbc.queryForList("SELECT " + MAJOR_COLUMNS + " FROM recruit_major m WHERE m.recruit_major_id IN (:ids)",
                new MapSqlParameterSource("ids", rids));
    }

    public List<Map<String, Object>> majorsByUab(String uabId) {
        return jdbc.queryForList("SELECT " + MAJOR_COLUMNS + " FROM recruit_major m WHERE m.uab_id=:u"
                + " ORDER BY m.recruit_major_code", new MapSqlParameterSource("u", uabId));
    }

    /** 综合查询-招生信息: 院校分页(按 uab 聚合计划人数) */
    public int countUnivs(String province, int year, String batch, String subjectType,
                          String unvProvince, String unvCity, String unvName, String majorName) {
        MapSqlParameterSource p = univFilterParams(province, year, batch, subjectType,
                unvProvince, unvCity, unvName, majorName);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ("
                + " SELECT m.uab_id FROM recruit_major m WHERE " + univFilterWhere(unvProvince, unvCity, unvName, majorName)
                + " GROUP BY m.uab_id) t", p, Integer.class);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> pageUnivs(String province, int year, String batch, String subjectType,
                                               String unvProvince, String unvCity, String unvName, String majorName,
                                               int offset, int limit) {
        MapSqlParameterSource p = univFilterParams(province, year, batch, subjectType,
                unvProvince, unvCity, unvName, majorName);
        p.addValue("o", offset).addValue("l", limit);
        return jdbc.queryForList("SELECT m.uab_id, m.unv_code, m.unv_name, m.unv_province, m.unv_city,"
                + " m.batch, m.subject_type, m.province AS exam_province, m.year,"
                + " COALESCE(SUM(m.major_plan),0) AS plan_total"
                + " FROM recruit_major m WHERE " + univFilterWhere(unvProvince, unvCity, unvName, majorName)
                + " GROUP BY m.uab_id, m.unv_code, m.unv_name, m.unv_province, m.unv_city,"
                + " m.batch, m.subject_type, m.province, m.year"
                + " ORDER BY m.unv_code LIMIT :o, :l", p);
    }

    private static String univFilterWhere(String unvProvince, String unvCity, String unvName, String majorName) {
        StringBuilder w = new StringBuilder(
                "m.province=:p AND m.year=:y AND m.batch=:b AND m.subject_type=:s");
        if (unvProvince != null && !unvProvince.isBlank()) {
            w.append(" AND m.unv_province LIKE :up");
        }
        if (unvCity != null && !unvCity.isBlank()) {
            w.append(" AND m.unv_city LIKE :uc");
        }
        if (unvName != null && !unvName.isBlank()) {
            w.append(" AND m.unv_name LIKE :un");
        }
        if (majorName != null && !majorName.isBlank()) {
            w.append(" AND EXISTS (SELECT 1 FROM recruit_major x WHERE x.uab_id=m.uab_id"
                    + " AND x.province=:p AND x.year=:y AND x.batch=:b AND x.subject_type=:s"
                    + " AND x.major_name LIKE :mn)");
        }
        return w.toString();
    }

    private static MapSqlParameterSource univFilterParams(String province, int year, String batch, String subjectType,
                                                          String unvProvince, String unvCity, String unvName,
                                                          String majorName) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("p", province).addValue("y", year).addValue("b", batch).addValue("s", subjectType);
        if (unvProvince != null && !unvProvince.isBlank()) {
            p.addValue("up", "%" + unvProvince.trim() + "%");
        }
        if (unvCity != null && !unvCity.isBlank()) {
            p.addValue("uc", "%" + unvCity.trim() + "%");
        }
        if (unvName != null && !unvName.isBlank()) {
            p.addValue("un", "%" + unvName.trim() + "%");
        }
        if (majorName != null && !majorName.isBlank()) {
            p.addValue("mn", "%" + majorName.trim() + "%");
        }
        return p;
    }

    public List<Map<String, Object>> historiesByRids(Collection<String> rids) {
        if (rids.isEmpty()) return new ArrayList<>();
        return jdbc.queryForList("SELECT recruit_major_id, year, equivalent_year, hist_recruit_major_id, plan,"
                        + " min_mark, min_mark_rank, min_mark_percentile, equivalent_min_mark, avg_mark, avg_mark_rank,"
                        + " avg_mark_percentile, equivalent_avg_mark FROM recruit_major_history"
                        + " WHERE recruit_major_id IN (:ids) ORDER BY year DESC",
                new MapSqlParameterSource("ids", rids));
    }

    /** 院校(批次+科类)下所有专业的历年数据, 用于院校级汇总 */
    public List<Map<String, Object>> historiesByUab(String uabId) {
        return jdbc.queryForList("SELECT h.recruit_major_id, h.year, h.plan, h.min_mark, h.avg_mark"
                + " FROM recruit_major_history h JOIN recruit_major m ON m.recruit_major_id=h.recruit_major_id"
                + " WHERE m.uab_id=:u", new MapSqlParameterSource("u", uabId));
    }

    /* ---------------- 推荐物化 ---------------- */

    public void deleteRecommend(String code) {
        jdbc.update("DELETE FROM aspiration_recommend WHERE aspiration_code=:c", new MapSqlParameterSource("c", code));
    }

    /** 库内一次物化(避免逐行往返), 返回写入条数; p 里不能占用参数名 recommendCode */
    public int insertRecommend(String code, String where, String orderBy, MapSqlParameterSource p) {
        p.addValue("recommendCode", code);
        return jdbc.update("INSERT INTO aspiration_recommend (aspiration_code, recruit_major_id, sort_no)"
                + " SELECT :recommendCode, m.recruit_major_id, ROW_NUMBER() OVER (ORDER BY " + orderBy + ")"
                + " FROM recruit_major m WHERE " + where, p);
    }

    public List<String> recommendPage(String code, int offset, int limit) {
        return jdbc.queryForList("SELECT recruit_major_id FROM aspiration_recommend WHERE aspiration_code=:c"
                        + " ORDER BY sort_no LIMIT :o, :l",
                new MapSqlParameterSource().addValue("c", code).addValue("o", offset).addValue("l", limit), String.class);
    }

    /** 推荐顺序里尚未加入志愿表的前 n 个 */
    public List<String> recommendNotSelected(String code, int limit) {
        return jdbc.queryForList("SELECT r.recruit_major_id FROM aspiration_recommend r"
                        + " LEFT JOIN aspiration_major s ON s.aspiration_code=r.aspiration_code"
                        + " AND s.recruit_major_id=r.recruit_major_id"
                        + " WHERE r.aspiration_code=:c AND s.id IS NULL ORDER BY r.sort_no LIMIT :l",
                new MapSqlParameterSource().addValue("c", code).addValue("l", limit), String.class);
    }

    public int countRecommend(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM aspiration_recommend WHERE aspiration_code=:c",
                new MapSqlParameterSource("c", code), Integer.class);
        return n == null ? 0 : n;
    }

    /* ---------------- 已选志愿 ---------------- */

    public List<Map<String, Object>> selected(String code) {
        return jdbc.queryForList("SELECT recruit_major_id, sort_no, is_new FROM aspiration_major"
                + " WHERE aspiration_code=:c ORDER BY sort_no, id", new MapSqlParameterSource("c", code));
    }

    public int countSelected(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM aspiration_major WHERE aspiration_code=:c",
                new MapSqlParameterSource("c", code), Integer.class);
        return n == null ? 0 : n;
    }

    public int maxSelectedSort(String code) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(MAX(sort_no),0) FROM aspiration_major WHERE aspiration_code=:c",
                new MapSqlParameterSource("c", code), Integer.class);
        return n == null ? 0 : n;
    }

    public void insertSelected(String code, List<String> rids, int startSort) {
        if (rids.isEmpty()) return;
        MapSqlParameterSource[] batch = new MapSqlParameterSource[rids.size()];
        for (int i = 0; i < rids.size(); i++) {
            batch[i] = new MapSqlParameterSource().addValue("c", code).addValue("r", rids.get(i))
                    .addValue("s", startSort + i);
        }
        jdbc.batchUpdate("INSERT IGNORE INTO aspiration_major (aspiration_code, recruit_major_id, sort_no, is_new)"
                + " VALUES (:c, :r, :s, 1)", batch);
    }

    public int deleteSelected(String code, String rid) {
        return jdbc.update("DELETE FROM aspiration_major WHERE aspiration_code=:c AND recruit_major_id=:r",
                new MapSqlParameterSource().addValue("c", code).addValue("r", rid));
    }

    public int deleteAllSelected(String code) {
        return jdbc.update("DELETE FROM aspiration_major WHERE aspiration_code=:c", new MapSqlParameterSource("c", code));
    }

    public int clearNew(String code) {
        return jdbc.update("UPDATE aspiration_major SET is_new=0 WHERE aspiration_code=:c AND is_new=1",
                new MapSqlParameterSource("c", code));
    }

    public void updateSelectedSort(String code, List<String> ridsInOrder) {
        if (ridsInOrder.isEmpty()) return;
        MapSqlParameterSource[] batch = new MapSqlParameterSource[ridsInOrder.size()];
        for (int i = 0; i < ridsInOrder.size(); i++) {
            batch[i] = new MapSqlParameterSource().addValue("c", code).addValue("r", ridsInOrder.get(i))
                    .addValue("s", i + 1);
        }
        jdbc.batchUpdate("UPDATE aspiration_major SET sort_no=:s WHERE aspiration_code=:c AND recruit_major_id=:r", batch);
    }

    /* ---------------- 院校筛选(存排除值) ---------------- */

    public List<Map<String, Object>> univFilter(String code) {
        return jdbc.queryForList("SELECT filter_group, filter_value FROM aspiration_univ_filter WHERE aspiration_code=:c"
                + " ORDER BY id", new MapSqlParameterSource("c", code));
    }

    public void replaceUnivFilter(String code, Map<String, List<String>> excluded) {
        jdbc.update("DELETE FROM aspiration_univ_filter WHERE aspiration_code=:c", new MapSqlParameterSource("c", code));
        List<MapSqlParameterSource> batch = new ArrayList<>();
        excluded.forEach((g, vs) -> vs.forEach(v -> batch.add(new MapSqlParameterSource()
                .addValue("c", code).addValue("g", g).addValue("v", v))));
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO aspiration_univ_filter (aspiration_code, filter_group, filter_value)"
                    + " VALUES (:c, :g, :v)", batch.toArray(new MapSqlParameterSource[0]));
        }
    }

    /* ---------------- 其它筛选 ---------------- */

    public Map<String, Object> otherFilter(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT school_length, tuition_min, tuition_max,"
                + " include_unknown_tuition, preset_include, preset_exclude, custom_include, custom_exclude"
                + " FROM aspiration_other_filter WHERE aspiration_code=:c", new MapSqlParameterSource("c", code));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveOtherFilter(String code, Integer schoolLength, Integer tuitionMin, Integer tuitionMax,
                                boolean includeUnknown, String presetInclude, String presetExclude,
                                String customInclude, String customExclude) {
        jdbc.update("INSERT INTO aspiration_other_filter (aspiration_code, school_length, tuition_min, tuition_max,"
                        + " include_unknown_tuition, preset_include, preset_exclude, custom_include, custom_exclude)"
                        + " VALUES (:c, :sl, :tmin, :tmax, :iu, :pi, :pe, :ci, :ce)"
                        + " ON DUPLICATE KEY UPDATE school_length=VALUES(school_length), tuition_min=VALUES(tuition_min),"
                        + " tuition_max=VALUES(tuition_max), include_unknown_tuition=VALUES(include_unknown_tuition),"
                        + " preset_include=VALUES(preset_include), preset_exclude=VALUES(preset_exclude),"
                        + " custom_include=VALUES(custom_include), custom_exclude=VALUES(custom_exclude)",
                new MapSqlParameterSource().addValue("c", code).addValue("sl", schoolLength)
                        .addValue("tmin", tuitionMin).addValue("tmax", tuitionMax).addValue("iu", includeUnknown ? 1 : 0)
                        .addValue("pi", presetInclude).addValue("pe", presetExclude)
                        .addValue("ci", customInclude).addValue("ce", customExclude));
    }

    /* ---------------- 专业筛选(白名单) + 专业字典 ---------------- */

    public List<Map<String, Object>> majorFilter(String code) {
        return jdbc.queryForList("SELECT d.id, d.major_code, d.major_name, d.first_discipline, d.category"
                + " FROM aspiration_major_filter f JOIN major d ON d.id=f.major_id"
                + " WHERE f.aspiration_code=:c ORDER BY d.major_code", new MapSqlParameterSource("c", code));
    }

    public List<String> majorFilterCodes(String code) {
        return jdbc.queryForList("SELECT d.major_code FROM aspiration_major_filter f JOIN major d ON d.id=f.major_id"
                + " WHERE f.aspiration_code=:c", new MapSqlParameterSource("c", code), String.class);
    }

    public void addMajorFilter(String code, Collection<Long> majorIds) {
        if (majorIds.isEmpty()) return;
        MapSqlParameterSource[] batch = majorIds.stream()
                .map(id -> new MapSqlParameterSource().addValue("c", code).addValue("m", id))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT IGNORE INTO aspiration_major_filter (aspiration_code, major_id) VALUES (:c, :m)", batch);
    }

    public int removeMajorFilter(String code, Collection<Long> majorIds) {
        if (majorIds.isEmpty()) return 0;
        return jdbc.update("DELETE FROM aspiration_major_filter WHERE aspiration_code=:c AND major_id IN (:ids)",
                new MapSqlParameterSource().addValue("c", code).addValue("ids", majorIds));
    }

    public int clearMajorFilter(String code) {
        return jdbc.update("DELETE FROM aspiration_major_filter WHERE aspiration_code=:c",
                new MapSqlParameterSource("c", code));
    }

    public List<Map<String, Object>> majorCategories() {
        return jdbc.queryForList("SELECT LEFT(major_code,2) AS code, MIN(category) AS name FROM major"
                + " GROUP BY LEFT(major_code,2) ORDER BY code", new MapSqlParameterSource());
    }

    public List<Map<String, Object>> majorDisciplines() {
        return jdbc.queryForList("SELECT LEFT(major_code,4) AS code, MIN(first_discipline) AS name FROM major"
                + " GROUP BY LEFT(major_code,4) ORDER BY code", new MapSqlParameterSource());
    }

    public List<Map<String, Object>> majorCandidates(String categoryCode, String disciplineCode, String name, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, major_code, major_name, first_discipline, category FROM major WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (categoryCode != null) {
            sql.append(" AND major_code LIKE :cc");
            p.addValue("cc", categoryCode + "%");
        }
        if (disciplineCode != null) {
            sql.append(" AND major_code LIKE :dc");
            p.addValue("dc", disciplineCode + "%");
        }
        if (name != null) {
            sql.append(" AND major_name LIKE :n");
            p.addValue("n", "%" + name + "%");
        }
        sql.append(" ORDER BY major_code LIMIT ").append(limit);
        return jdbc.queryForList(sql.toString(), p);
    }

    public List<Map<String, Object>> majorsByCodes(Collection<String> codes) {
        if (codes.isEmpty()) return new ArrayList<>();
        return jdbc.queryForList("SELECT id, major_code, major_name, first_discipline, category FROM major"
                + " WHERE major_code IN (:codes)", new MapSqlParameterSource("codes", codes));
    }

    public int countMajorIds(Collection<Long> ids) {
        if (ids.isEmpty()) return 0;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM major WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids), Integer.class);
        return n == null ? 0 : n;
    }
}

package com.hbbxl.user.service.impl;

import com.hbbxl.user.service.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端工作台汇总(同库直查) */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    private final JdbcTemplate jdbc;

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();

        Long userTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE deleted=0", Long.class);
        Long fillerTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE u.deleted=0 AND r.code='user'", Long.class);
        Long fillerActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE u.deleted=0 AND r.code='user' AND u.status=1 "
                        + "AND (u.expire_time IS NULL OR u.expire_time>=NOW())", Long.class);
        Long fillerExpired = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE u.deleted=0 AND r.code='user' "
                        + "AND u.expire_time IS NOT NULL AND u.expire_time<NOW()", Long.class);
        Long fillerDisabled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE u.deleted=0 AND r.code='user' AND u.status=0 "
                        + "AND (u.expire_time IS NULL OR u.expire_time>=NOW())", Long.class);

        Long collegeCount = jdbc.queryForObject("SELECT COUNT(*) FROM college", Long.class);
        Long majorDictCount = jdbc.queryForObject("SELECT COUNT(*) FROM major", Long.class);
        Long recruitMajorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruit_major WHERE province='河北' AND year=2026", Long.class);
        Long aspirationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM aspiration WHERE deleted=0", Long.class);
        Long scoreLineYears = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT year) FROM score_line WHERE province='河北'", Long.class);
        List<Integer> years = jdbc.query(
                "SELECT DISTINCT year FROM score_line WHERE province='河北' ORDER BY year",
                (rs, i) -> rs.getInt(1));

        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", n(userTotal));
        users.put("fillerTotal", n(fillerTotal));
        users.put("fillerActive", n(fillerActive));
        users.put("fillerExpired", n(fillerExpired));
        users.put("fillerDisabled", n(fillerDisabled));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("collegeCount", n(collegeCount));
        data.put("majorDictCount", n(majorDictCount));
        data.put("recruitMajorCount", n(recruitMajorCount));
        data.put("aspirationCount", n(aspirationCount));
        data.put("scoreLineYearCount", n(scoreLineYears));
        data.put("scoreLineYears", years);

        out.put("users", users);
        out.put("data", data);
        return out;
    }

    private static long n(Long v) {
        return v == null ? 0L : v;
    }
}

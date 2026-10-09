package com.hbbxl.user.service.impl;

import com.hbbxl.user.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl implements AdminLogService {

    private final JdbcTemplate jdbc;

    @Override
    public Map<String, Object> page(int page, int size, String keyword, Integer status, String module) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (status != null && (status == 0 || status == 1)) {
            where.append(" AND l.status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(module)) {
            where.append(" AND l.module LIKE ? ");
            args.add("%" + module.trim() + "%");
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (u.username LIKE ? OR u.real_name LIKE ? OR l.action LIKE ? OR l.request_params LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }

        String from = " FROM sys_log l LEFT JOIN sys_user u ON u.id=l.user_id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add(offset);
        List<Map<String, Object>> records = jdbc.query(
                "SELECT l.id, l.user_id, u.username, u.real_name, l.module, l.action, l.request_params, "
                        + "l.response_data, l.ip, l.duration, l.status, l.create_time"
                        + from + where
                        + " ORDER BY l.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    long uid = rs.getLong("user_id");
                    row.put("userId", rs.wasNull() ? null : uid);
                    row.put("username", rs.getString("username"));
                    row.put("realName", rs.getString("real_name"));
                    row.put("module", rs.getString("module"));
                    row.put("action", rs.getString("action"));
                    row.put("requestParams", rs.getString("request_params"));
                    row.put("responseData", rs.getString("response_data"));
                    row.put("ip", rs.getString("ip"));
                    int dur = rs.getInt("duration");
                    row.put("duration", rs.wasNull() ? null : dur);
                    row.put("status", rs.getInt("status"));
                    row.put("createTime", rs.getTimestamp("create_time") == null
                            ? null : rs.getTimestamp("create_time").toLocalDateTime().toString().replace('T', ' '));
                    return row;
                },
                pageArgs.toArray());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total == null ? 0L : total);
        out.put("size", s);
        out.put("current", p);
        out.put("pages", total == null || total == 0 ? 0 : (total + s - 1) / s);
        return out;
    }
}

package com.hbbxl.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.auth.entity.SysConfig;
import com.hbbxl.auth.mapper.SysConfigMapper;
import com.hbbxl.auth.service.ConfigService;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final Set<String> ALLOWED = Set.of(
            "system_name", "membership_years", "default_password", "contact_hint");

    private final SysConfigMapper configMapper;

    @Override
    public Map<String, String> publicAll() {
        Map<String, String> out = new LinkedHashMap<>();
        for (SysConfig c : configMapper.selectList(new LambdaQueryWrapper<>())) {
            out.put(c.getConfigKey(), c.getConfigValue() == null ? "" : c.getConfigValue());
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> adminList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysConfig c : configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("configKey", c.getConfigKey());
            row.put("configValue", c.getConfigValue());
            row.put("remark", c.getRemark());
            row.put("updateTime", c.getUpdateTime());
            out.add(row);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            throw new BizException("配置不能为空");
        }
        for (Map.Entry<String, String> e : body.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (!ALLOWED.contains(key)) {
                throw new BizException("不支持的配置项: " + key);
            }
            String val = e.getValue() == null ? "" : e.getValue().trim();
            if ("membership_years".equals(key)) {
                int years;
                try {
                    years = Integer.parseInt(val);
                } catch (NumberFormatException ex) {
                    throw new BizException("开通年数必须是正整数");
                }
                if (years < 1 || years > 10) {
                    throw new BizException("开通年数须在 1~10 之间");
                }
            }
            if ("system_name".equals(key) && val.isEmpty()) {
                throw new BizException("系统名称不能为空");
            }
            SysConfig row = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key));
            if (row == null) {
                throw new BizException("配置项不存在: " + key);
            }
            row.setConfigValue(val);
            configMapper.updateById(row);
        }
    }
}

package com.hbbxl.auth.controller;

import com.hbbxl.auth.entity.SysConfig;
import com.hbbxl.auth.mapper.SysConfigMapper;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置控制器
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigMapper sysConfigMapper;

    @Operation(summary = "获取所有系统配置")
    @GetMapping("/all")
    public R<Map<String, String>> getAll() {
        List<SysConfig> list = sysConfigMapper.selectList(null);
        Map<String, String> map = list.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        return R.ok(map);
    }

    @Operation(summary = "获取单个配置值")
    @GetMapping("/{key}")
    public R<String> get(@PathVariable String key) {
        SysConfig config = sysConfigMapper.selectById(key);
        return R.ok(config != null ? config.getConfigValue() : "");
    }

    @Operation(summary = "更新系统配置（批量）")
    @PutMapping("/save")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> save(@RequestBody Map<String, String> configMap) {
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            SysConfig config = sysConfigMapper.selectById(entry.getKey());
            if (config == null) {
                config = new SysConfig();
                config.setConfigKey(entry.getKey());
                config.setConfigValue(entry.getValue());
                sysConfigMapper.insert(config);
            } else {
                config.setConfigValue(entry.getValue());
                sysConfigMapper.updateById(config);
            }
        }
        return R.ok();
    }
}

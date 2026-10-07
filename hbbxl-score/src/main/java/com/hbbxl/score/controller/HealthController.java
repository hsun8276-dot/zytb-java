package com.hbbxl.score.controller;

import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "分数线位次服务")
@RestController
@RequestMapping("/score")
public class HealthController {

    @Operation(summary = "存活探针")
    @GetMapping("/ping")
    public R<Map<String, Object>> ping() {
        return R.ok(Map.of("service", "hbbxl-score", "status", "UP", "desc", "分数线位次"));
    }
}

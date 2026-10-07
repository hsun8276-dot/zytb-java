package com.hbbxl.college.controller;

import com.hbbxl.college.service.CollegeStatsService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 院校库统计(数据在 CollegeStatsServiceImpl) */
@Tag(name = "院校统计")
@RestController
@RequestMapping("/college")
@RequiredArgsConstructor
public class CollegeStatsController {

    private final CollegeStatsService collegeStatsService;

    @Operation(summary = "仪表板统计")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(collegeStatsService.dashboardStats());
    }
}

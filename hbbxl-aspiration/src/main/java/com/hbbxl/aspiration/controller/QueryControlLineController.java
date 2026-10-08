package com.hbbxl.aspiration.controller;

import com.hbbxl.aspiration.service.QueryControlLineService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 综合查询 · 历年省控线查询 */
@Tag(name = "综合查询-省控线")
@RestController
@RequestMapping("/aspiration/query/control-line")
@RequiredArgsConstructor
public class QueryControlLineController {

    private final QueryControlLineService queryControlLineService;

    @Operation(summary = "有省控线数据的省份")
    @GetMapping("/provinces")
    public R<List<String>> provinces() {
        return R.ok(queryControlLineService.provinces());
    }

    @Operation(summary = "某省有数据的年份")
    @GetMapping("/years")
    public R<List<Integer>> years(@RequestParam String province) {
        return R.ok(queryControlLineService.years(province));
    }

    @Operation(summary = "省控线列表")
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam String province,
                                       @RequestParam Integer year,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return R.ok(queryControlLineService.page(province, year, page, size));
    }
}

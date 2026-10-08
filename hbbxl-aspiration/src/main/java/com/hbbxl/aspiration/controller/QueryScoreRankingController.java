package com.hbbxl.aspiration.controller;

import com.hbbxl.aspiration.service.QueryScoreRankingService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 综合查询 · 一分一档表查询 */
@Tag(name = "综合查询-一分一档")
@RestController
@RequestMapping("/aspiration/query/score-ranking")
@RequiredArgsConstructor
public class QueryScoreRankingController {

    private final QueryScoreRankingService queryScoreRankingService;

    @Operation(summary = "有一分一档数据的省份")
    @GetMapping("/provinces")
    public R<List<String>> provinces() {
        return R.ok(queryScoreRankingService.provinces());
    }

    @Operation(summary = "某省有数据的年份")
    @GetMapping("/years")
    public R<List<Integer>> years(@RequestParam String province) {
        return R.ok(queryScoreRankingService.years(province));
    }

    @Operation(summary = "某省某年的文理科/组别")
    @GetMapping("/groups")
    public R<List<String>> groups(@RequestParam String province, @RequestParam Integer year) {
        return R.ok(queryScoreRankingService.groups(province, year));
    }

    @Operation(summary = "一分一档列表")
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam String province,
                                       @RequestParam Integer year,
                                       @RequestParam String groupName,
                                       @RequestParam(defaultValue = "全部") String tableType,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return R.ok(queryScoreRankingService.page(province, year, groupName, tableType, page, size));
    }

    @Operation(summary = "导出一分一档 Excel")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String province,
                                         @RequestParam Integer year,
                                         @RequestParam String groupName,
                                         @RequestParam(defaultValue = "全部") String tableType) {
        QueryScoreRankingService.ExportFile file = queryScoreRankingService.export(province, year, groupName, tableType);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }
}

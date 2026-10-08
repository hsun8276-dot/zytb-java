package com.hbbxl.aspiration.controller;

import com.hbbxl.aspiration.service.QueryRecruitService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 综合查询 · 招生信息(3+1+2) / 高校录取数据查询 */
@Tag(name = "综合查询-招生信息")
@RestController
@RequestMapping("/aspiration/query/recruit")
@RequiredArgsConstructor
public class QueryRecruitController {

    private final QueryRecruitService queryRecruitService;

    @Operation(summary = "院校分页列表(招生信息)")
    @GetMapping("/univs")
    public R<Map<String, Object>> univs(@RequestParam(defaultValue = "河北") String province,
                                        @RequestParam(defaultValue = "2026") Integer year,
                                        @RequestParam(defaultValue = "本科批") String batch,
                                        @RequestParam(defaultValue = "物理组") String groupName,
                                        @RequestParam(required = false) String unvProvince,
                                        @RequestParam(required = false) String unvCity,
                                        @RequestParam(required = false) String unvName,
                                        @RequestParam(required = false) String majorName,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return R.ok(queryRecruitService.pageUnivs(province, year, batch, groupName,
                unvProvince, unvCity, unvName, majorName, page, size));
    }

    @Operation(summary = "院校下专业列表(展开行)")
    @GetMapping("/univs/{uabId}/majors")
    public R<List<Map<String, Object>>> majors(@PathVariable String uabId,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(queryRecruitService.univMajors(uabId, keyword));
    }

    @Operation(summary = "院校明细")
    @GetMapping("/unv-detail")
    public R<Map<String, Object>> unvDetail(@RequestParam String uabId) {
        return R.ok(queryRecruitService.unvDetail(uabId));
    }

    @Operation(summary = "专业明细")
    @GetMapping("/major-detail")
    public R<Map<String, Object>> majorDetail(@RequestParam String rid) {
        return R.ok(queryRecruitService.majorDetail(rid));
    }
}

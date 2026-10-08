package com.hbbxl.college.controller;

import com.hbbxl.college.service.CollegeQueryService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 综合查询工具 · 院校查询 / 专业查询 / 专业搜索 */
@Tag(name = "综合查询工具")
@RestController
@RequestMapping("/college")
@RequiredArgsConstructor
public class CollegeQueryController {

    private final CollegeQueryService collegeQueryService;

    @Operation(summary = "省份列表")
    @GetMapping("/search/provinces")
    public R<List<String>> provinces() {
        return R.ok(collegeQueryService.provinces());
    }

    @Operation(summary = "城市列表(按省)")
    @GetMapping("/search/cities")
    public R<List<String>> cities(@RequestParam String province) {
        return R.ok(collegeQueryService.cities(province));
    }

    @Operation(summary = "院校搜索")
    @GetMapping("/search/colleges")
    public R<List<Map<String, Object>>> colleges(@RequestParam(required = false) String province,
                                                 @RequestParam(required = false) String city,
                                                 @RequestParam(required = false) String name) {
        return R.ok(collegeQueryService.searchColleges(province, city, name));
    }

    @Operation(summary = "院校详情")
    @GetMapping("/detail/{code}")
    public R<Map<String, Object>> detail(@PathVariable Integer code) {
        return R.ok(collegeQueryService.collegeDetail(code));
    }

    @Operation(summary = "院校开设专业(按门类分组)")
    @GetMapping("/detail/{code}/majors")
    public R<List<Map<String, Object>>> detailMajors(@PathVariable Integer code) {
        return R.ok(collegeQueryService.collegeMajors(code));
    }

    @Operation(summary = "专业目录 · 学科门类")
    @GetMapping("/majors/categories")
    public R<List<String>> majorCategories() {
        return R.ok(collegeQueryService.majorCategories());
    }

    @Operation(summary = "专业目录 · 一级学科类")
    @GetMapping("/majors/disciplines")
    public R<List<String>> majorDisciplines(@RequestParam String category) {
        return R.ok(collegeQueryService.majorDisciplines(category));
    }

    @Operation(summary = "专业目录 · 检索")
    @GetMapping("/majors/dict")
    public R<List<Map<String, Object>>> majorDict(@RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String discipline,
                                                  @RequestParam(required = false) String keyword) {
        return R.ok(collegeQueryService.searchMajorDict(category, discipline, keyword));
    }

    @Operation(summary = "专业搜索 · 专业名联想")
    @GetMapping("/majors/names")
    public R<List<String>> majorNames(@RequestParam String keyword) {
        return R.ok(collegeQueryService.searchMajorNames(keyword));
    }

    @Operation(summary = "专业搜索 · 开设该专业的院校")
    @GetMapping("/majors/colleges")
    public R<List<Map<String, Object>>> collegesByMajor(@RequestParam String majorName) {
        return R.ok(collegeQueryService.collegesByMajor(majorName));
    }
}

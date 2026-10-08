package com.hbbxl.aspiration.controller;

import com.hbbxl.aspiration.service.FillService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 志愿填报子页面: 业务逻辑全部在 FillServiceImpl */
@Tag(name = "志愿填报子页面")
@RestController
@RequestMapping("/aspiration")
@RequiredArgsConstructor
public class FillController {

    private static final String UID = "X-User-Id";
    private static final String ROLE = "X-User-Role";

    private final FillService fillService;

    @Operation(summary = "顶部学生摘要(位次/同分人数/已选数/滑杆/概率参数/上限)")
    @GetMapping("/fill/{code}/header")
    public R<Map<String, Object>> header(@PathVariable String code, @RequestHeader(UID) Long uid,
                                         @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.header(code, uid, role));
    }

    @Operation(summary = "智能筛选: 物化推荐结果并保存滑杆值, 返回条数")
    @PostMapping("/fill/{code}/recommend")
    public R<Map<String, Object>> recommend(@PathVariable String code, @RequestBody Map<String, Integer> body,
                                            @RequestHeader(UID) Long uid,
                                            @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.recommend(code, body.get("upperParam"), body.get("lowerParam"), uid, role));
    }

    @Operation(summary = "智能专业推荐列表(读物化结果, 分页)")
    @GetMapping("/fill/{code}/recommend")
    public R<Map<String, Object>> recommendList(@PathVariable String code,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestHeader(UID) Long uid,
                                                @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.recommendList(code, page, size, uid, role));
    }

    @Operation(summary = "按高校检索(最多200条)")
    @PostMapping("/fill/{code}/search/unv")
    public R<List<Map<String, Object>>> searchUnv(@PathVariable String code, @RequestBody Map<String, Object> body,
                                                  @RequestHeader(UID) Long uid,
                                                  @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.searchUnv(code, body, uid, role));
    }

    @Operation(summary = "首次招生高校检索(最多300条)")
    @PostMapping("/fill/{code}/search/debut")
    public R<List<Map<String, Object>>> searchDebut(@PathVariable String code, @RequestBody Map<String, Object> body,
                                                    @RequestHeader(UID) Long uid,
                                                    @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.searchDebut(code, body, uid, role));
    }

    @Operation(summary = "已选志愿列表(按志愿顺序)")
    @GetMapping("/fill/{code}/selected")
    public R<List<Map<String, Object>>> selected(@PathVariable String code, @RequestHeader(UID) Long uid,
                                                 @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.selectedList(code, uid, role));
    }

    @Operation(summary = "添加志愿")
    @PostMapping("/fill/{code}/selected")
    public R<Map<String, Object>> add(@PathVariable String code, @RequestBody Map<String, String> body,
                                      @RequestHeader(UID) Long uid,
                                      @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.addSelected(code, body.get("rid"), uid, role));
    }

    @Operation(summary = "移除志愿")
    @PostMapping("/fill/{code}/selected/remove")
    public R<Map<String, Object>> remove(@PathVariable String code, @RequestBody Map<String, String> body,
                                         @RequestHeader(UID) Long uid,
                                         @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.removeSelected(code, body.get("rid"), uid, role));
    }

    @Operation(summary = "全部删除")
    @PostMapping("/fill/{code}/selected/remove-all")
    public R<Void> removeAll(@PathVariable String code, @RequestHeader(UID) Long uid,
                             @RequestHeader(value = ROLE, required = false) String role) {
        fillService.removeAllSelected(code, uid, role);
        return R.ok();
    }

    @Operation(summary = "清除高亮")
    @PostMapping("/fill/{code}/selected/clear-new")
    public R<Void> clearNew(@PathVariable String code, @RequestHeader(UID) Long uid,
                            @RequestHeader(value = ROLE, required = false) String role) {
        fillService.clearHighlight(code, uid, role);
        return R.ok();
    }

    @Operation(summary = "保存志愿排序")
    @PutMapping("/fill/{code}/selected/order")
    public R<Void> order(@PathVariable String code, @RequestBody Map<String, List<String>> body,
                         @RequestHeader(UID) Long uid,
                         @RequestHeader(value = ROLE, required = false) String role) {
        fillService.saveOrder(code, body.get("rids"), uid, role);
        return R.ok();
    }

    @Operation(summary = "系统自动排序(1=录取概率 2=预测最低分 3=上年录取最低分)并保存")
    @PostMapping("/fill/{code}/selected/auto-order")
    public R<List<String>> autoOrder(@PathVariable String code, @RequestBody Map<String, Integer> body,
                                     @RequestHeader(UID) Long uid,
                                     @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.autoOrder(code, body.get("method"), uid, role));
    }

    @Operation(summary = "一键填报: 可选数量上限")
    @GetMapping("/fill/{code}/one-click")
    public R<Map<String, Object>> oneClickInfo(@PathVariable String code, @RequestHeader(UID) Long uid,
                                               @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.oneClickInfo(code, uid, role));
    }

    @Operation(summary = "一键填报: 按推荐顺序加入未选专业")
    @PostMapping("/fill/{code}/one-click")
    public R<Map<String, Object>> oneClick(@PathVariable String code, @RequestBody Map<String, Integer> body,
                                           @RequestHeader(UID) Long uid,
                                           @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.oneClick(code, body.get("count"), uid, role));
    }

    @Operation(summary = "院校筛选(返回选项与排除值)")
    @GetMapping("/fill/{code}/filter/unv")
    public R<Map<String, Object>> univFilter(@PathVariable String code, @RequestHeader(UID) Long uid,
                                             @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.univFilter(code, uid, role));
    }

    @Operation(summary = "保存院校筛选(各组未勾选的值)")
    @PutMapping("/fill/{code}/filter/unv")
    public R<Void> saveUnivFilter(@PathVariable String code, @RequestBody Map<String, Object> body,
                                  @RequestHeader(UID) Long uid,
                                  @RequestHeader(value = ROLE, required = false) String role) {
        fillService.saveUnivFilter(code, body, uid, role);
        return R.ok();
    }

    @Operation(summary = "其它筛选")
    @GetMapping("/fill/{code}/filter/other")
    public R<Map<String, Object>> otherFilter(@PathVariable String code, @RequestHeader(UID) Long uid,
                                              @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.otherFilter(code, uid, role));
    }

    @Operation(summary = "保存其它筛选")
    @PutMapping("/fill/{code}/filter/other")
    public R<Void> saveOtherFilter(@PathVariable String code, @RequestBody Map<String, Object> body,
                                   @RequestHeader(UID) Long uid,
                                   @RequestHeader(value = ROLE, required = false) String role) {
        fillService.saveOtherFilter(code, body, uid, role);
        return R.ok();
    }

    @Operation(summary = "专业筛选白名单")
    @GetMapping("/fill/{code}/filter/major")
    public R<List<Map<String, Object>>> majorFilter(@PathVariable String code, @RequestHeader(UID) Long uid,
                                                    @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.majorFilter(code, uid, role));
    }

    @Operation(summary = "专业筛选: 添加")
    @PostMapping("/fill/{code}/filter/major")
    public R<Void> addMajorFilter(@PathVariable String code, @RequestBody Map<String, List<Long>> body,
                                  @RequestHeader(UID) Long uid,
                                  @RequestHeader(value = ROLE, required = false) String role) {
        fillService.addMajorFilter(code, body.get("majorIds"), uid, role);
        return R.ok();
    }

    @Operation(summary = "专业筛选: 移除选中")
    @PostMapping("/fill/{code}/filter/major/remove")
    public R<Void> removeMajorFilter(@PathVariable String code, @RequestBody Map<String, List<Long>> body,
                                     @RequestHeader(UID) Long uid,
                                     @RequestHeader(value = ROLE, required = false) String role) {
        fillService.removeMajorFilter(code, body.get("majorIds"), uid, role);
        return R.ok();
    }

    @Operation(summary = "专业筛选: 全部移除")
    @PostMapping("/fill/{code}/filter/major/clear")
    public R<Void> clearMajorFilter(@PathVariable String code, @RequestHeader(UID) Long uid,
                                    @RequestHeader(value = ROLE, required = false) String role) {
        fillService.clearMajorFilter(code, uid, role);
        return R.ok();
    }

    @Operation(summary = "专业字典: 门类/一级学科下拉")
    @GetMapping("/fill-dict/options")
    public R<Map<String, Object>> dictOptions() {
        return R.ok(fillService.majorDictOptions());
    }

    @Operation(summary = "专业字典: 候选专业(最多200个)")
    @GetMapping("/fill-dict/majors")
    public R<Map<String, Object>> dictMajors(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) String discipline,
                                             @RequestParam(required = false) String name) {
        return R.ok(fillService.majorCandidates(category, discipline, name));
    }

    @Operation(summary = "院校明细")
    @GetMapping("/fill/{code}/unv-detail")
    public R<Map<String, Object>> unvDetail(@PathVariable String code, @RequestParam String uabId,
                                            @RequestHeader(UID) Long uid,
                                            @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.unvDetail(code, uabId, uid, role));
    }

    @Operation(summary = "专业明细")
    @GetMapping("/fill/{code}/major-detail")
    public R<Map<String, Object>> majorDetail(@PathVariable String code, @RequestParam String rid,
                                              @RequestHeader(UID) Long uid,
                                              @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.majorDetail(code, rid, uid, role));
    }

    @Operation(summary = "专业映射(包含的教育部专业)")
    @GetMapping("/fill/{code}/major-mapping")
    public R<List<Map<String, Object>>> majorMapping(@PathVariable String code, @RequestParam String rid,
                                                     @RequestHeader(UID) Long uid,
                                                     @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.majorMapping(code, rid, uid, role));
    }

    @Operation(summary = "志愿报告数据")
    @GetMapping("/fill/{code}/report")
    public R<Map<String, Object>> report(@PathVariable String code, @RequestHeader(UID) Long uid,
                                         @RequestHeader(value = ROLE, required = false) String role) {
        return R.ok(fillService.report(code, uid, role));
    }

    @Operation(summary = "导出Excel志愿单(rids 为空导出全部)")
    @PostMapping("/fill/{code}/export")
    public ResponseEntity<byte[]> export(@PathVariable String code, @RequestBody(required = false) Map<String, List<String>> body,
                                         @RequestHeader(UID) Long uid,
                                         @RequestHeader(value = ROLE, required = false) String role) {
        FillService.ExportFile f = fillService.export(code, body == null ? null : body.get("rids"), uid, role);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(f.content());
    }
}

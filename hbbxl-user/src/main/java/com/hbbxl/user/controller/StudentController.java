package com.hbbxl.user.controller;

import com.hbbxl.common.core.result.R;
import com.hbbxl.user.dao.StudentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学生端控制器
 */
@Tag(name = "学生端")
@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentMapper studentMapper;

    @Operation(summary = "获取学生考试类信息")
    @GetMapping("/category")
    public R<Map<String, Object>> category(@RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> result = studentMapper.selectStudentCategory(userId);
        return R.ok(result);
    }

    @Operation(summary = "科目列表（按学生考试大类过滤；非学生用户返回全部科目）")
    @GetMapping("/subjects")
    public R<List<Map<String, Object>>> subjects(@RequestHeader("X-User-Id") Long userId,
                                                  @RequestHeader("X-User-Role") String role) {
        // 学生按考试大类过滤，老师/管理员返回全部科目
        if ("student".equals(role)) {
            List<Map<String, Object>> list = studentMapper.selectSubjectsByCategory(userId);
            if (list != null && !list.isEmpty()) {
                return R.ok(list);
            }
        }
        // 非学生或学生无关联大类时，返回全部科目
        List<Map<String, Object>> list = studentMapper.selectAllSubjects();
        return R.ok(list);
    }
}
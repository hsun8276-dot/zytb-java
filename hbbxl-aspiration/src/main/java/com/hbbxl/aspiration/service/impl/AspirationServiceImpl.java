package com.hbbxl.aspiration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.aspiration.dao.AspirationMapper;
import com.hbbxl.aspiration.dao.SysUserDao;
import com.hbbxl.aspiration.entity.Aspiration;
import com.hbbxl.aspiration.service.AspirationService;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** 志愿方案服务实现: 学生管理/交单的业务规则都在这里 */
@Service
@RequiredArgsConstructor
public class AspirationServiceImpl implements AspirationService {

    private static final Set<String> FIRST_SUBJECTS = Set.of("物理", "历史");
    private static final Set<String> SECOND_SUBJECTS = Set.of("化学", "生物", "地理", "政治");
    private static final Set<String> BATCHES = Set.of("本科批", "专科批");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AspirationMapper aspirationMapper;
    private final SysUserDao sysUserDao;

    @Override
    public IPage<Map<String, Object>> pageList(long page, long size, String keyword, Integer submitStatus,
                                               Long currentUserId, String role) {
        LambdaQueryWrapper<Aspiration> qw = new LambdaQueryWrapper<Aspiration>()
                .eq(Aspiration::getDeleted, 0)
                .eq(submitStatus != null && (submitStatus == 0 || submitStatus == 1),
                        Aspiration::getSubmitStatus, submitStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Aspiration::getStudentName, keyword.trim())
                        .or().like(Aspiration::getAspirationName, keyword.trim())
                        .or().like(Aspiration::getContactPhone, keyword.trim()));
        if (!CommonConstants.ROLE_ADMIN.equals(role)) {
            qw.eq(Aspiration::getUserId, currentUserId);
        }
        qw.orderByDesc(Aspiration::getCreateTime);
        IPage<Aspiration> src = aspirationMapper.selectPage(new Page<>(page, size), qw);
        Page<Map<String, Object>> out = new Page<>(src.getCurrent(), src.getSize(), src.getTotal());
        out.setRecords(src.getRecords().stream().map(this::toRow).collect(Collectors.toList()));
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(Map<String, Object> body, Long currentUserId) {
        String studentName = str(body.get("studentName"));
        if (studentName == null) {
            throw new BizException("学生姓名不能为空");
        }
        if (studentName.length() > 50) {
            throw new BizException("学生姓名过长");
        }
        Integer score = intOrNull(body.get("score"));
        if (score == null || score < 0 || score > 750) {
            throw new BizException("高考分数不合法(0-750)");
        }
        String firstSubject = str(body.get("firstSubject"));
        if (firstSubject == null || !FIRST_SUBJECTS.contains(firstSubject)) {
            throw new BizException("首选科目必须为 物理/历史");
        }
        // 次选科目: 3+1+2 的 "2", 前端传来勾选数组, 必须恰好2科且不与首选重复
        LinkedHashSet<String> seconds = parseSecondSubjects(body.get("secondSubjects"), firstSubject);
        String batch = str(body.get("batch"));
        if (batch == null) batch = "本科批";
        if (!BATCHES.contains(batch)) {
            throw new BizException("批次必须为 本科批/专科批");
        }
        Aspiration a = new Aspiration();
        a.setAspirationCode(UUID.randomUUID().toString());
        a.setUserId(currentUserId);
        a.setStudentName(studentName);
        a.setGender(intOrNull(body.get("gender")) == null ? 1 : intOrNull(body.get("gender")));
        a.setProvince(strOr(body.get("province"), "河北"));
        a.setYear(intOrNull(body.get("year")) == null ? 2026 : intOrNull(body.get("year")));
        a.setBatch(batch);
        a.setGroupName(firstSubject + "组");
        a.setScore(score);
        a.setBonusScore(intOrZero(body.get("bonusScore")));
        a.setFirstSubject(firstSubject);
        a.setSecondSubjects(String.join(",", seconds));
        a.setAspirationName(strOr(body.get("aspirationName"), "志愿填报"));
        a.setHighSchool(strOr(body.get("highSchool"), ""));
        a.setContactPhone(strOr(body.get("contactPhone"), ""));
        a.setUpperParam(20);
        a.setLowerParam(60);
        a.setSubmitStatus(0);
        aspirationMapper.insert(a);
        return a.getAspirationCode();
    }

    @Override
    public Map<String, Object> detail(String aspirationCode, Long currentUserId, String role) {
        Aspiration a = loadOwned(aspirationCode, currentUserId, role);
        Map<String, Object> m = toRow(a);
        // 采集扩展段(扫码补全)也全部返回
        m.put("ethnicity", a.getEthnicity());
        m.put("isFresh", a.getIsFresh());
        m.put("chineseScore", a.getChineseScore());
        m.put("mathScore", a.getMathScore());
        m.put("englishScore", a.getEnglishScore());
        m.put("elective1Score", a.getElective1Score());
        m.put("elective2Score", a.getElective2Score());
        m.put("elective3Score", a.getElective3Score());
        m.put("heightCm", a.getHeightCm());
        m.put("weightKg", a.getWeightKg());
        m.put("colorBlind", a.getColorBlind());
        m.put("colorWeakness", a.getColorWeakness());
        m.put("leftVision", a.getLeftVision());
        m.put("rightVision", a.getRightVision());
        m.put("parentName", a.getParentName());
        m.put("fatherJob", a.getFatherJob());
        m.put("motherJob", a.getMotherJob());
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInfo(String aspirationCode, Map<String, Object> body, Long currentUserId, String role) {
        Aspiration a = loadOwned(aspirationCode, currentUserId, role);
        if (a.getSubmitStatus() != null && a.getSubmitStatus() == 1) {
            throw new BizException("该方案已交单, 志愿信息已锁定, 如需修改请先撤销交单");
        }
        Aspiration up = new Aspiration();
        up.setId(a.getId());
        if (body.containsKey("aspirationName")) {
            up.setAspirationName(strOr(body.get("aspirationName"), "志愿填报"));
        }
        if (body.containsKey("highSchool")) {
            up.setHighSchool(strOr(body.get("highSchool"), ""));
        }
        if (body.containsKey("contactPhone")) {
            up.setContactPhone(strOr(body.get("contactPhone"), ""));
        }
        aspirationMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSlider(String aspirationCode, Integer upperParam, Integer lowerParam,
                             Long currentUserId, String role) {
        Aspiration a = loadOwned(aspirationCode, currentUserId, role);
        if (a.getSubmitStatus() != null && a.getSubmitStatus() == 1) {
            throw new BizException("该方案已交单, 推荐参数已锁定");
        }
        checkSlider(upperParam);
        checkSlider(lowerParam);
        Aspiration up = new Aspiration();
        up.setId(a.getId());
        up.setUpperParam(upperParam);
        up.setLowerParam(lowerParam);
        aspirationMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String aspirationCode, Integer status, Long currentUserId, String role) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("交单状态不合法");
        }
        Aspiration a = loadOwned(aspirationCode, currentUserId, role);
        Aspiration up = new Aspiration();
        up.setId(a.getId());
        if (status == 1) {
            if (a.getSubmitStatus() != null && a.getSubmitStatus() == 1) {
                throw new BizException("该方案已交单, 请勿重复交单");
            }
            up.setSubmitStatus(1);
            String realName = sysUserDao.selectRealNameById(currentUserId);
            up.setSubmitter(realName == null || realName.isBlank() ? "填报师" + currentUserId : realName);
            up.setSubmitTime(LocalDateTime.now());
        } else {
            if (a.getSubmitStatus() == null || a.getSubmitStatus() != 1) {
                throw new BizException("该方案未交单, 无需撤销");
            }
            up.setSubmitStatus(0);
            up.setSubmitter("");
            up.setSubmitTime(null);
        }
        aspirationMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String aspirationCode, Long currentUserId, String role) {
        Aspiration a = loadOwned(aspirationCode, currentUserId, role);
        Aspiration up = new Aspiration();
        up.setId(a.getId());
        up.setDeleted(1);
        aspirationMapper.updateById(up);
    }

    /* ---------------- 私有方法 ---------------- */

    /** 按方案码加载未删除方案; 非管理员只能操作自己创建的 */
    private Aspiration loadOwned(String aspirationCode, Long currentUserId, String role) {
        if (aspirationCode == null || aspirationCode.isBlank()) {
            throw new BizException("方案码不能为空");
        }
        Aspiration a = aspirationMapper.selectOne(new LambdaQueryWrapper<Aspiration>()
                .eq(Aspiration::getAspirationCode, aspirationCode)
                .eq(Aspiration::getDeleted, 0));
        if (a == null) {
            throw new BizException("志愿方案不存在或已删除");
        }
        if (!CommonConstants.ROLE_ADMIN.equals(role) && !currentUserId.equals(a.getUserId())) {
            throw new BizException("无权操作该志愿方案");
        }
        return a;
    }

    private void checkSlider(Integer v) {
        if (v == null || v < 0 || v > 200) {
            throw new BizException("滑杆分数不合法(0-200)");
        }
    }

    private LinkedHashSet<String> parseSecondSubjects(Object raw, String firstSubject) {
        if (!(raw instanceof Collection<?> col) || col.isEmpty()) {
            throw new BizException("次选科目必须勾选2科(3+1+2)");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object o : col) {
            String s = String.valueOf(o).trim();
            if (!SECOND_SUBJECTS.contains(s)) {
                throw new BizException("次选科目不合法: " + s + "(仅可 化学/生物/地理/政治)");
            }
            if (s.equals(firstSubject)) {
                throw new BizException("次选科目不能与首选科目相同");
            }
            out.add(s);
        }
        if (out.size() != 2) {
            throw new BizException("次选科目必须恰好2科(3+1+2)");
        }
        return out;
    }

    /** 列表/详情行: 带展示字段(性别/选科/组别/时间串) */
    private Map<String, Object> toRow(Aspiration a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("aspirationCode", a.getAspirationCode());
        m.put("studentName", a.getStudentName());
        m.put("gender", a.getGender());
        m.put("genderText", a.getGender() != null && a.getGender() == 2 ? "女" : (a.getGender() != null && a.getGender() == 0 ? "未知" : "男"));
        m.put("province", a.getProvince());
        m.put("year", a.getYear());
        m.put("batch", a.getBatch());
        m.put("groupName", a.getGroupName());
        m.put("score", a.getScore());
        m.put("bonusScore", a.getBonusScore());
        m.put("firstSubject", a.getFirstSubject());
        String subjects = a.getFirstSubject()
                + (a.getSecondSubjects() == null || a.getSecondSubjects().isBlank()
                ? "" : "、" + a.getSecondSubjects().replace(",", "、"));
        m.put("subjectsDisplay", subjects);
        m.put("aspirationName", a.getAspirationName());
        m.put("highSchool", a.getHighSchool());
        m.put("contactPhone", a.getContactPhone());
        m.put("upperParam", a.getUpperParam());
        m.put("lowerParam", a.getLowerParam());
        m.put("submitStatus", a.getSubmitStatus());
        m.put("submitter", a.getSubmitter());
        m.put("submitTime", a.getSubmitTime() == null ? null : DT.format(a.getSubmitTime()));
        m.put("createTime", a.getCreateTime() == null ? null : DT.format(a.getCreateTime()));
        return m;
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private String strOr(Object o, String def) {
        String s = str(o);
        return s == null ? def : s;
    }

    private Integer intOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrZero(Object o) {
        Integer v = intOrNull(o);
        return v == null ? 0 : v;
    }
}

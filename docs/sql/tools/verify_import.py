# -*- coding: utf-8 -*-
"""入库结果 vs 参考系统(格学)抓包样本逐字段对照"""
import os, sys, json, collections
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'crawler'))
from common import db  # noqa: E402

API = os.path.join(os.path.dirname(__file__), '..', '..', 'reference_site', 'api')


def load(name):
    d = json.load(open(os.path.join(API, name + '.json'), encoding='utf-8'))
    d = d.get('d', d) if isinstance(d, dict) else d
    data = d['Data'] if isinstance(d, dict) else d
    return json.loads(data) if isinstance(data, str) else data


conn = db()
cur = conn.cursor()
cur.execute('SELECT recruit_major_id, unv_name, pre_min_mark, pre_min_rank, pre_min_percentile, major_plan, '
            'school_length, tuition, major_codes, required_subjects, unv_nature, unv_type, unv_attributes, '
            'has_master, has_doctor, plan_explain, unv_rank, first_time_admission, attention_type '
            'FROM recruit_major')
cols = [d[0] for d in cur.description]
rm = {r[0]: dict(zip(cols, r)) for r in cur.fetchall()}
cur.execute('SELECT recruit_major_id, year, plan, min_mark, min_mark_rank, min_mark_percentile, equivalent_min_mark '
            'FROM recruit_major_history')
hist = {(r[0], r[1]): r for r in cur.fetchall()}

c = collections.Counter()
bad = collections.defaultdict(list)
for name in ['GetRecommendMajors', 'GetUserAspMajors', 'GetDebutMajors']:
    for x in load(name):
        m = rm.get(x['RecruitMajorID'])
        c['样本'] += 1
        if not m:
            bad['缺失'].append(x['RecruitMajorID']); continue

        def chk(key, ok, detail):
            c[key + '_总'] += 1
            if ok:
                c[key + '_同'] += 1
            elif len(bad[key]) < 4:
                bad[key].append(detail)

        chk('院校名', m['unv_name'] == x['UnvName'], (m['unv_name'], x['UnvName']))
        chk('预测最低分', m['pre_min_mark'] == x['PreMinMark'], (x['RecruitMajorID'], m['pre_min_mark'], x['PreMinMark']))
        if x['PreMinMarkRank']:
            chk('预测位次', m['pre_min_rank'] == x['PreMinMarkRank'], (m['pre_min_rank'], x['PreMinMarkRank']))
            chk('预测百分位', abs(float(m['pre_min_percentile'] or -1) - x['PreMinPercentile']) < 0.001,
                (m['pre_min_percentile'], x['PreMinPercentile']))
        chk('计划', m['major_plan'] == x['MajorPlan'], (m['major_plan'], x['MajorPlan']))
        chk('学制', m['school_length'] == x['SchoolLength'], (m['school_length'], x['SchoolLength']))
        chk('学费', m['tuition'] == x['Tuition'], (x['RecruitMajorID'], m['tuition'], x['Tuition']))
        chk('专业代码', set(filter(None, m['major_codes'].split(','))) == set(x['MajorCodes']),
            (x['MajorName'], m['major_codes'], x['MajorCodes']))
        chk('选科', m['required_subjects'].split(',') == x['RequiredSubjects'] + x['ElectiveSubjects'],
            (m['required_subjects'], x['RequiredSubjects'], x['ElectiveSubjects']))
        chk('属性', m['unv_nature'] == x['UnvNature'], (m['unv_nature'], x['UnvNature']))
        chk('类型', m['unv_type'] == x['UnvType'], (m['unv_type'], x['UnvType']))
        chk('标签', [t for t in m['unv_attributes'].split(',') if t] == x['UnvAttributes'], (m['unv_attributes'], x['UnvAttributes']))
        chk('硕士点', bool(m['has_master']) == x['HasMasterUnit'], (m['unv_name'], m['has_master'], x['HasMasterUnit']))
        chk('博士点', bool(m['has_doctor']) == x['HasDoctorUnit'], (m['unv_name'], m['has_doctor'], x['HasDoctorUnit']))
        chk('专业解释', m['plan_explain'] == x['RecruitPlanExplain'], (m['plan_explain'], x['RecruitPlanExplain']))
        chk('高校排名', m['unv_rank'] == x['UnvRank'], (m['unv_name'], m['unv_rank'], x['UnvRank']))
        chk('首次招生', bool(m['first_time_admission']) == x['UnvFirstTimeAdmission'],
            (m['unv_name'], m['first_time_admission'], x['UnvFirstTimeAdmission']))
        for h in x['MajorHistorys']:
            row = hist.get((x['RecruitMajorID'], h['Year']))
            if h['MinMark'] is None:
                continue
            chk('历年最低分', row and row[3] == h['MinMark'], (x['RecruitMajorID'], h['Year'], row and row[3], h['MinMark']))
            chk('历年位次', row and row[4] == h['MinMarkRank'], (h['Year'], row and row[4], h['MinMarkRank']))
            chk('历年百分位', row and row[5] is not None and abs(float(row[5]) - h['MinMarkPercentile']) < 0.001,
                (h['Year'], row and row[5], h['MinMarkPercentile']))
            chk('等效分', row and row[6] == h['EquivalentMinMark'], (h['Year'], row and row[6], h['EquivalentMinMark']))
            chk('等效分±1', row and row[6] is not None and abs(row[6] - h['EquivalentMinMark']) <= 1,
                (h['Year'], row and row[6], h['EquivalentMinMark']))
            chk('历年计划', row and row[2] == h['Plan'], (h['Year'], row and row[2], h['Plan']))

print('样本', c['样本'], '缺失', len(bad['缺失']))
for k in sorted({k[:-2] for k in c if k.endswith('_总')}):
    print(f"  {k}: {c[k + '_同']}/{c[k + '_总']}", bad.get(k, [])[:4])

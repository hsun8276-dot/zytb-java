# -*- coding: utf-8 -*-
"""格学 PreMinMark 与 2026 官方投档线 / 26参考分 对比"""
import os, sys, json, collections
import openpyxl
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'crawler'))
from common import db

API = os.path.join(os.path.dirname(__file__), '..', '..', 'reference_site', 'api')


def load(name):
    d = json.load(open(os.path.join(API, name + '.json'), encoding='utf-8'))
    d = d.get('d', d) if isinstance(d, dict) else d
    data = d['Data'] if isinstance(d, dict) else d
    return json.loads(data) if isinstance(data, str) else data


c = db().cursor()
c.execute("select college_code, major_code, min_mark, min_rank from score_line "
          "where province='河北' and year=2026 and batch='本科批' and subject_type='物理类'")
off = {(str(a).zfill(4), b): (m, r) for a, b, m, r in c.fetchall()}

wb = openpyxl.load_workbook(r'F:\教育考试院\河北省\2026年河北本科物理组志愿数据.xlsx', read_only=True, data_only=True)
rows = list(wb.worksheets[0].iter_rows(values_only=True))
h = rows[0]; ix = {k: i for i, k in enumerate(h)}
ref = {(str(r[ix['院校代码']]).zfill(4), str(r[ix['专业代码']]).zfill(2)): r for r in rows[1:]}

for name in ['GetRecommendMajors', 'GetUserAspMajors', 'GetDebutMajors']:
    data = load(name)
    cnt = collections.Counter()
    diffs = collections.Counter()
    for x in data:
        k = (x['UnvCode'], x['RecruitMajorCode'])
        pm = x['PreMinMark']
        o = off.get(k)
        r = ref.get(k)
        if o:
            cnt['official_hit'] += 1
            if o[0] == pm: cnt['official_eq'] += 1
            elif pm is not None and o[0] is not None: diffs[pm - o[0]] += 1
        if r:
            cnt['xlsx_hit'] += 1
            if r[ix['26参考分']] == pm: cnt['ref_eq'] += 1
            if r[ix['26人']] == x['MajorPlan']: cnt['plan_eq'] += 1
    print(name, len(data), dict(cnt), 'premin-official 差值:', diffs.most_common(8))
    for x in data:
        k = (x['UnvCode'], x['RecruitMajorCode'])
        if k not in off:
            r = ref.get(k)
            print('   未投出:', x['RecruitMajorID'], x['UnvName'], x['MajorName'], 'PreMin=', x['PreMinMark'],
                  'xlsx参考分=', r[ix['26参考分']] if r else None, '新招=', r[ix['新招专业']] if r else None,
                  'Attn=', x['MajorAttendtionType'], 'PreMinRank=', x['PreMinMarkRank'], 'Pct=', x['PreMinPercentile'])

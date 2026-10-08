# -*- coding: utf-8 -*-
"""河北省教育考试院(hebeea.edu.cn) 高考数据采集
数据源: 普通高考栏目 /ptgk/ (翻页 index_N.html), 文章 /c/YYYY-MM-DD/id.html, 附件挂在 file.hebeea.edu.cn
  - 投档线: 《20XX年河北省本科批/专科批平行志愿投档情况统计》 每篇 2 个 xlsx(物理科目组合/历史科目组合)
      xlsx 结构: 第2行标注科类; 列 院校代号|院校名称(城市)[性质]|专业代号|专业名称|投档最低分|语数之和|语数最高|外语|首选科目|再选最高|再选次高|志愿号|备注
  - 一分一段: 《20XX年河北省普通高校招生各类考生成绩统计表》等(扫描图/pdf/xlsx 形态逐年不同)
排除: 对口本科批/对口专科批(对口升学)、提前批、征集志愿
用法: python hebei.py discover        # 列出发现的公告与附件
      python hebei.py score_line      # 下载并导入三年投档线
      python hebei.py rank            # 一分一段
"""
import re, sys, os
import openpyxl
from common import (html, download, db, ledger_upsert, replace_score_line, replace_score_ranking,
                    summary, property_from_name, to_int, rel)

PROV = '河北'
BASE = 'https://www.hebeea.edu.cn'
YEARS = (2024, 2025, 2026)

# 栏目只保留最近 5 页(2025-04 之后), 更早的公告列表页不可达但文章直链仍有效, 在此登记
KNOWN = [
    (2024, 'score_line', '本科批', f'{BASE}/c/2024-07-22/489446.html', '2024年河北省本科批平行志愿投档情况统计'),
    (2024, 'score_line', '专科批', f'{BASE}/c/2024-08-10/489182.html', '2024年河北省专科批平行志愿投档情况统计'),
]


def list_articles(max_pages=12):
    """遍历 /ptgk/ 栏目, 返回 [(url, title, date)]"""
    out, seen = [], set()
    for i in range(1, max_pages + 1):
        url = f'{BASE}/ptgk/' if i == 1 else f'{BASE}/ptgk/index_{i}.html'
        try:
            page = html(url, 'utf-8')
        except Exception as e:
            print(f'  栏目页 {url} 失败: {e}'); break
        for m in re.finditer(r'href="(/c/(\d{4}-\d{2}-\d{2})/\d+\.html)"[^>]*>([^<]{4,80})<', page):
            u, d, t = BASE + m.group(1), m.group(2), m.group(3).strip()
            if u not in seen:
                seen.add(u); out.append((u, t, d))
    return out


def attachments(article_url):
    page = html(article_url, 'utf-8')
    files = []
    for m in re.finditer(r'href="(https?://file\.hebeea\.edu\.cn/[^"]+\.(xlsx?|pdf|zip|docx?))"', page, re.I):
        if m.group(1) not in files:
            files.append(m.group(1))
    imgs = re.findall(r'<img[^>]+src="([^"]+\.(?:jpg|jpeg|png))"', page, re.I)
    return files, imgs, page


def classify(title):
    """返回 (data_type, batch) 或 None"""
    if any(k in title for k in ('对口', '征集', '提前批', '艺术', '体育', '高职单招')):
        return None
    if '投档情况统计' in title:
        if '本科批' in title:
            return 'score_line', '本科批'
        if '专科批' in title:
            return 'score_line', '专科批'
        return None
    if '成绩统计' in title or '一分一档' in title or '分段统计' in title:
        return 'score_ranking', ''
    return None


def discover():
    arts = list_articles()
    print(f'栏目共 {len(arts)} 篇文章')
    found = []
    for u, t, d in arts:
        c = classify(t)
        y = int(d[:4])
        m = re.search(r'(20\d{2})年', t)
        if m:
            y = int(m.group(1))
        if c and y in YEARS:
            found.append((y, c[0], c[1], u, t))
    urls = {f[3] for f in found}
    found += [k for k in KNOWN if k[3] not in urls]
    for f in sorted(found):
        print(f)
    return found


def parse_line_xlsx(path):
    """返回 (subject_type, rows[dict]) ; 科类取自前 4 行文本"""
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb[wb.sheetnames[0]]
    rows = list(ws.iter_rows(values_only=True))
    head = ''.join(str(v or '') for r in rows[:5] for v in r)
    subject = '物理类' if '物理' in head else ('历史类' if '历史' in head else None)
    out = []
    for r in rows:
        if len(r) < 5:
            continue
        code, name, mcode, mname, mark = (str(r[0] or '').strip(), str(r[1] or '').strip(),
                                          str(r[2] or '').strip(), str(r[3] or '').strip(), to_int(r[4]))
        if not code.isdigit() or not mcode or not mname or mark is None:
            continue
        tiebreak = '/'.join(str(v).strip() if v is not None else '' for v in r[5:12]) if len(r) >= 12 else ''
        remark = str(r[12] or '').strip() if len(r) > 12 else ''
        out.append(dict(college_code=int(code), college_name=name, major_code=mcode, major_name=mname,
                        min_mark=mark, property=property_from_name(name), tiebreak=tiebreak, remark=remark))
    return subject, out


def run_score_line():
    conn = db()
    for y, dtype, batch, url, title in discover():
        if dtype != 'score_line':
            continue
        files, _, _ = attachments(url)
        print(f'\n{y} {batch} {title} -> {len(files)} 附件')
        for fu in files:
            local = download(fu, PROV, f'{y}_{batch}_{os.path.basename(fu)}', referer=url)
            subject, rows = parse_line_xlsx(local)
            if not subject:
                ledger_upsert(conn, PROV, y, dtype, batch, '', url, fu, rel(local), 'xlsx', 0, 'skipped', '无法识别科类')
                print(f'  [跳过] 无法识别科类 {fu}'); continue
            n = replace_score_line(conn, PROV, y, batch, subject, rows)
            ledger_upsert(conn, PROV, y, dtype, batch, subject, url, fu, rel(local), 'xlsx', n, 'loaded', title)
            marks = [r['min_mark'] for r in rows]
            print(f'  {subject}: 入库 {n} 行, 分数 {min(marks)}~{max(marks)}')
    summary(conn, PROV)
    conn.close()


def run_rank():
    """一分一段: 先列出候选公告与附件形态, 解析逻辑按形态分支"""
    conn = db()
    for y, dtype, batch, url, title in discover():
        if dtype != 'score_ranking':
            continue
        files, imgs, page = attachments(url)
        print(f'\n{y} {title}\n  {url}\n  附件: {files}\n  图片: {len(imgs)} 张')
        for fu in files:
            download(fu, PROV, f'{y}_rank_{os.path.basename(fu)}', referer=url)
    conn.close()


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'discover'
    {'discover': discover, 'score_line': run_score_line, 'rank': run_rank}[cmd]()

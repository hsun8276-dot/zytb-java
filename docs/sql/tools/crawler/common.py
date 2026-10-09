# -*- coding: utf-8 -*-
"""考试院数据采集公共模块

铁律: 高考数据(一分一段/投档线/招生计划)一律取自各省教育考试院官网, 不用第三方聚合站, 不用 baoxilv 库。

职责:
  - fetch(): 带 UA 的 GET, 自动重试
  - download(): 附件下载到 data_raw/<省>/ 并缓存(已存在不重复下载)
  - db(): zytb 连接
  - ledger_*: crawl_source 采集台账(来源可追溯 + 幂等进度)
  - replace_rows(): 按维度 DELETE 后批量 INSERT 的幂等落库
"""
import os, re, sys, time, hashlib, datetime as dt
import requests
import pymysql

sys.stdout.reconfigure(encoding='utf-8')

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RAW_DIR = os.path.join(REPO, 'data_raw')

DB = dict(host=os.environ.get('ZYTB_DB_HOST', '127.0.0.1'),
          port=int(os.environ.get('ZYTB_DB_PORT', '3306')),
          user=os.environ.get('ZYTB_DB_USER', 'root'),
          password=os.environ.get('ZYTB_DB_PASS', 'root'),
          database=os.environ.get('ZYTB_DB_NAME', 'zytb'),
          charset='utf8mb4')

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
}

_session = requests.Session()
_session.headers.update(HEADERS)


def fetch(url, retries=3, timeout=30, **kw):
    last = None
    for i in range(retries):
        try:
            r = _session.get(url, timeout=timeout, **kw)
            if r.status_code == 200:
                return r
            last = Exception(f'HTTP {r.status_code}')
        except Exception as e:
            last = e
        time.sleep(1.5 * (i + 1))
    raise last


def html(url, encoding=None):
    r = fetch(url)
    r.encoding = encoding or r.apparent_encoding or 'utf-8'
    return r.text


def download(url, province, name=None, referer=None):
    """下载附件到 data_raw/<province>/<name>, 已存在则直接返回"""
    d = os.path.join(RAW_DIR, province)
    os.makedirs(d, exist_ok=True)
    if not name:
        base = os.path.basename(url.split('?')[0]) or hashlib.md5(url.encode()).hexdigest()
        name = base
    path = os.path.join(d, name)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    headers = {'Referer': referer} if referer else {}
    r = fetch(url, headers=headers, timeout=120)
    with open(path, 'wb') as f:
        f.write(r.content)
    print(f'  下载 {name} ({len(r.content)//1024} KB)')
    return path


def rel(path):
    return os.path.relpath(path, REPO).replace('\\', '/')


def db():
    return pymysql.connect(**DB)


def ledger_upsert(conn, province, year, data_type, batch='', subject_type='', page_url='', file_url='',
                  local_file='', file_format='', rows_loaded=None, status=None, note=None):
    """台账写入: 以 (省,年,类型,批次,科类,附件) 为键; rows/status/note 为 None 时不覆盖"""
    cur = conn.cursor()
    cur.execute("""SELECT id FROM crawl_source WHERE province=%s AND year=%s AND data_type=%s
                   AND batch=%s AND subject_type=%s AND file_url=%s""",
                (province, year, data_type, batch, subject_type, file_url))
    row = cur.fetchone()
    now = dt.datetime.now()
    if row:
        sets, vals = [], []
        for col, v in (('page_url', page_url), ('local_file', local_file), ('file_format', file_format),
                       ('rows_loaded', rows_loaded), ('status', status), ('note', note)):
            if v not in (None, ''):
                sets.append(f'{col}=%s'); vals.append(v)
        if status == 'downloaded':
            sets.append('fetched_at=%s'); vals.append(now)
        if status == 'loaded':
            sets.append('loaded_at=%s'); vals.append(now)
        if sets:
            vals.append(row[0])
            cur.execute(f"UPDATE crawl_source SET {', '.join(sets)} WHERE id=%s", vals)
    else:
        cur.execute("""INSERT INTO crawl_source (province,year,data_type,batch,subject_type,page_url,file_url,
                       local_file,file_format,rows_loaded,status,note,fetched_at,loaded_at)
                       VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                    (province, year, data_type, batch, subject_type, page_url, file_url, local_file, file_format,
                     rows_loaded or 0, status or 'pending', note or '',
                     now if status in ('downloaded', 'loaded') else None,
                     now if status == 'loaded' else None))
    conn.commit()


def replace_score_line(conn, province, year, batch, subject_type, rows):
    """rows: dict 列表, 键为 score_line 列名. 按 (省,年,批次,科类) 幂等覆盖"""
    cur = conn.cursor()
    cur.execute("DELETE FROM score_line WHERE province=%s AND year=%s AND batch=%s AND subject_type=%s",
                (province, year, batch, subject_type))
    if not rows:
        conn.commit(); return 0
    cols = ['province', 'year', 'batch', 'subject_type', 'college_code', 'college_name', 'major_code',
            'group_code', 'major_name', 'subject_requirement', 'property', 'min_mark', 'avg_mark',
            'min_rank', 'avg_rank', 'plan_count', 'tiebreak', 'remark']
    sql = f"INSERT INTO score_line ({','.join(cols)}) VALUES ({','.join(['%s']*len(cols))})"
    data = []
    for r in rows:
        r = dict(r)
        r.update(province=province, year=year, batch=batch, subject_type=subject_type)
        data.append(tuple(r.get(c) if r.get(c) is not None else ('' if c in
                    ('college_name', 'major_code', 'group_code', 'major_name', 'subject_requirement',
                     'property', 'tiebreak', 'remark') else None) for c in cols))
    for i in range(0, len(data), 2000):
        cur.executemany(sql, data[i:i+2000])
    conn.commit()
    return len(data)


def replace_score_ranking(conn, province, year, subject_type, rows):
    """rows: [(score, same_count, cumulative)] 按 (省,年,科类) 幂等覆盖"""
    cur = conn.cursor()
    cur.execute("DELETE FROM score_ranking WHERE province=%s AND year=%s AND subject_type=%s",
                (province, year, subject_type))
    data = [(province, year, subject_type, s, c or 0, cum) for s, c, cum in rows]
    if data:
        cur.executemany("INSERT INTO score_ranking (province,year,subject_type,score,same_count,cumulative) "
                        "VALUES (%s,%s,%s,%s,%s,%s)", data)
    conn.commit()
    return len(data)


def summary(conn, province):
    cur = conn.cursor()
    cur.execute("""SELECT year,batch,subject_type,COUNT(*),MIN(min_mark),MAX(min_mark) FROM score_line
                   WHERE province=%s GROUP BY 1,2,3 ORDER BY 1,2,3""", (province,))
    print(f'[{province}] score_line:')
    for r in cur.fetchall():
        print('   ', r)
    cur.execute("""SELECT year,subject_type,COUNT(*),MIN(score),MAX(score),MAX(cumulative) FROM score_ranking
                   WHERE province=%s GROUP BY 1,2 ORDER BY 1,2""", (province,))
    print(f'[{province}] score_ranking:')
    for r in cur.fetchall():
        print('   ', r)


def property_from_name(name):
    m = re.search(r'\[([^\]]+)\]', name or '')
    return m.group(1) if m else ''


def to_int(v):
    if v is None:
        return None
    try:
        s = str(v).strip().replace(',', '')
        if s in ('', '-', '—', '/'):
            return None
        return int(float(s))
    except (TypeError, ValueError):
        return None

# -*- coding: utf-8 -*-
"""从格学 9003 拉取职业库写入 zytb.profession / profession_major"""
from __future__ import annotations

import json
import time
import urllib.parse
import urllib.request

import pymysql

BASE = "http://39.107.86.41:9003"
DB = dict(host="127.0.0.1", user="root", password="root", database="zytb", charset="utf8mb4")


def get_json(path: str):
    url = BASE + path
    with urllib.request.urlopen(url, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def ensure_tables(cur):
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS profession (
            id                BIGINT AUTO_INCREMENT PRIMARY KEY,
            occ_id            INT          NOT NULL COMMENT '源站 occId',
            occ_name          VARCHAR(100) NOT NULL COMMENT '职业名称',
            industry          VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '行业',
            occ_type          VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '职业类别',
            position_salary   VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '岗位薪资',
            definition        TEXT         NULL COMMENT '职业定义',
            occ_introduction  TEXT         NULL COMMENT '职业介绍/工作内容',
            job_require       TEXT         NULL COMMENT '岗位要求',
            occ_trait         TEXT         NULL COMMENT '职业特点',
            development_space TEXT         NULL COMMENT '发展空间',
            work_contact      TEXT         NULL COMMENT '工作交往',
            create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_occ_id (occ_id),
            KEY idx_industry (industry),
            KEY idx_occ_type (occ_type),
            KEY idx_name (occ_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·职业库'
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS profession_major (
            id         BIGINT AUTO_INCREMENT PRIMARY KEY,
            occ_id     INT          NOT NULL,
            major_code VARCHAR(20)  NOT NULL DEFAULT '',
            major_name VARCHAR(100) NOT NULL DEFAULT '',
            bachelor   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '授予学位',
            duration   VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '学制',
            UNIQUE KEY uk_occ_major (occ_id, major_code),
            KEY idx_occ (occ_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·职业关联专业'
        """
    )


def main():
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    ensure_tables(cur)
    cur.execute("DELETE FROM profession_major")
    cur.execute("DELETE FROM profession")
    conn.commit()

    listing = get_json("/getOccList?industry=&occType=&occName=")
    rows = listing.get("data") or []
    print(f"list count={len(rows)}")

    for i, row in enumerate(rows, 1):
        occ_id = int(row["occId"])
        info = get_json(f"/getOccInfo?occId={occ_id}").get("data") or row
        cur.execute(
            """
            INSERT INTO profession (
              occ_id, occ_name, industry, occ_type, position_salary,
              definition, occ_introduction, job_require, occ_trait,
              development_space, work_contact
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (
                occ_id,
                (info.get("occName") or row.get("occName") or "").strip(),
                (info.get("industry") or row.get("industry") or "").strip(),
                (info.get("occType") or row.get("occType") or "").strip(),
                (info.get("positionSalary") or row.get("positionSalary") or "").strip(),
                info.get("definition") or "",
                info.get("occIntroduction") or "",
                info.get("jobRequire") or "",
                info.get("occTrait") or "",
                info.get("developmentSpace") or "",
                info.get("workContact") or "",
            ),
        )
        try:
            maj = get_json(f"/getOccMajor?occId={occ_id}").get("data") or []
        except Exception:
            maj = []
        for m in maj:
            code = (m.get("majorCode") or "").strip()
            name = (m.get("majorName") or "").strip()
            if not code and not name:
                continue
            cur.execute(
                """
                INSERT INTO profession_major (occ_id, major_code, major_name, bachelor, duration)
                VALUES (%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE
                  major_name=VALUES(major_name),
                  bachelor=VALUES(bachelor),
                  duration=VALUES(duration)
                """,
                (
                    occ_id,
                    code,
                    name,
                    (m.get("bacherlor") or m.get("bachelor") or "").strip(),
                    (m.get("duration") or "").strip(),
                ),
            )
        if i % 50 == 0:
            conn.commit()
            print(f"  imported {i}/{len(rows)}")
        time.sleep(0.03)

    conn.commit()
    cur.execute("SELECT COUNT(*) FROM profession")
    print("profession", cur.fetchone()[0])
    cur.execute("SELECT COUNT(*) FROM profession_major")
    print("profession_major", cur.fetchone()[0])
    cur.execute("SELECT COUNT(DISTINCT industry) FROM profession")
    print("industries", cur.fetchone()[0])
    conn.close()


if __name__ == "__main__":
    main()

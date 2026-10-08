"""db/mysql/02_seed.sql(MySQL 데모 데이터)를 앱이 쓰는 PostgreSQL용 db/demo/seed.sql로 옮긴다.

- 명시적 id를 넣으므로 IDENTITY 테이블에는 OVERRIDING SYSTEM VALUE, 끝에서 시퀀스를 max(id)로 맞춘다.
- 가상 CDN(cdn.example.com) 사진은 앱에서 열리지 않으므로 본문 사진·썸네일·프로필 사진 연결을 뺀다(기본 아이콘으로 보인다).
- render_version을 0으로 두어 앱의 다시 렌더링 작업이 본문 HTML을 지금 규칙으로 새로 만들게 한다.
다시 만들기: python3 db/demo/convert_mysql_seed.py
"""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
src = (root / "db/mysql/02_seed.sql").read_text(encoding="utf-8")
identity = {"member", "image", "auth_identity", "tag", "post", "comment", "report_case", "report",
            "member_suspension", "notification"}

s = src
s = re.sub(r"^SET .*?;\n", "", s, flags=re.M)
s = s.replace("START TRANSACTION;", "BEGIN;\nSET LOCAL TIME ZONE 'UTC';")
s = re.sub(r"^UPDATE member SET profile_image_id = .*?;\n", "", s, flags=re.M)
s = re.sub(r"^-- 순환 FK: 사진을 넣은 뒤 프로필 사진 연결\n", "", s, flags=re.M)
s = re.sub(r"\n\n!\[[^\]]*\]\(https://cdn\.example\.com/[^)]*\)", "", s)
s = re.sub(r"\n?<p><img src=\"https://cdn\.example\.com/[^\"]*\"[^>]*></p>", "", s)
s = re.sub(r"'https://cdn\.example\.com/[^']*'", "NULL", s)
s = re.sub(r"^-- AUTO_INCREMENT.*\n?", "", s, flags=re.M)


def overriding(m):
    table = m.group(1)
    return m.group(0).replace(") VALUES", ") OVERRIDING SYSTEM VALUE VALUES") if table in identity else m.group(0)


s = re.sub(r"^INSERT INTO (\w+) \(.*?\) VALUES", overriding, s, flags=re.M)
assert "cdn.example.com" not in s

tail = ["", "-- 명시적 id 뒤로 번호가 이어지게"]
for t in sorted(identity):
    tail.append(f"SELECT setval(pg_get_serial_sequence('{t}', 'id'), (SELECT max(id) FROM {t}));")
tail.append("-- 본문 HTML은 앱의 다시 렌더링 작업이 지금 규칙으로 새로 만든다")
tail.append("UPDATE post SET render_version = 0 WHERE status = 'PUBLISHED';")
s = s.replace("COMMIT;", "\n".join(tail) + "\n\nCOMMIT;")

header = """-- =============================================================================
-- PostgreSQL 데모 데이터(앱용) — db/mysql/02_seed.sql에서 db/demo/convert_mysql_seed.py로 만든 파일. 직접 고치지 말고 원본을 고친 뒤 다시 만든다.
-- 빈 DB(Flyway V1까지 적용된 상태)에 한 번 넣는다. 데모 전용, 모든 이메일·ID는 가상의 값.
-- LOCAL 데모 계정 비밀번호: password1!  (admin@example.com 관리자, minji@example.com, hyunwoo.choi@example.com, sujin@example.com)
-- =============================================================================
"""
s = header + s.split("=============================================================================\n", 2)[-1]
(root / "db/demo/seed.sql").write_text(s, encoding="utf-8")
print("db/demo/seed.sql", len(s.splitlines()), "lines")

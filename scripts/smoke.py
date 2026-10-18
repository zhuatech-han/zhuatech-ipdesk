#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""全新实例的实际HTTP验收；只创建明确标记TEST的资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import datetime as dt
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo

checks = 0


def check(ok, message):
    """保留实际断言，不输出凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global checks
    if not ok:
        raise AssertionError(message)
    checks += 1


class Client:
    """每个身份使用独立Cookie与CSRF。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

    def __init__(self, url):
        self.url = url.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def call(self, path, method="GET", body=None, expected=200, raw=False, upload=None):
        headers = {}
        if method != "GET":
            t = self.call("/api/auth/csrf")
            headers[t["header"]] = t["token"]
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        if upload:
            boundary = "TEST" + secrets.token_hex(16)
            parts = []
            for key, value in upload.get("fields", {}).items():
                parts.append(
                    f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
                )
            parts.append(
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{upload["name"]}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()
            )
            parts += [upload["data"], f"\r\n--{boundary}--\r\n".encode()]
            data = b"".join(parts)
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        req = urllib.request.Request(
            self.url + path, data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(req, timeout=60) as r:
                status, result = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, result = e.code, e.read()
        check(
            status == expected,
            f"{method} {path}: expected {expected}, got {status}: {result[:220]!r}",
        )
        if raw:
            return result
        return json.loads(result) if result else None

    def post(self, path, body, expected=200):
        return self.call(path, "POST", body, expected)

    def login(self, username, password):
        return self.post(
            "/api/auth/login", {"username": username, "password": password}
        )


def pdf(label="TEST IPDesk evidence", padding=0):
    """生成有真实交叉引用表的独立验收PDF。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    stream = (
        f"BT /F1 14 Tf 60 740 Td ({label}) Tj ET".encode()
        + b"\n% TEST evidence padding" * padding
    )
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length "
        + str(len(stream)).encode()
        + b" >>\nstream\n"
        + stream
        + b"\nendstream",
    ]
    out = b"%PDF-1.4\n"
    offsets = [0]
    for n, obj in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{n} 0 obj\n".encode() + obj + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode()
    out += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    return (
        out
        + f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    )


def main():
    """全新MySQL上的提案、授权、期限、费用和真实邮件验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8176")
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--private-state", required=True)
    parser.add_argument("--mailpit-url", default="")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    state = Path(args.private_state).resolve()
    check(
        not state.is_relative_to(root) and not state.exists(),
        "Use new private state outside repository",
    )
    envfile = Path(args.env_file)
    check(
        envfile.is_file() and not envfile.stat().st_mode & 0o077,
        "Private env mode600 required",
    )
    env = dict(
        x.split("=", 1)
        for x in envfile.read_text().splitlines()
        if "=" in x and not x.startswith("#")
    )
    admin = Client(args.url)
    anon = Client(args.url)
    anon.call("/api/matters", expected=401)
    check(anon.call("/actuator/health")["status"] == "UP", "healthy application")
    admin.login(env.get("ADMIN_USERNAME") or "admin", env["ADMIN_PASSWORD"])
    check(admin.call("/api/matters")["total"] == 0, "Fresh business database required")
    check(
        len(admin.call("/api/admin/accounts")) == 1, "Only bootstrap account may exist"
    )
    pw = "Aa9" + secrets.token_hex(20)
    agency = admin.post(
        "/api/admin/departments",
        {
            "code": "TEST-AGENCY",
            "name": "TEST代理机构",
            "kind": "AGENCY",
            "enabled": True,
        },
    )
    other = admin.post(
        "/api/admin/departments",
        {
            "code": "TEST-OTHER",
            "name": "TEST其他部门",
            "kind": "INTERNAL",
            "enabled": True,
        },
    )
    clients = {}
    users = {}
    for name, role, dept in [
        ("owner", 2, 1),
        ("manager", 3, 1),
        ("reviewer", 4, 1),
        ("writer", 5, 1),
        ("checker", 6, 1),
        ("agent", 7, agency["id"]),
        ("agent2", 7, agency["id"]),
        ("outside", 3, other["id"]),
    ]:
        user = admin.post(
            "/api/admin/accounts",
            {
                "username": "test_" + name,
                "displayName": "TEST " + name,
                "password": pw,
                "roleId": role,
                "departmentId": dept,
                "email": "test_" + name + "@example.invalid",
                "mailOptIn": True,
                "enabled": True,
            },
        )
        users[name] = user
        clients[name] = Client(args.url)
        clients[name].login(user["username"], pw)
    owner, manager, reviewer, writer, checker, agent, agent2, outside = [
        clients[k]
        for k in [
            "owner",
            "manager",
            "reviewer",
            "writer",
            "checker",
            "agent",
            "agent2",
            "outside",
        ]
    ]
    agent.call("/api/admin/accounts", expected=403)
    agent.call("/api/proposals", expected=403)
    admin.post(
        "/api/admin/roles",
        {"name": "TEST越权", "scope": "AGENT", "permissions": ["matters", "admin"]},
        expected=400,
    )
    admin.post(
        "/api/admin/accounts",
        {
            "username": "test_escalate",
            "displayName": "TEST",
            "password": pw,
            "roleId": 1,
            "departmentId": agency["id"],
            "enabled": True,
        },
        expected=400,
    )
    p = owner.post(
        "/api/proposals",
        {
            "title": "TEST 企业知识产权与代理协同",
            "rightType": "PATENT",
            "summary": "TEST合成技术交底：用于验收流程，不对应真实发明或客户。",
        },
    )
    p = owner.post(f'/api/proposals/{p["id"]}/submit', {"version": p["version"]})
    owner.post(
        f'/api/proposals/{p["id"]}/approve',
        {"version": p["version"], "note": "TEST"},
        expected=403,
    )
    p = reviewer.post(
        f'/api/proposals/{p["id"]}/approve',
        {"version": p["version"], "note": "TEST独立评审通过"},
    )
    m = manager.post(
        "/api/matters",
        {
            "proposalId": p["id"],
            "code": "TEST-IP-001",
            "jurisdiction": "中国",
            "applicant": "TEST企业",
            "applicationNo": "TEST-APPLICATION-001",
        },
    )
    mid = m["id"]
    manager.post(
        "/api/matters",
        {
            "proposalId": p["id"],
            "code": "TEST-DUP",
            "jurisdiction": "中国",
            "applicant": "TEST",
        },
        expected=400,
    )
    m = manager.post(
        f"/api/matters/{mid}/assign",
        {
            "version": m["version"],
            "ownerId": users["owner"]["id"],
            "agentId": users["agent"]["id"],
            "note": "TEST显式授权代理",
        },
    )
    manager.post(
        f"/api/matters/{mid}/assign",
        {
            "version": m["version"] - 1,
            "ownerId": users["owner"]["id"],
            "note": "TEST过期写",
        },
        expected=409,
    )
    check(
        agent.call("/api/matters")["total"] == 1, "explicitly assigned agent sees case"
    )
    agent2.call(f"/api/matters/{mid}", expected=404)
    outside.call(f"/api/matters/{mid}", expected=404)

    def upload(client, name="TEST-notice.pdf", data=None, shared=False, expected=200):
        return client.call(
            f"/api/matters/{mid}/files",
            "POST",
            expected=expected,
            upload={
                "name": name,
                "data": data if data is not None else pdf(),
                "fields": {"shared": str(shared).lower(), "note": "TEST验收资料"},
            },
        )

    internal = upload(manager, "TEST-internal.pdf")
    shared = upload(manager, shared=True)
    shared2 = upload(manager, shared=True)
    check(shared2["versionNumber"] == 2, "same-name file retains new version")
    proof = upload(writer, "TEST-proof.pdf", pdf("TEST proof", padding=120000))
    check(proof["sizeBytes"] > 2800000, "real large evidence upload")
    upload(manager, "TEST-oversize.pdf", b"x" * (3 * 1024 * 1024 + 1), expected=413)
    upload(manager, "TEST-fake.pdf", b"%PDF-fake", expected=400)
    upload(manager, "TEST.html", b"<script>test</script>", expected=400)
    agent.call(f'/api/files/{internal["id"]}', expected=404)
    check(
        hashlib.sha256(agent.call(f'/api/files/{shared["id"]}', raw=True)).hexdigest()
        == shared["digest"],
        "original shared file SHA",
    )
    ad = agent.call(f"/api/matters/{mid}")
    check(
        "proposal" not in ad and "fees" not in ad and "audit" not in ad,
        "no internal detail leakage",
    )
    check(len(ad["files"]) == 2, "only shared files visible")
    today = dt.datetime.now(ZoneInfo("Asia/Shanghai")).date()

    def new_deadline(
        title="TEST来文答复",
        previous=None,
        source=None,
        responsible=None,
        expected=200,
        due=None,
    ):
        b = {
            "title": title,
            "kindId": 1,
            "dueOn": str(due or today + dt.timedelta(days=3)),
            "responsibleId": responsible or users["agent"]["id"],
            "sourceFileId": source or shared["id"],
            "reason": "TEST正式合成来文日期人工录入",
        }
        if previous:
            b.update(previousId=previous["id"], previousVersion=previous["version"])
        return manager.post(f"/api/matters/{mid}/deadlines", b, expected)

    new_deadline(source=internal["id"], expected=400)
    new_deadline(due=dt.date(1800, 1, 1), expected=400)
    d = new_deadline()
    check(
        len(agent.call(f"/api/matters/{mid}")["deadlines"]) == 0,
        "pending date hidden from agent",
    )
    d = reviewer.post(
        f'/api/deadlines/{d["id"]}/approve',
        {"version": d["version"], "note": "TEST核对来文日期"},
    )
    changed = new_deadline("TEST延期后答复", previous=d)
    check(
        agent.call(f"/api/matters/{mid}")["deadlines"][0]["id"] == d["id"],
        "old version remains active while change pending",
    )
    new_deadline(previous=d, expected=400)
    changed = reviewer.post(
        f'/api/deadlines/{changed["id"]}/approve',
        {"version": changed["version"], "note": "TEST核准改期"},
    )
    old = next(
        x
        for x in manager.call(f"/api/matters/{mid}")["deadlines"]
        if x["id"] == d["id"]
    )
    check(old["state"] == "SUPERSEDED", "prior deadline is retained and superseded")
    agent.post(
        f'/api/deadlines/{old["id"]}/complete',
        {"version": old["version"], "completionFileId": shared["id"], "note": "TEST"},
        expected=400,
    )
    # 邮件仅发往本机Mailpit，所有接收地址为example.invalid。
    setting = next(
        x for x in admin.call("/api/admin/settings") if x["code"] == "email_enabled"
    )
    admin.call(
        f'/api/admin/settings/{setting["id"]}',
        "PUT",
        {"version": setting["version"], "parameterValue": "true"},
    )
    first = admin.post("/api/notifications/run", {})
    check(first["created"] == 4, "owner and agent each get in-app and email records")
    second = admin.post("/api/notifications/run", {})
    check(
        second["created"] == 0 and second["sent"] == 0,
        "same-day version reminder dedupe",
    )
    outbox = admin.call("/api/notifications?admin=true")
    email_rows = [x for x in outbox["items"] if x["channel"] == "EMAIL"]
    if args.mailpit_url:
        check(
            first["sent"] == 2 and all(x["state"] == "SENT" for x in email_rows),
            "real SMTP accepted both mails",
        )
        with urllib.request.urlopen(
            args.mailpit_url.rstrip("/") + "/api/v1/messages", timeout=20
        ) as response:
            mailbox = json.load(response)
        check(mailbox["total"] == 2, "Mailpit captured two messages")
        with urllib.request.urlopen(
            args.mailpit_url.rstrip("/")
            + "/api/v1/message/"
            + mailbox["messages"][0]["ID"],
            timeout=20,
        ) as response:
            captured = json.load(response)
        check(
            "TEST-IP-001" in captured["Text"] and "TEST延期后答复" in captured["Text"],
            "SMTP body has correct case and deadline",
        )
    else:
        check(
            first["sent"] == 0 and all(x["state"] == "SKIPPED" for x in email_rows),
            "unconfigured SMTP does not claim delivery",
        )
    mine = agent.call("/api/notifications")["items"]
    inapp = next(x for x in mine if x["channel"] == "INAPP")
    agent.post(f'/api/notifications/{inapp["id"]}/read', {"version": inapp["version"]})
    receipt = upload(agent, "TEST-agent-receipt.pdf")
    changed = agent.post(
        f'/api/deadlines/{changed["id"]}/complete',
        {
            "version": changed["version"],
            "completionFileId": receipt["id"],
            "note": "TEST代理提交办理回执",
        },
    )
    check(changed["state"] == "DONE_PENDING", "agent cannot finalize own delivery")
    agent.post(
        f'/api/deadlines/{changed["id"]}/accept',
        {"version": changed["version"], "note": "TEST"},
        expected=403,
    )
    changed = reviewer.post(
        f'/api/deadlines/{changed["id"]}/return',
        {"version": changed["version"], "note": "TEST回执补充说明"},
    )
    changed = agent.post(
        f'/api/deadlines/{changed["id"]}/complete',
        {
            "version": changed["version"],
            "completionFileId": receipt["id"],
            "note": "TEST已补充",
        },
    )
    changed = reviewer.post(
        f'/api/deadlines/{changed["id"]}/accept',
        {"version": changed["version"], "note": "TEST核实办结"},
    )
    # 财务申请与部分付款、待审预留、完整冲销。
    fee = writer.post(
        f"/api/matters/{mid}/fees",
        {
            "categoryId": 4,
            "reference": "TEST-BILL-001",
            "payee": "TEST代理机构",
            "amountCents": "100029",
            "description": "TEST验收费用1000.29元",
            "sourceFileId": proof["id"],
        },
    )
    writer.post(f'/api/fees/{fee["id"]}/submit', {"version": fee["version"]})
    fee = next(
        x for x in checker.call(f"/api/matters/{mid}")["fees"] if x["id"] == fee["id"]
    )
    fee = checker.post(
        f'/api/fees/{fee["id"]}/approve',
        {"version": fee["version"], "note": "TEST核准费用"},
    )

    def pay(ref, amount, reverse=None, expected=200):
        return writer.post(
            f'/api/fees/{fee["id"]}/payments',
            {
                "reference": ref,
                "amountCents": amount,
                "paidOn": str(today),
                "proofFileId": proof["id"],
                "note": "TEST线下凭据核销",
                "reverseOfId": reverse,
            },
            expected,
        )

    p1 = pay("TEST-PAY-001", "60000")
    pay("TEST-OVER", "40030", expected=400)
    p1 = checker.post(
        f'/api/payments/{p1["id"]}/approve',
        {"version": p1["version"], "note": "TEST核实支付"},
    )
    p2 = pay("TEST-PAY-002", "40029")
    p2 = checker.post(
        f'/api/payments/{p2["id"]}/approve',
        {"version": p2["version"], "note": "TEST核实尾款"},
    )
    check(
        checker.call("/api/reports")["paid"] == "100029", "exact partial sums equal fee"
    )
    rev = pay("TEST-REV-001", "0", p1["id"])
    pay("TEST-PENDING-OVER", "1", expected=400)
    rev = checker.post(
        f'/api/payments/{rev["id"]}/approve',
        {"version": rev["version"], "note": "TEST独立批准完整冲销"},
    )
    pay("TEST-REV-DUP", "0", p1["id"], expected=400)
    check(checker.call("/api/reports")["paid"] == "40029", "append-only full reversal")
    finalpay = pay("TEST-PAY-003", "60000")
    checker.post(
        f'/api/payments/{finalpay["id"]}/approve',
        {"version": finalpay["version"], "note": "TEST正确凭据重新核销"},
    )
    # 结案、冻结写入、独立重开。
    m = manager.call(f"/api/matters/{mid}")["matter"]
    m = manager.post(
        f"/api/matters/{mid}/state/close",
        {"version": m["version"], "note": "TEST全部事项已核清"},
    )
    manager.post(
        f"/api/matters/{mid}/state/approve",
        {"version": m["version"], "note": "TEST自审"},
        expected=403,
    )
    m = reviewer.post(
        f"/api/matters/{mid}/state/approve",
        {"version": m["version"], "note": "TEST独立结案"},
    )
    upload(manager, expected=400)
    m = manager.post(
        f"/api/matters/{mid}/state/reopen",
        {"version": m["version"], "note": "TEST新增后续事项"},
    )
    m = reviewer.post(
        f"/api/matters/{mid}/state/approve",
        {"version": m["version"], "note": "TEST独立批准重开"},
    )
    # 原代理撤销后不能读文件/提醒，也不能继续交付；新代理无内部资料。
    m = manager.post(
        f"/api/matters/{mid}/assign",
        {
            "version": m["version"],
            "ownerId": users["owner"]["id"],
            "agentId": users["agent2"]["id"],
            "note": "TEST代理交接",
        },
    )
    agent.call(f'/api/files/{shared["id"]}', expected=404)
    agent.call(f"/api/matters/{mid}", expected=404)
    check(
        len(agent.call("/api/notifications")["items"]) == 0,
        "revoked access removes old reminder content",
    )
    agent2.call(f'/api/files/{internal["id"]}', expected=404)
    nextd = new_deadline("TEST新代理后续办理", responsible=users["agent2"]["id"])
    nextd = reviewer.post(
        f'/api/deadlines/{nextd["id"]}/approve',
        {"version": nextd["version"], "note": "TEST交接后独立确认"},
    )
    check(owner.call("/api/reports")["overdue"] == 0, "future date not overdue")
    csv = manager.call("/api/export", raw=True)
    check(b"TEST-IP-001" in csv, "actual CSV export")
    check(
        b"TEST-IP-001" not in outside.call("/api/export", raw=True),
        "CSV respects department isolation",
    )
    check(
        hashlib.sha256(admin.call(f'/api/files/{proof["id"]}', raw=True)).hexdigest()
        == proof["digest"],
        "large proof SHA retained",
    )
    # 保留可供截图的三种权利类型提案，不编造真实商业数据。
    for kind, title in [
        ("TRADEMARK", "TEST商标标识申请"),
        ("COPYRIGHT", "TEST软件著作权登记"),
    ]:
        owner.post(
            "/api/proposals",
            {
                "title": title,
                "rightType": kind,
                "summary": "TEST合成资料，仅用于流程验收。",
            },
        )
    private = {
        "testPassword": pw,
        "users": users,
        "matterId": mid,
        "proofId": proof["id"],
        "proofSHA": proof["digest"],
        "assertions": checks,
        "mailpitVerified": bool(args.mailpit_url),
    }
    fd = os.open(state, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(private, f, ensure_ascii=False)
    print(
        json.dumps(
            {
                "result": "passed",
                "assertions": checks,
                "matterId": mid,
                "realSMTP": bool(args.mailpit_url),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()

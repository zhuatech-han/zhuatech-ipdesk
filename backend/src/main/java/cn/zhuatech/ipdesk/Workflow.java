// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 企业提案、案件授权、人工期限版本及费用凭据的完整业务门禁；不作法律判断或实际支付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Workflow {
  final Store db;
  final Access access;
  final Files files;
  final Clock clock;

  public Workflow(Store db, Access access, Files files, Clock clock) {
    this.db = db;
    this.access = access;
    this.files = files;
    this.clock = clock;
  }

  static final Set<String> LIVE = Set.of("PENDING", "OPEN", "DONE_PENDING", "CANCEL_PENDING");

  /** 仅接受三类受支持权利，国际/地区字段由专业人员维护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String right(Object v) {
    String s = Rules.text(v, 20);
    Rules.check(Set.of("PATENT", "TRADEMARK", "COPYRIGHT").contains(s), "权利类型不支持");
    return s;
  }

  Proposal proposal(Long id) {
    var p = db.get(Proposal.class, id);
    if (!access.proposalVisible(p)) throw new Problem(404, "提案不可访问");
    return p;
  }

  Matter active(Long id) {
    var m = access.matter(id);
    Rules.state(m.state, "ACTIVE");
    return m;
  }

  void choice(Long id, String group) {
    var e = db.get(Entry.class, id);
    Rules.check(e.enabled && e.groupCode.equals(group), "分类已停用或类型不正确");
  }

  void version(Row r, Map<String, Object> b) {
    Rules.version(r, b.get("version"));
  }

  String note(Map<String, Object> b) {
    return Rules.text(b.get("note"), 1600);
  }

  void log(String a, Row r, Long matter, String n) {
    db.flush();
    access.audit(a, r.id, matter, n);
  }

  /** 提案按同一数据范围返回，代理不读取企业技术提案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Proposal> proposals() {
    access.internal("proposals");
    return db.all(Proposal.class).stream().filter(access::proposalVisible).toList().reversed();
  }

  /** 本人草稿或驳回提案可编辑，提交后冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Proposal saveProposal(Long id, Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("proposals");
    var p = id == null ? new Proposal() : proposal(id);
    if (id != null) {
      version(p, b);
      Rules.check(p.creatorId.equals(a.id), "仅提案人可修改");
      Rules.state(p.state, "DRAFT", "REJECTED");
    } else {
      p.creatorId = a.id;
      p.departmentId = a.departmentId;
      p.createdAt = clock.instant();
      db.save(p);
    }
    p.title = Rules.text(b.get("title"), 240);
    p.rightType = right(b.get("rightType"));
    p.summary = Rules.text(b.get("summary"), 6000);
    p.state = "DRAFT";
    p.submitterId = null;
    p.reviewerId = null;
    p.reviewedAt = null;
    p.reviewNote = "";
    log("PROPOSAL_SAVE", p, null, "");
    return p;
  }

  /** 草稿删除保留审计，已提交记录不物理删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteProposal(Long id, Object v) {
    db.writeLock();
    var a = access.internal("proposals");
    var p = proposal(id);
    Rules.version(p, v);
    Rules.state(p.state, "DRAFT");
    Rules.check(p.creatorId.equals(a.id), "仅本人草稿可删除");
    access.audit("PROPOSAL_DELETE", p.id, null, p.title);
    db.delete(p);
  }

  /** 提案送审、撤回与独立批准/驳回，复核者不能是创建人或提交人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Proposal proposalAction(Long id, String action, Map<String, Object> b) {
    db.writeLock();
    var p = proposal(id);
    version(p, b);
    var a = access.current();
    switch (action) {
      case "submit" -> {
        access.internal("proposals");
        Rules.check(p.creatorId.equals(a.id), "仅本人送审");
        Rules.state(p.state, "DRAFT", "REJECTED");
        p.state = "PENDING";
        p.submitterId = a.id;
      }
      case "withdraw" -> {
        Rules.check(p.submitterId.equals(a.id), "仅提交人撤回");
        Rules.state(p.state, "PENDING");
        p.state = "DRAFT";
      }
      case "approve", "reject" -> {
        access.internal("proposal.review");
        Rules.state(p.state, "PENDING");
        Rules.separate(p.creatorId, a.id);
        Rules.separate(p.submitterId, a.id);
        p.state = action.equals("approve") ? "APPROVED" : "REJECTED";
        p.reviewerId = a.id;
        p.reviewedAt = clock.instant();
        p.reviewNote = note(b);
      }
      default -> throw new Problem(400, "操作不存在");
    }
    log("PROPOSAL_" + action.toUpperCase(Locale.ROOT), p, null, p.reviewNote);
    return p;
  }

  /** 从已独立批准的提案建立唯一案件，归属继承提案而非调用方输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Matter createMatter(Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("case.write");
    var p = proposal(Rules.id(b.get("proposalId")));
    Rules.state(p.state, "APPROVED");
    Rules.check(db.query(Matter.class, "from Matter where proposalId=?1", p.id).isEmpty(), "提案已立案");
    var m = new Matter();
    m.code = Rules.code(b.get("code"));
    m.title = p.title;
    m.rightType = p.rightType;
    m.departmentId = p.departmentId;
    m.ownerId = p.creatorId;
    m.creatorId = a.id;
    m.proposalId = p.id;
    m.state = "ACTIVE";
    m.createdAt = clock.instant();
    updateCaseFields(m, b);
    db.save(m);
    log("MATTER_CREATE", m, m.id, "");
    return m;
  }

  void updateCaseFields(Matter m, Map<String, Object> b) {
    m.jurisdiction = Rules.text(b.get("jurisdiction"), 80);
    m.applicant = Rules.text(b.get("applicant"), 240);
    m.applicationNo = Rules.optional(b.get("applicationNo"), 120);
    m.filedOn =
        b.get("filedOn") == null || b.get("filedOn").toString().isBlank()
            ? null
            : Rules.date(b.get("filedOn"));
    Rules.check(m.filedOn == null || !m.filedOn.isAfter(LocalDate.now(clock)), "申请日期不能在未来");
  }

  /** 案件基本信息可修订，不改写稳定代码和提案归属。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Matter updateMatter(Long id, Map<String, Object> b) {
    db.writeLock();
    access.internal("case.write");
    var m = active(id);
    version(m, b);
    m.title = Rules.text(b.get("title"), 240);
    updateCaseFields(m, b);
    log("MATTER_UPDATE", m, id, "");
    return m;
  }

  /** 显式授权单个代理账号；撤换立即影响列表、文件、任务与后续提醒。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Matter assign(Long id, Map<String, Object> b) {
    db.writeLock();
    access.internal("case.write");
    var m = active(id);
    version(m, b);
    var owner = db.get(Account.class, Rules.id(b.get("ownerId")));
    Rules.check(
        access.valid(owner) && !access.external(owner) && owner.departmentId.equals(m.departmentId),
        "负责人须为同部门启用内部账号");
    Long agent = Rules.nullableId(b.get("agentId"));
    if (agent != null) {
      var u = db.get(Account.class, agent);
      Rules.check(access.valid(u) && access.external(u), "代理须为启用外部账号");
    }
    m.ownerId = owner.id;
    m.agentId = agent;
    log("MATTER_ASSIGN", m, id, note(b));
    return m;
  }

  /** 审签结案仍检查未办期限、待审费用及未核清付款，重开另经独立批准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Matter caseAction(Long id, String action, Map<String, Object> b) {
    db.writeLock();
    var m = access.matter(id);
    version(m, b);
    var a = access.current();
    switch (action) {
      case "close" -> {
        access.internal("case.write");
        Rules.state(m.state, "ACTIVE");
        Rules.check(blockers(id).isEmpty(), "存在未办结期限、费用或付款");
        m.closeById = a.id;
        m.reviewNote = note(b);
        m.state = "CLOSING";
      }
      case "reopen" -> {
        access.internal("case.write");
        Rules.state(m.state, "CLOSED");
        m.closeById = a.id;
        m.reviewNote = note(b);
        m.state = "REOPENING";
      }
      case "approve", "reject" -> {
        access.internal("case.review");
        Rules.state(m.state, "CLOSING", "REOPENING");
        Rules.separate(m.closeById, a.id);
        boolean close = m.state.equals("CLOSING");
        if (close && action.equals("approve")) Rules.check(blockers(id).isEmpty(), "结案存在未决事项");
        m.state =
            action.equals("approve")
                ? (close ? "CLOSED" : "ACTIVE")
                : (close ? "ACTIVE" : "CLOSED");
        m.reviewNote = note(b);
      }
      default -> throw new Problem(400, "操作不存在");
    }
    log("CASE_" + action.toUpperCase(Locale.ROOT), m, id, m.reviewNote);
    return m;
  }

  /** 查询不可忽略的结案阻断事项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<String> blockers(Long id) {
    List<String> b = new ArrayList<>();
    if (db.query(Deadline.class, "from Deadline where matterId=?1", id).stream()
        .anyMatch(d -> LIVE.contains(d.state))) b.add("未办结期限");
    for (var f : db.query(Fee.class, "from Fee where matterId=?1", id)) {
      if (Set.of("DRAFT", "PENDING").contains(f.state)) b.add("未审费用 #" + f.id);
      if (f.state.equals("APPROVED") && paid(f.id) != f.amountCents) b.add("未核清费用 #" + f.id);
      if (db.query(Payment.class, "from Payment where feeId=?1 and state='PENDING'", f.id).size()
          > 0) b.add("待审付款 #" + f.id);
    }
    return b;
  }

  /** 同一数据范围内的搜索、状态筛选、分页和稳定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> list(String q, String state, int page, String sort) {
    Rules.check(page >= 1 && page <= 10000, "页码错误");
    String query = Rules.optional(q, 240).toLowerCase(Locale.ROOT);
    var rows =
        access.matters().stream()
            .filter(m -> state.isBlank() || m.state.equals(state))
            .filter(
                m ->
                    (m.code + " " + m.title + " " + m.applicationNo)
                        .toLowerCase(Locale.ROOT)
                        .contains(query))
            .sorted(
                sort.equals("code")
                    ? Comparator.comparing(m -> m.code)
                    : Comparator.comparing((Matter m) -> m.id).reversed())
            .toList();
    int from = Math.min((page - 1) * 20, rows.size());
    return Map.of(
        "items", rows.subList(from, Math.min(from + 20, rows.size())), "total", rows.size());
  }

  boolean taskVisible(Deadline d, Matter m) {
    return !access.external(access.current())
        || (Objects.equals(d.responsibleId, access.current().id)
            && Objects.equals(m.agentId, access.current().id)
            && !Set.of("PENDING", "REJECTED").contains(d.state));
  }

  /** 案件详情仅代理共享文件/本人任务；提案、费用和内部审计不向代理输出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var m = access.matter(id);
    boolean ext = access.external(access.current());
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("matter", m);
    var contacts = new ArrayList<Map<String, Object>>();
    for (Long personId : m.agentId == null ? List.of(m.ownerId) : List.of(m.ownerId, m.agentId)) {
      var person = db.get(Account.class, personId);
      contacts.add(Map.of("id", person.id, "name", person.displayName));
    }
    r.put("contacts", contacts);
    r.put("files", files.metadata(id));
    r.put(
        "deadlines",
        db.query(Deadline.class, "from Deadline where matterId=?1 order by id desc", id).stream()
            .filter(d -> taskVisible(d, m))
            .toList());
    if (!ext) {
      r.put("proposal", db.get(Proposal.class, m.proposalId));
      r.put("blockers", blockers(id));
      r.put("audit", db.query(Audit.class, "from Audit where matterId=?1 order by id desc", id));
      if (access.has("finance") || access.has("finance.review")) {
        var fees = db.query(Fee.class, "from Fee where matterId=?1 order by id desc", id);
        r.put("fees", fees);
        r.put(
            "payments",
            db.query(
                Payment.class,
                "from Payment p where p.feeId in (select f.id from Fee f where f.matterId=?1) order by p.id desc",
                id));
        r.put(
            "paid",
            fees.stream()
                .map(
                    f ->
                        Map.of(
                            "feeId",
                            f.id,
                            "approved",
                            Long.toString(paid(f.id)),
                            "reserved",
                            Long.toString(reserved(f.id))))
                .toList());
      }
    }
    return r;
  }

  /** 新期限或改期建立待审版本，旧版保持有效至新版本独立批准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Deadline deadline(Long id, Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("deadline.write");
    var m = active(id);
    var d = new Deadline();
    d.matterId = id;
    d.previousId = Rules.nullableId(b.get("previousId"));
    if (d.previousId != null) {
      var old = db.get(Deadline.class, d.previousId);
      Rules.check(old.matterId.equals(id), "期限跨案件");
      Rules.version(old, b.get("previousVersion"));
      Rules.state(old.state, "OPEN");
      Rules.check(
          db.query(Deadline.class, "from Deadline where previousId=?1 and state='PENDING'", old.id)
              .isEmpty(),
          "已有待审改期");
    }
    d.title = Rules.text(b.get("title"), 240);
    d.kindId = Rules.id(b.get("kindId"));
    choice(d.kindId, "DEADLINE");
    d.dueOn = Rules.date(b.get("dueOn"));
    d.responsibleId = Rules.id(b.get("responsibleId"));
    var responsible = db.get(Account.class, d.responsibleId);
    Rules.check(access.visible(responsible, m), "经办人没有当前案件授权");
    d.sourceFileId = Rules.id(b.get("sourceFileId"));
    files.evidence(d.sourceFileId, id, access.external(responsible));
    d.reason = Rules.text(b.get("reason"), 1600);
    d.creatorId = a.id;
    d.state = "PENDING";
    d.createdAt = clock.instant();
    db.save(d);
    log("DEADLINE_CREATE", d, id, d.reason);
    return d;
  }

  void noPendingChange(Deadline d) {
    Rules.check(
        db.query(Deadline.class, "from Deadline where previousId=?1 and state='PENDING'", d.id)
            .isEmpty(),
        "须先处理待审改期");
  }

  /** 期限批准/改期、完成提交、取消与复核分别控制状态和实际经办人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Deadline deadlineAction(Long id, String action, Map<String, Object> b) {
    db.writeLock();
    var d = db.get(Deadline.class, id);
    var m = active(d.matterId);
    var a = access.current();
    if (!taskVisible(d, m) && access.external(a)) throw new Problem(404, "任务不可访问");
    version(d, b);
    switch (action) {
      case "approve", "reject" -> {
        access.internal("deadline.review");
        Rules.state(d.state, "PENDING");
        Rules.separate(d.creatorId, a.id);
        if (action.equals("approve")) {
          var resp = db.get(Account.class, d.responsibleId);
          Rules.check(access.visible(resp, m), "经办人授权已变化");
          files.evidence(d.sourceFileId, m.id, access.external(resp));
          if (d.previousId != null) {
            var old = db.get(Deadline.class, d.previousId);
            Rules.state(old.state, "OPEN");
            old.state = "SUPERSEDED";
          }
          d.state = "OPEN";
        } else d.state = "REJECTED";
        d.reviewerId = a.id;
        d.reviewNote = note(b);
      }
      case "complete" -> {
        access.require("delivery");
        Rules.state(d.state, "OPEN");
        noPendingChange(d);
        if (access.external(a)) Rules.check(d.responsibleId.equals(a.id), "仅指派代理提交");
        d.completionFileId = Rules.id(b.get("completionFileId"));
        files.evidence(
            d.completionFileId, m.id, access.external(db.get(Account.class, d.responsibleId)));
        d.completionById = a.id;
        d.completionNote = note(b);
        d.state = "DONE_PENDING";
      }
      case "accept", "return" -> {
        access.internal("deadline.review");
        Rules.state(d.state, "DONE_PENDING");
        Rules.separate(d.completionById, a.id);
        d.reviewNote = note(b);
        d.reviewerId = a.id;
        d.state = action.equals("accept") ? "DONE" : "OPEN";
      }
      case "cancel" -> {
        access.internal("deadline.write");
        Rules.state(d.state, "OPEN");
        noPendingChange(d);
        d.completionById = a.id;
        d.completionNote = note(b);
        d.state = "CANCEL_PENDING";
      }
      case "cancel-approve", "cancel-reject" -> {
        access.internal("deadline.review");
        Rules.state(d.state, "CANCEL_PENDING");
        Rules.separate(d.completionById, a.id);
        d.reviewerId = a.id;
        d.reviewNote = note(b);
        d.state = action.equals("cancel-approve") ? "CANCELLED" : "OPEN";
      }
      default -> throw new Problem(400, "操作不存在");
    }
    log("DEADLINE_" + action.toUpperCase(Locale.ROOT), d, m.id, d.reviewNote);
    return d;
  }

  Fee fee(Long id) {
    var f = db.get(Fee.class, id);
    access.matter(f.matterId);
    return f;
  }

  /** 内部财务登记费用草稿，发票/账单引用以案件+编号去重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Fee saveFee(Long matterId, Long id, Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("finance");
    var m = active(matterId);
    var f = id == null ? new Fee() : fee(id);
    if (id != null) {
      version(f, b);
      Rules.check(f.matterId.equals(m.id) && f.creatorId.equals(a.id), "只能编辑本人同案费用");
      Rules.state(f.state, "DRAFT", "REJECTED");
    } else {
      f.matterId = m.id;
      f.creatorId = a.id;
      f.createdAt = clock.instant();
    }
    f.categoryId = Rules.id(b.get("categoryId"));
    choice(f.categoryId, "FEE");
    f.reference = Rules.voucher(b.get("reference"));
    f.payee = Rules.text(b.get("payee"), 240);
    f.amountCents = Rules.cents(b.get("amountCents"), false);
    f.description = Rules.text(b.get("description"), 1600);
    f.sourceFileId = Rules.id(b.get("sourceFileId"));
    files.evidence(f.sourceFileId, m.id, false);
    f.state = "DRAFT";
    f.reviewNote = "";
    f.reviewerId = null;
    f.submitterId = null;
    if (id == null) db.save(f);
    log("FEE_SAVE", f, m.id, "");
    return f;
  }

  /** 删除本人未提交费用，保留独立审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteFee(Long id, Object v) {
    db.writeLock();
    var a = access.internal("finance");
    var f = fee(id);
    active(f.matterId);
    Rules.version(f, v);
    Rules.state(f.state, "DRAFT");
    Rules.check(f.creatorId.equals(a.id), "只能删除本人草稿");
    access.audit("FEE_DELETE", f.id, f.matterId, "");
    db.delete(f);
  }

  /** 费用提交和批准分别控制岗位与经办复核分离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Fee feeAction(Long id, String action, Map<String, Object> b) {
    db.writeLock();
    var f = fee(id);
    active(f.matterId);
    version(f, b);
    var a = access.current();
    if (action.equals("submit")) {
      access.internal("finance");
      Rules.check(f.creatorId.equals(a.id), "仅本人提交");
      Rules.state(f.state, "DRAFT", "REJECTED");
      f.state = "PENDING";
      f.submitterId = a.id;
    } else if (Set.of("approve", "reject").contains(action)) {
      access.internal("finance.review");
      Rules.state(f.state, "PENDING");
      Rules.separate(f.creatorId, a.id);
      Rules.separate(f.submitterId, a.id);
      f.reviewerId = a.id;
      f.reviewNote = note(b);
      f.state = action.equals("approve") ? "APPROVED" : "REJECTED";
    } else throw new Problem(400, "操作不存在");
    log("FEE_" + action.toUpperCase(Locale.ROOT), f, f.matterId, f.reviewNote);
    return f;
  }

  /** 已批准净核销，负数仅来自完整冲销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public long paid(Long id) {
    return db.query(Payment.class, "from Payment where feeId=?1 and state='APPROVED'", id).stream()
        .mapToLong(p -> p.amountCents)
        .reduce(0, Math::addExact);
  }

  /** 待审正向付款保留额度，待审冲销不提前释放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public long reserved(Long id) {
    return db.query(Payment.class, "from Payment where feeId=?1 and state='PENDING'", id).stream()
        .filter(p -> p.amountCents > 0)
        .mapToLong(p -> p.amountCents)
        .reduce(0, Math::addExact);
  }

  /** 外部支付后登记凭据，支持分次核销；本方法不发起资金交易。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Payment payment(Long id, Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("finance");
    var f = fee(id);
    active(f.matterId);
    Rules.state(f.state, "APPROVED");
    var p = new Payment();
    p.feeId = f.id;
    p.reverseOfId = Rules.nullableId(b.get("reverseOfId"));
    if (p.reverseOfId != null) {
      var old = db.get(Payment.class, p.reverseOfId);
      Rules.check(
          old.feeId.equals(id) && old.reverseOfId == null && old.state.equals("APPROVED"),
          "只可冲销本费用已核实的正向凭据");
      Rules.check(
          db.query(
                  Payment.class,
                  "from Payment where reverseOfId=?1 and state in ('PENDING','APPROVED')",
                  old.id)
              .isEmpty(),
          "已有待审或已批准冲销");
      p.amountCents = -old.amountCents;
    } else {
      p.amountCents = Rules.cents(b.get("amountCents"), false);
      Rules.check(
          Math.addExact(Math.addExact(paid(id), reserved(id)), p.amountCents) <= f.amountCents,
          "超过费用可核销余额（含待审占用）");
    }
    p.reference = Rules.voucher(b.get("reference"));
    p.paidOn = Rules.date(b.get("paidOn"));
    Rules.check(!p.paidOn.isAfter(LocalDate.now(clock)), "凭据日期不能在未来");
    p.proofFileId = Rules.id(b.get("proofFileId"));
    files.evidence(p.proofFileId, f.matterId, false);
    p.note = note(b);
    p.creatorId = a.id;
    p.state = "PENDING";
    p.createdAt = clock.instant();
    db.save(p);
    log("PAYMENT_CREATE", p, f.matterId, p.note);
    return p;
  }

  /** 凭据独立复核，批准时重算余额并阻止双重冲销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Payment paymentAction(Long id, String action, Map<String, Object> b) {
    db.writeLock();
    var a = access.internal("finance.review");
    var p = db.get(Payment.class, id);
    var f = fee(p.feeId);
    active(f.matterId);
    version(p, b);
    Rules.state(p.state, "PENDING");
    Rules.separate(p.creatorId, a.id);
    Rules.check(Set.of("approve", "reject").contains(action), "操作不存在");
    if (action.equals("approve")) {
      if (p.reverseOfId != null) {
        Rules.check(
            db.query(
                    Payment.class,
                    "from Payment where reverseOfId=?1 and state='APPROVED'",
                    p.reverseOfId)
                .isEmpty(),
            "原凭据已冲销");
        Rules.check(paid(f.id) + p.amountCents >= 0, "核销金额不足");
      } else Rules.check(paid(f.id) + reserved(f.id) <= f.amountCents, "超出费用上限");
      p.state = "APPROVED";
    } else p.state = "REJECTED";
    p.reviewerId = a.id;
    p.reviewNote = note(b);
    log("PAYMENT_" + action.toUpperCase(Locale.ROOT), p, f.matterId, p.reviewNote);
    return p;
  }

  /** 统一范围统计待办及费用，不向没有财务权限的账号返回金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    var matters = access.matters();
    var ids = matters.stream().map(m -> m.id).collect(java.util.stream.Collectors.toSet());
    var today = LocalDate.now(clock);
    int warning =
        Integer.parseInt(
            db.query(Setting.class, "from Setting where code='warning_days'")
                .getFirst()
                .parameterValue);
    var deadlines =
        db.all(Deadline.class).stream()
            .filter(
                d ->
                    ids.contains(d.matterId)
                        && Set.of("OPEN", "DONE_PENDING", "CANCEL_PENDING").contains(d.state))
            .filter(d -> taskVisible(d, db.get(Matter.class, d.matterId)))
            .sorted(Comparator.comparing((Deadline d) -> d.dueOn).thenComparing(d -> d.id))
            .toList();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("matters", matters.size());
    r.put("active", matters.stream().filter(m -> m.state.equals("ACTIVE")).count());
    r.put("overdue", deadlines.stream().filter(d -> d.dueOn.isBefore(today)).count());
    r.put(
        "due",
        deadlines.stream()
            .filter(d -> !d.dueOn.isAfter(today.plusDays(warning)))
            .limit(100)
            .toList());
    r.put("today", today);
    if (!access.external(access.current())
        && (access.has("finance") || access.has("finance.review"))) {
      var fees =
          db.all(Fee.class).stream()
              .filter(f -> ids.contains(f.matterId) && f.state.equals("APPROVED"))
              .toList();
      long total = fees.stream().mapToLong(f -> f.amountCents).reduce(0, Math::addExact),
          net = fees.stream().mapToLong(f -> paid(f.id)).reduce(0, Math::addExact);
      r.put("approvedFees", Long.toString(total));
      r.put("paid", Long.toString(net));
      r.put("unpaid", Long.toString(total - net));
    }
    return r;
  }

  /** 导出当前权限案件数据，防止表格公式执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export() {
    access.internal("reports");
    var out = new StringBuilder("\ufeff案件编号,标题,权利类型,司法辖区,申请人,申请号,状态\r\n");
    for (var m : access.matters()) {
      out.append(
              String.join(
                  ",",
                  List.of(
                          m.code,
                          m.title,
                          m.rightType,
                          m.jurisdiction,
                          m.applicant,
                          m.applicationNo,
                          m.state)
                      .stream()
                      .map(Rules::csv)
                      .toList()))
          .append("\r\n");
    }
    return out.toString();
  }
}

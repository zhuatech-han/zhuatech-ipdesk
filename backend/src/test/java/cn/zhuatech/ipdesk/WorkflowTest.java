// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** 实际JPA/Flyway、职责分离、代理隔离、期限版本、核销预留与提醒持久化验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ipdeskflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "ADMIN_PASSWORD=TEST-Only-Password123!",
      "ipdesk.scheduler-enabled=false"
    })
@Transactional
class WorkflowTest {
  @Autowired Store db;
  @Autowired Access access;
  @Autowired Catalog catalog;
  @Autowired Workflow flow;
  @Autowired Files files;
  @Autowired Notifications notifications;
  @Autowired Clock clock;
  Long owner, manager, reviewer, writer, checker, agent, agent2, outsider;
  Matter matter;
  Long shared, internal;
  Proposal proposal;

  static Map<String, Object> b(Object... args) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i += 2) m.put(args[i].toString(), args[i + 1]);
    return m;
  }

  void as(Long id) {
    var a = db.get(Account.class, id);
    var auth = UsernamePasswordAuthenticationToken.authenticated(a.username, null, List.of());
    auth.setDetails(a.passwordHash);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  Long user(String name, long role, Long dept) {
    return ((Account)
            catalog.save(
                "accounts",
                null,
                b(
                    "username",
                    name,
                    "displayName",
                    "TEST " + name,
                    "password",
                    "TEST-Only-Password123!",
                    "roleId",
                    role,
                    "departmentId",
                    dept,
                    "enabled",
                    true,
                    "email",
                    name + "@example.invalid",
                    "mailOptIn",
                    true)))
        .id;
  }

  Map<String, Object> action(Row r) {
    return b("version", r.version, "note", "TEST 独立核实");
  }

  @BeforeEach
  void setup() throws Exception {
    as(1L);
    owner = user("test_owner", 2, 1L);
    manager = user("test_manager", 3, 1L);
    reviewer = user("test_review", 4, 1L);
    writer = user("test_finance", 5, 1L);
    checker = user("test_check", 6, 1L);
    var agency =
        (Department)
            catalog.save(
                "departments",
                null,
                b("code", "TEST-AGENCY", "name", "TEST代理机构", "kind", "AGENCY", "enabled", true));
    agent = user("test_agent", 7, agency.id);
    agent2 = user("test_agent2", 7, agency.id);
    var dept =
        (Department)
            catalog.save(
                "departments",
                null,
                b("code", "TEST-OTHER", "name", "TEST其他部门", "kind", "INTERNAL", "enabled", true));
    outsider = user("test_other", 3, dept.id);
    as(owner);
    proposal =
        flow.saveProposal(
            null, b("title", "TEST技术方案", "rightType", "PATENT", "summary", "TEST公开合成技术提案"));
    flow.proposalAction(proposal.id, "submit", action(proposal));
    as(reviewer);
    flow.proposalAction(proposal.id, "approve", action(proposal));
    as(manager);
    matter =
        flow.createMatter(
            b(
                "proposalId",
                proposal.id,
                "code",
                "TEST-IP-001",
                "jurisdiction",
                "中国",
                "applicant",
                "TEST企业"));
    flow.assign(
        matter.id,
        b("version", matter.version, "ownerId", owner, "agentId", agent, "note", "TEST指派"));
    shared =
        (Long)
            files.upload(matter.id, "TEST-notice.pdf", RulesTest.pdf(), true, "TEST共享来文").get("id");
    internal =
        (Long)
            files
                .upload(matter.id, "TEST-internal.pdf", RulesTest.pdf(), false, "TEST内部资料")
                .get("id");
  }

  Deadline deadline() {
    as(manager);
    return flow.deadline(
        matter.id,
        b(
            "title",
            "TEST来文答复",
            "kindId",
            1,
            "dueOn",
            LocalDate.now(clock).plusDays(2).toString(),
            "responsibleId",
            agent,
            "sourceFileId",
            shared,
            "reason",
            "TEST按合成来文登记"));
  }

  Deadline openDeadline() {
    var d = deadline();
    as(reviewer);
    flow.deadlineAction(d.id, "approve", action(d));
    return d;
  }

  Fee fee() {
    as(writer);
    var f =
        flow.saveFee(
            matter.id,
            null,
            b(
                "categoryId",
                4,
                "reference",
                "TEST-BILL-1",
                "payee",
                "TEST代理机构",
                "amountCents",
                "100029",
                "description",
                "TEST官费与服务费",
                "sourceFileId",
                internal));
    flow.feeAction(f.id, "submit", action(f));
    as(checker);
    flow.feeAction(f.id, "approve", action(f));
    return f;
  }

  Payment pay(Fee f, String reference, String amount) {
    as(writer);
    return flow.payment(
        f.id,
        b(
            "reference",
            reference,
            "amountCents",
            amount,
            "paidOn",
            LocalDate.now(clock).toString(),
            "proofFileId",
            internal,
            "note",
            "TEST线下支付凭据"));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void submitterCannotReviewOwnProposal() {
    as(1L);
    var p =
        flow.saveProposal(null, b("title", "TEST自审", "rightType", "TRADEMARK", "summary", "TEST"));
    flow.proposalAction(p.id, "submit", action(p));
    assertThrows(Problem.class, () -> flow.proposalAction(p.id, "approve", action(p)));
  }

  @Test
  void proposalCreatesOnlyOneCase() {
    as(manager);
    assertThrows(
        Problem.class,
        () ->
            flow.createMatter(
                b(
                    "proposalId",
                    proposal.id,
                    "code",
                    "TEST-DUP",
                    "jurisdiction",
                    "中国",
                    "applicant",
                    "TEST")));
  }

  @Test
  void externalSeesOnlyExplicitCaseAndSharedFiles() {
    as(agent);
    var d = flow.detail(matter.id);
    assertFalse(d.containsKey("proposal"));
    assertFalse(d.containsKey("fees"));
    assertEquals(1, files.metadata(matter.id).size());
    assertThrows(Problem.class, () -> files.file(internal));
    assertThrows(Problem.class, () -> flow.proposals());
    as(agent2);
    assertThrows(Problem.class, () -> flow.detail(matter.id));
    as(outsider);
    assertThrows(Problem.class, () -> flow.detail(matter.id));
  }

  @Test
  void revocationImmediatelyProtectsDownload() {
    as(manager);
    flow.assign(
        matter.id,
        b("version", matter.version, "ownerId", owner, "agentId", null, "note", "TEST撤销代理"));
    as(agent);
    assertThrows(Problem.class, () -> files.file(shared));
    assertEquals(0, access.matters().size());
  }

  @Test
  void staleWritesRejected() {
    as(manager);
    assertThrows(
        Problem.class,
        () ->
            flow.assign(
                matter.id,
                b(
                    "version",
                    matter.version + 1,
                    "ownerId",
                    owner,
                    "agentId",
                    agent,
                    "note",
                    "TEST")));
  }

  @Test
  void pendingDateIsHiddenAndCannotSelfApprove() {
    var d = deadline();
    as(agent);
    assertEquals(0, ((List<?>) flow.detail(matter.id).get("deadlines")).size());
    as(1L);
    var own =
        flow.deadline(
            matter.id,
            b(
                "title",
                "TEST自审期限",
                "kindId",
                1,
                "dueOn",
                LocalDate.now(clock).toString(),
                "responsibleId",
                owner,
                "sourceFileId",
                internal,
                "reason",
                "TEST"));
    assertThrows(Problem.class, () -> flow.deadlineAction(own.id, "approve", action(own)));
    assertEquals("PENDING", d.state);
  }

  @Test
  void replacingDeadlineKeepsOldOpenUntilApproval() {
    var d = openDeadline();
    as(manager);
    var next =
        flow.deadline(
            matter.id,
            b(
                "previousId",
                d.id,
                "previousVersion",
                d.version,
                "title",
                d.title,
                "kindId",
                1,
                "dueOn",
                d.dueOn.plusDays(5).toString(),
                "responsibleId",
                agent,
                "sourceFileId",
                shared,
                "reason",
                "TEST来文改期"));
    assertEquals("OPEN", d.state);
    as(agent);
    assertThrows(
        Problem.class,
        () ->
            flow.deadlineAction(
                d.id,
                "complete",
                b("version", d.version, "completionFileId", shared, "note", "TEST")));
    as(reviewer);
    flow.deadlineAction(next.id, "approve", action(next));
    assertEquals("SUPERSEDED", d.state);
    assertEquals("OPEN", next.state);
  }

  @Test
  void agentCannotUseInternalSource() {
    as(manager);
    assertThrows(
        Problem.class,
        () ->
            flow.deadline(
                matter.id,
                b(
                    "title",
                    "TEST",
                    "kindId",
                    1,
                    "dueOn",
                    LocalDate.now(clock).toString(),
                    "responsibleId",
                    agent,
                    "sourceFileId",
                    internal,
                    "reason",
                    "TEST")));
  }

  @Test
  void completionNeedsIndependentReviewAndOriginalFileSurvives() {
    var d = openDeadline();
    as(agent);
    flow.deadlineAction(
        d.id, "complete", b("version", d.version, "completionFileId", shared, "note", "TEST交付"));
    assertEquals("DONE_PENDING", d.state);
    as(reviewer);
    flow.deadlineAction(d.id, "accept", action(d));
    assertEquals("DONE", d.state);
    assertTrue(files.file(shared).content.length > 0);
  }

  @Test
  void cancellationRemainsOpenRiskUntilReviewed() {
    var d = openDeadline();
    as(manager);
    flow.deadlineAction(d.id, "cancel", action(d));
    assertEquals("CANCEL_PENDING", d.state);
    assertTrue(flow.blockers(matter.id).contains("未办结期限"));
    as(reviewer);
    flow.deadlineAction(d.id, "cancel-reject", action(d));
    assertEquals("OPEN", d.state);
  }

  @Test
  void paymentsReserveAndRejectOverage() {
    var f = fee();
    var p = pay(f, "TEST-PAY-1", "60000");
    as(writer);
    assertThrows(Problem.class, () -> pay(f, "TEST-PAY-2", "40030"));
    assertEquals(60000, flow.reserved(f.id));
    as(checker);
    flow.paymentAction(p.id, "approve", action(p));
    assertEquals(60000, flow.paid(f.id));
    var p2 = pay(f, "TEST-PAY-2", "40029");
    as(checker);
    flow.paymentAction(p2.id, "approve", action(p2));
    assertEquals(100029, flow.paid(f.id));
  }

  @Test
  void reversalDoesNotReleaseBeforeApproval() {
    var f = fee();
    var p = pay(f, "TEST-PAY-1", "100029");
    as(checker);
    flow.paymentAction(p.id, "approve", action(p));
    as(writer);
    var rev =
        flow.payment(
            f.id,
            b(
                "reverseOfId",
                p.id,
                "reference",
                "TEST-REV-1",
                "paidOn",
                LocalDate.now(clock).toString(),
                "proofFileId",
                internal,
                "note",
                "TEST完整冲销"));
    assertThrows(Problem.class, () -> pay(f, "TEST-OVER", "1"));
    as(checker);
    flow.paymentAction(rev.id, "approve", action(rev));
    assertEquals(0, flow.paid(f.id));
    assertThrows(
        Problem.class,
        () -> {
          as(writer);
          flow.payment(f.id, b("reverseOfId", p.id, "reference", "TEST-REV-2"));
        });
  }

  @Test
  void closeRequiresClearedItemsAndIndependentReviewer() {
    var d = openDeadline();
    as(manager);
    assertThrows(Problem.class, () -> flow.caseAction(matter.id, "close", action(matter)));
    flow.deadlineAction(d.id, "cancel", action(d));
    as(reviewer);
    flow.deadlineAction(d.id, "cancel-approve", action(d));
    as(manager);
    flow.caseAction(matter.id, "close", action(matter));
    as(reviewer);
    flow.caseAction(matter.id, "approve", action(matter));
    assertEquals("CLOSED", matter.state);
    as(manager);
    assertThrows(Problem.class, () -> files.upload(matter.id, "x.pdf", RulesTest.pdf(), false, ""));
    flow.caseAction(matter.id, "reopen", action(matter));
    as(reviewer);
    flow.caseAction(matter.id, "approve", action(matter));
    assertEquals("ACTIVE", matter.state);
  }

  @Test
  void reminderDedupeAndRevocation() {
    openDeadline();
    assertEquals(2, notifications.enqueue());
    assertEquals(0, notifications.enqueue());
    as(agent);
    assertEquals(1, ((List<?>) ((Map<?, ?>) notifications.list(false)).get("items")).size());
    as(manager);
    flow.assign(
        matter.id,
        b("version", matter.version, "ownerId", owner, "agentId", agent2, "note", "TEST变更代理"));
    as(agent);
    assertEquals(0, ((List<?>) ((Map<?, ?>) notifications.list(false)).get("items")).size());
  }

  @Test
  void unconfiguredEmailNeverClaimsDelivery() {
    openDeadline();
    as(1L);
    var setting = db.query(Setting.class, "from Setting where code='email_enabled'").getFirst();
    catalog.save("settings", setting.id, b("version", setting.version, "parameterValue", "true"));
    assertEquals(4, notifications.enqueue());
    assertEquals(0, notifications.dispatch());
    assertTrue(
        db.query(Alert.class, "from Alert where channel='EMAIL'").stream()
            .allMatch(a -> a.state.equals("SKIPPED")));
  }

  @Test
  void roleCannotEscalateAgency() {
    as(1L);
    assertThrows(
        Problem.class,
        () ->
            catalog.save(
                "roles",
                null,
                b(
                    "name",
                    "TEST错误代理权限",
                    "scope",
                    "AGENT",
                    "permissions",
                    List.of("matters", "admin"))));
  }

  @Test
  void csvScopeExcludesOtherDepartment() {
    as(owner);
    assertTrue(flow.export().contains("TEST-IP-001"));
    as(outsider);
    assertFalse(flow.export().contains("TEST-IP-001"));
    as(agent);
    assertThrows(Problem.class, () -> flow.export());
  }
}

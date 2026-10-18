// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 知识产权协同持久化实体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Models {
  /** 身份与乐观并发版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @MappedSuperclass
  public abstract static class Row {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version public long version;
  }

  /** 实例事务协调锁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Guard")
  @Table(name = "write_guard")
  public static class Guard {
    @Id public Long id;
  }

  /** 组织与代理机构。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Department")
  @Table(name = "department")
  public static class Department extends Row {
    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "name", nullable = false, length = 120)
    public String name = "";

    @Column(name = "kind", nullable = false, length = 20)
    public String kind = "";

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 权限与不可变数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Role")
  @Table(name = "access_role")
  public static class Role extends Row {
    @Column(name = "name", nullable = false, length = 120)
    public String name = "";

    @Column(name = "scope", nullable = false, length = 20)
    public String scope = "";

    @Column(name = "permissions", nullable = false, length = 1000)
    public String permissions = "";
  }

  /** 登录账号及本人提醒接收设置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Account")
  @Table(name = "account")
  public static class Account extends Row {
    @Column(name = "username", nullable = false, length = 60)
    public String username = "";

    @Column(name = "display_name", nullable = false, length = 120)
    public String displayName = "";

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash = "";

    @Column(name = "role_id", nullable = false)
    public Long roleId;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "email", nullable = false, length = 254)
    public String email = "";

    @Column(name = "mail_opt_in", nullable = false)
    public boolean mailOptIn = false;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 可停用的业务分类。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Entry")
  @Table(name = "dictionary_entry")
  public static class Entry extends Row {
    @Column(name = "group_code", nullable = false, length = 40)
    public String groupCode = "";

    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 非秘密系统参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Setting")
  @Table(name = "system_setting")
  public static class Setting extends Row {
    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "parameter_value", nullable = false, length = 1000)
    public String parameterValue = "";
  }

  /** 业务菜单与权限入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Menu")
  @Table(name = "nav_menu")
  public static class Menu extends Row {
    @Column(name = "code", nullable = false, length = 40)
    public String code = "";

    @Column(name = "label", nullable = false, length = 120)
    public String label = "";

    @Column(name = "permission", nullable = false, length = 60)
    public String permission = "";

    @Column(name = "position", nullable = false)
    public int position = 0;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;
  }

  /** 企业技术提案与独立评审。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Proposal")
  @Table(name = "disclosure")
  public static class Proposal extends Row {
    @Column(name = "title", nullable = false, length = 240)
    public String title = "";

    @Column(name = "right_type", nullable = false, length = 20)
    public String rightType = "";

    @Column(name = "summary", nullable = false, length = 6000)
    public String summary = "";

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "submitter_id", nullable = true)
    public Long submitterId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "review_note", nullable = false, length = 1600)
    public String reviewNote = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "reviewed_at", nullable = true)
    public Instant reviewedAt;
  }

  /** 已立项知识产权案件及显式代理授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Matter")
  @Table(name = "ip_matter")
  public static class Matter extends Row {
    @Column(name = "code", nullable = false, length = 60)
    public String code = "";

    @Column(name = "title", nullable = false, length = 240)
    public String title = "";

    @Column(name = "right_type", nullable = false, length = 20)
    public String rightType = "";

    @Column(name = "jurisdiction", nullable = false, length = 80)
    public String jurisdiction = "";

    @Column(name = "applicant", nullable = false, length = 240)
    public String applicant = "";

    @Column(name = "application_no", nullable = false, length = 120)
    public String applicationNo = "";

    @Column(name = "filed_on", nullable = true)
    public LocalDate filedOn;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "owner_id", nullable = false)
    public Long ownerId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "agent_id", nullable = true)
    public Long agentId;

    @Column(name = "proposal_id", nullable = false)
    public Long proposalId;

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "close_by_id", nullable = true)
    public Long closeById;

    @Column(name = "review_note", nullable = false, length = 1600)
    public String reviewNote = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 私有文件原字节及显式共享标记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Evidence")
  @Table(name = "evidence_file")
  public static class Evidence extends Row {
    @Column(name = "matter_id", nullable = false)
    public Long matterId;

    @Column(name = "file_name", nullable = false, length = 160)
    public String fileName = "";

    @Column(name = "content_type", nullable = false, length = 80)
    public String contentType = "";

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes = 0;

    @Column(name = "digest", nullable = false, length = 64)
    public String digest = "";

    @Column(name = "version_number", nullable = false)
    public int versionNumber = 0;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "shared", nullable = false)
    public boolean shared = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "note", nullable = false, length = 600)
    public String note = "";

    @JsonIgnore
    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "content", nullable = false, columnDefinition = "longblob")
    public byte[] content = new byte[0];
  }

  /** 人工期限版本、确认与办结门禁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Deadline")
  @Table(name = "case_deadline")
  public static class Deadline extends Row {
    @Column(name = "matter_id", nullable = false)
    public Long matterId;

    @Column(name = "previous_id", nullable = true)
    public Long previousId;

    @Column(name = "title", nullable = false, length = 240)
    public String title = "";

    @Column(name = "kind_id", nullable = false)
    public Long kindId;

    @Column(name = "due_on", nullable = false)
    public LocalDate dueOn;

    @Column(name = "source_file_id", nullable = false)
    public Long sourceFileId;

    @Column(name = "responsible_id", nullable = false)
    public Long responsibleId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "reason", nullable = false, length = 1600)
    public String reason = "";

    @Column(name = "review_note", nullable = false, length = 1600)
    public String reviewNote = "";

    @Column(name = "completion_by_id", nullable = true)
    public Long completionById;

    @Column(name = "completion_file_id", nullable = true)
    public Long completionFileId;

    @Column(name = "completion_note", nullable = false, length = 1600)
    public String completionNote = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 官费与代理费申请及费用上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Fee")
  @Table(name = "case_fee")
  public static class Fee extends Row {
    @Column(name = "matter_id", nullable = false)
    public Long matterId;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @Column(name = "reference", nullable = false, length = 120)
    public String reference = "";

    @Column(name = "payee", nullable = false, length = 240)
    public String payee = "";

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;

    @Column(name = "description", nullable = false, length = 1600)
    public String description = "";

    @Column(name = "source_file_id", nullable = false)
    public Long sourceFileId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "submitter_id", nullable = true)
    public Long submitterId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "review_note", nullable = false, length = 1600)
    public String reviewNote = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 外部支付凭据与完整冲销双人复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Payment")
  @Table(name = "payment_record")
  public static class Payment extends Row {
    @Column(name = "fee_id", nullable = false)
    public Long feeId;

    @Column(name = "reverse_of_id", nullable = true)
    public Long reverseOfId;

    @Column(name = "reference", nullable = false, length = 120)
    public String reference = "";

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "amount_cents", nullable = false)
    public long amountCents = 0;

    @Column(name = "paid_on", nullable = false)
    public LocalDate paidOn;

    @Column(name = "proof_file_id", nullable = false)
    public Long proofFileId;

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "reviewer_id", nullable = true)
    public Long reviewerId;

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";

    @Column(name = "review_note", nullable = false, length = 1600)
    public String reviewNote = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }

  /** 站内提醒与持久化邮件投递记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Alert")
  @Table(name = "notification")
  public static class Alert extends Row {
    @Column(name = "deadline_id", nullable = false)
    public Long deadlineId;

    @Column(name = "recipient_id", nullable = false)
    public Long recipientId;

    @Column(name = "dedupe_key", nullable = false, length = 240)
    public String dedupeKey = "";

    @Column(name = "channel", nullable = false, length = 20)
    public String channel = "";

    @Column(name = "state", nullable = false, length = 30)
    public String state = "";

    @Column(name = "email", nullable = false, length = 254)
    public String email = "";

    @Column(name = "attempts", nullable = false)
    public int attempts = 0;

    @Column(name = "reason", nullable = false, length = 120)
    public String reason = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "claimed_at", nullable = true)
    public Instant claimedAt;

    @Column(name = "sent_at", nullable = true)
    public Instant sentAt;
  }

  /** 只追加的操作与审计记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Audit")
  @Table(name = "audit_event")
  public static class Audit extends Row {
    @Column(name = "actor_id", nullable = false)
    public Long actorId;

    @Column(name = "department_id", nullable = false)
    public Long departmentId;

    @Column(name = "matter_id", nullable = true)
    public Long matterId;

    @Column(name = "action", nullable = false, length = 80)
    public String action = "";

    @Column(name = "object_ref", nullable = false, length = 120)
    public String objectRef = "";

    @Column(name = "note", nullable = false, length = 1600)
    public String note = "";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
  }
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 案件资料分为内部与代理共享；所有版本不覆盖，撤销代理即时影响下载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Files {
  final Store db;
  final Access access;
  final Clock clock;

  public Files(Store db, Access access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 仅投影元数据，不将私有BLOB载入列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> metadata(Long id) {
    access.matter(id);
    boolean ext = access.external(access.current());
    var rows =
        db.query(
            Object[].class,
            "select e.id,e.version,e.fileName,e.versionNumber,e.sizeBytes,e.digest,e.shared,e.creatorId,e.createdAt,e.note from Evidence e where e.matterId=?1 order by e.id",
            id);
    String[] keys = {
      "id",
      "version",
      "fileName",
      "versionNumber",
      "sizeBytes",
      "digest",
      "shared",
      "creatorId",
      "createdAt",
      "note"
    };
    return rows.stream()
        .filter(r -> !ext || Boolean.TRUE.equals(r[6]))
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>();
              for (int i = 0; i < keys.length; i++) m.put(keys[i], r[i]);
              return m;
            })
        .toList();
  }

  /** 真实文件与元数据同事务保存；外部上传自动作为共享交付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> upload(
      Long id, String name, byte[] data, boolean shared, String note) {
    db.writeLock();
    var m = access.matter(id);
    Rules.state(m.state, "ACTIVE");
    var a = access.current();
    if (!access.has("delivery") && !access.has("case.write") && !access.has("finance"))
      throw new Problem(403, "没有资料上传权限");
    boolean ext = access.external(a);
    if (shared && !ext) access.internal("case.write");
    String clean = FileFormat.filename(name), type = FileFormat.validate(clean, data);
    var all =
        db.query(
            Object[].class,
            "select e.fileName,e.versionNumber,e.sizeBytes from Evidence e where e.matterId=?1",
            id);
    Rules.check(all.size() < 100, "每案件最多100个文件版本");
    long sum = all.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();
    int cap =
        Integer.parseInt(
            db.query(Setting.class, "from Setting where code='max_case_mb'")
                .getFirst()
                .parameterValue);
    Rules.check(sum + data.length <= cap * 1024L * 1024, "案件文件容量已满");
    var e = new Evidence();
    e.matterId = id;
    e.fileName = clean;
    e.versionNumber =
        all.stream()
                .filter(r -> r[0].equals(clean))
                .mapToInt(r -> ((Number) r[1]).intValue())
                .max()
                .orElse(0)
            + 1;
    e.contentType = type;
    e.sizeBytes = data.length;
    e.content = data;
    e.digest = Rules.digest(data);
    e.creatorId = a.id;
    e.shared = ext || shared;
    e.createdAt = clock.instant();
    e.note = Rules.optional(note, 600);
    db.save(e);
    db.flush();
    access.audit(
        "FILE_UPLOAD", e.id, id, "版本 " + e.versionNumber + "；" + (e.shared ? "代理共享" : "内部"));
    return metadata(id).stream().filter(r -> r.get("id").equals(e.id)).findFirst().orElseThrow();
  }

  /** 每次下载核验当前代理授权和共享状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Evidence file(Long id) {
    var e = db.get(Evidence.class, id);
    access.matter(e.matterId);
    if (access.external(access.current()) && !e.shared) throw new Problem(404, "文件不可访问");
    return e;
  }

  /** 校验凭据属于同案件，指派代理时必须显式共享。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Evidence evidence(Long fileId, Long matterId, boolean shared) {
    var e = file(fileId);
    Rules.check(e.matterId.equals(matterId), "文件跨案件引用");
    Rules.check(!shared || e.shared, "代理办理的来文和回执须为共享文件");
    return e;
  }
}

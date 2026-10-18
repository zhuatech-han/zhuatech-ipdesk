// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 同源业务接口，实际状态和范围由领域服务统一校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Workflow flow;
  final Catalog catalog;
  final Files files;
  final Notifications notifications;

  public ApiController(Workflow flow, Catalog catalog, Files files, Notifications notifications) {
    this.flow = flow;
    this.catalog = catalog;
    this.files = files;
    this.notifications = notifications;
  }

  /** 表单启用引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/references")
  public Object references() {
    return catalog.references();
  }

  /** 企业提案范围列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/proposals")
  public Object proposals() {
    return flow.proposals();
  }

  /** 提案草稿新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/proposals")
  public Object createProposal(@RequestBody Map<String, Object> b) {
    return flow.saveProposal(null, b);
  }

  /** 提案草稿修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/proposals/{id}")
  public Object updateProposal(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.saveProposal(id, b);
  }

  /** 提案草稿删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/proposals/{id}")
  public void deleteProposal(@PathVariable Long id, @RequestParam long version) {
    flow.deleteProposal(id, version);
  }

  /** 提案独立评审。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/proposals/{id}/{action}")
  public Object proposalAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return flow.proposalAction(id, action, b);
  }

  /** 案件有界搜索分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/matters")
  public Object matters(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "id") String sort) {
    return flow.list(q, state, page, sort);
  }

  /** 从已审提案立案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters")
  public Object createMatter(@RequestBody Map<String, Object> b) {
    return flow.createMatter(b);
  }

  /** 案件详情按内部/代理权限投影。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/matters/{id}")
  public Object matter(@PathVariable Long id) {
    return flow.detail(id);
  }

  /** 案件信息修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/matters/{id}")
  public Object updateMatter(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.updateMatter(id, b);
  }

  /** 负责人和代理显式授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters/{id}/assign")
  public Object assign(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.assign(id, b);
  }

  /** 结案与重开审签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters/{id}/state/{action}")
  public Object caseAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return flow.caseAction(id, action, b);
  }

  /** 期限待审版本建立。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters/{id}/deadlines")
  public Object deadline(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.deadline(id, b);
  }

  /** 期限确认、办理与取消复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/deadlines/{id}/{action}")
  public Object deadlineAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return flow.deadlineAction(id, action, b);
  }

  /** 同案附件原字节上传，真实解析与容量检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters/{id}/files")
  public Object upload(
      @PathVariable Long id,
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "false") boolean shared,
      @RequestParam(defaultValue = "") String note)
      throws java.io.IOException {
    return files.upload(id, file.getOriginalFilename(), file.getBytes(), shared, note);
  }

  /** 每次核对授权，附件下载禁止路径输入和缓存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/files/{id}")
  public ResponseEntity<byte[]> file(@PathVariable Long id) {
    var e = files.file(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(e.contentType))
        .cacheControl(CacheControl.noStore())
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(e.fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(e.content);
  }

  /** 新建费用草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/matters/{id}/fees")
  public Object createFee(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.saveFee(id, null, b);
  }

  /** 修订本人费用草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/matters/{id}/fees/{feeId}")
  public Object updateFee(
      @PathVariable Long id, @PathVariable Long feeId, @RequestBody Map<String, Object> b) {
    return flow.saveFee(id, feeId, b);
  }

  /** 删除未提交费用草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/fees/{id}")
  public void deleteFee(@PathVariable Long id, @RequestParam long version) {
    flow.deleteFee(id, version);
  }

  /** 费用送审及独立批准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/fees/{id}/{action}")
  public Object feeAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return flow.feeAction(id, action, b);
  }

  /** 线下支付凭据和完整冲销登记，不移动资金。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/fees/{id}/payments")
  public Object payment(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return flow.payment(id, b);
  }

  /** 凭据独立复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/payments/{id}/{action}")
  public Object paymentAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return flow.paymentAction(id, action, b);
  }

  /** 当前权限仪表盘。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object report() {
    return flow.report();
  }

  /** 当前范围CSV，禁止代理及无报表岗位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/export")
  public ResponseEntity<String> export() {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header("Content-Disposition", "attachment; filename=ipdesk-cases.csv")
        .cacheControl(CacheControl.noStore())
        .body(flow.export());
  }

  /** 管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{resource}")
  public Object catalog(@PathVariable String resource) {
    return catalog.list(resource);
  }

  /** 管理目录新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{resource}")
  public Object createCatalog(@PathVariable String resource, @RequestBody Map<String, Object> b) {
    return catalog.save(resource, null, b);
  }

  /** 管理目录变更。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{resource}/{id}")
  public Object updateCatalog(
      @PathVariable String resource, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return catalog.save(resource, id, b);
  }

  /** 管理目录删除保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{resource}/{id}")
  public void deleteCatalog(
      @PathVariable String resource, @PathVariable Long id, @RequestParam long version) {
    catalog.delete(resource, id, version);
  }

  /** 本人提醒/管理员邮件投递记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/notifications")
  public Object notifications(@RequestParam(defaultValue = "false") boolean admin) {
    return notifications.list(admin);
  }

  /** 站内已读或人工确认未送达后重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/notifications/{id}/{action}")
  public void notification(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    notifications.action(id, action, b);
  }

  /** 管理员立即检查到期事项及投递队列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/notifications/run")
  public Object run() {
    return notifications.runNow();
  }
}

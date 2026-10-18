// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;

/** 金额边界与附件解析真实验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RulesTest {
  /** 静态有效PDF测试文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static byte[] pdf() throws IOException {
    try (var d = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      d.addPage(new PDPage());
      d.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void exactMoney() {
    assertEquals(29, Rules.cents("29", false));
    assertEquals(10000000000000L, Rules.cents("10000000000000", false));
    assertThrows(Problem.class, () -> Rules.cents("1.2", false));
    assertThrows(Problem.class, () -> Rules.cents("0", false));
    assertThrows(Problem.class, () -> Rules.cents("10000000000001", false));
  }

  @Test
  void strongPasswordByteLimit() {
    Rules.password("TEST-Only-Password123!");
    assertThrows(Problem.class, () -> Rules.password("x"));
    assertThrows(Problem.class, () -> Rules.password("Aa1" + "中".repeat(24)));
  }

  @Test
  void validAndCorruptPdf() throws Exception {
    assertEquals("application/pdf", FileFormat.validate("notice.pdf", pdf()));
    assertThrows(Problem.class, () -> FileFormat.validate("notice.pdf", "%PDF-fake".getBytes()));
    assertThrows(Problem.class, () -> FileFormat.validate("notice.html", pdf()));
  }

  @Test
  void rejectActivePdf() throws Exception {
    try (var d = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      d.addPage(new PDPage());
      d.getDocumentCatalog()
          .setOpenAction(
              new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert(1)"));
      d.save(out);
      assertThrows(Problem.class, () -> FileFormat.validate("notice.pdf", out.toByteArray()));
    }
  }

  @Test
  void safeExportAndEmail() {
    assertEquals("\"'=1+2\"", Rules.csv("=1+2"));
    assertThrows(Problem.class, () -> Catalog.email("a@example.invalid\r\nBcc:b@example.invalid"));
    assertThrows(Problem.class, () -> Catalog.email("a@example.invalid,b@example.invalid"));
    assertEquals("a@example.invalid", Catalog.email("a@example.invalid"));
  }
}

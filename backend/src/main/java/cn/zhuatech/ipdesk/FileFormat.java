// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;

/** 私有附件格式校验，不替代杀毒服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class FileFormat {
  static {
    ImageIO.setUseCache(false);
  }

  /** 生成安全展示及下载文件名，原始路径不进入响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String filename(String name) {
    String s = Rules.text(name, 160).replaceAll("[^\\p{L}\\p{N}_. -]", "_");
    Rules.check(!s.equals(".") && !s.equals(".."), "文件名无效");
    return s;
  }

  /** PDF须可解析、未加密、无表单/嵌入文件；图片先校验像素再解码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String validate(String name, byte[] data) {
    Rules.check(data.length > 0 && data.length <= 3 * 1024 * 1024, "文件须在3MB以内");
    String lower = name.toLowerCase(Locale.ROOT);
    try {
      if (lower.endsWith(".pdf")) {
        Rules.check(
            data.length > 8 && new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
            "PDF文件签名无效");
        try (var doc = Loader.loadPDF(data)) {
          Rules.check(
              !doc.isEncrypted() && doc.getNumberOfPages() > 0 && doc.getNumberOfPages() <= 200,
              "仅支持200页以内未加密PDF");
          validatePdfObjects(doc.getDocumentCatalog().getCOSObject());
          var c = doc.getDocumentCatalog();
          Rules.check(
              c.getAcroForm() == null
                  && c.getOpenAction() == null
                  && (c.getNames() == null
                      || (c.getNames().getJavaScript() == null
                          && c.getNames().getEmbeddedFiles() == null)),
              "仅支持无活动内容的静态PDF");
        }
        return "application/pdf";
      }
      Rules.check(
          lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"),
          "只支持静态PDF、PNG和JPEG");
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
        var readers = ImageIO.getImageReaders(stream);
        Rules.check(readers.hasNext(), "图片无法解析");
        var reader = readers.next();
        try {
          reader.setInput(stream);
          long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
          Rules.check(pixels > 0 && pixels <= 20000000, "图片须不超过2000万像素");
          String type = reader.getFormatName().toLowerCase(Locale.ROOT);
          Rules.check(
              type.equals("png")
                  ? lower.endsWith(".png")
                  : type.equals("jpeg") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")),
              "图片格式与扩展名不一致");
          Rules.check(reader.read(0) != null, "图片数据无效");
          return type.equals("png") ? "image/png" : "image/jpeg";
        } finally {
          reader.dispose();
        }
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "文件损坏、含不支持内容或无法解析");
    }
  }

  /** 有界遍历PDF对象，拒绝活动动作和嵌入文件；不替代杀毒服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static void validatePdfObjects(COSBase root) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
    var pending = new ArrayDeque<COSBase>();
    pending.push(root);
    var keys =
        Set.of("JS", "AA", "OpenAction", "EmbeddedFiles", "AF", "AcroForm", "RichMediaContent");
    var actions =
        Set.of(
            "JavaScript",
            "Launch",
            "SubmitForm",
            "ImportData",
            "GoToR",
            "GoToE",
            "Rendition",
            "Movie",
            "Sound");
    while (!pending.isEmpty()) {
      COSBase value = pending.pop();
      if (!seen.add(value)) continue;
      Rules.check(seen.size() <= 100000, "PDF对象数量超限");
      if (value instanceof COSObject object) {
        if (object.getObject() != null) pending.push(object.getObject());
      } else if (value instanceof COSDictionary dict) {
        for (var key : dict.keySet())
          Rules.check(!keys.contains(key.getName()), "PDF含活动动作、表单或嵌入文件");
        var action = dict.getDictionaryObject(COSName.S);
        Rules.check(!(action instanceof COSName n) || !actions.contains(n.getName()), "PDF动作不受支持");
        for (var child : dict.getValues()) if (child != null) pending.push(child);
      } else if (value instanceof COSArray array) {
        for (var child : array) if (child != null) pending.push(child);
      }
    }
  }
}

package io.github.lnyocly.ai4j.document.tika;

import io.github.lnyocly.ai4j.document.DocumentParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于 Apache Tika 的 {@link DocumentParser} 实现。
 *
 * <p>支持 PDF、Word（doc/docx）、Excel（xls/xlsx）、PowerPoint、HTML、XML、纯文本
 * 等主流富文档格式。由 {@code ai4j-document-tika} artifact 通过
 * {@code META-INF/services} 注册到 ServiceLoader。</p>
 *
 * <p>核心 ai4j 模块不携带此实现；用户按需引入
 * {@code io.github.lnyo-cly:ai4j-document-tika} 即可启用富文档解析。</p>
 */
public class TikaDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream in, String filename) throws IOException {
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set("resourceName", filename);
        }
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        try {
            parser.parse(in, handler, metadata, context);
        } catch (TikaException e) {
            throw new IOException("Tika parse failed: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new IOException("SAX parse failed: " + e.getMessage(), e);
        }
        return handler.toString();
    }
}

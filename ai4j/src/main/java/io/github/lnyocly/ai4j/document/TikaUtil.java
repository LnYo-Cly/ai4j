package io.github.lnyocly.ai4j.document;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析工具类（向后兼容入口）。
 *
 * <p>历史版本直接依赖 {@code tika-parsers-standard-package} 做富文档内容解析，
 * 但该包会带来约 60MB 传递依赖（POI / PDFBox / BouncyCastle 等）。
 * 自 P0-B 依赖瘦身起，文档解析能力拆分到独立 artifact
 * {@code ai4j-document-tika}，本类保留作为向后兼容入口：</p>
 *
 * <ul>
 *   <li>{@code parse*} 系列方法委托给 {@link DocumentParsers}（基于 ServiceLoader），
 *       当 classpath 上无实现时抛出友好错误。</li>
 *   <li>{@code detectMimeType*} 方法继续使用 {@code tika-core}（轻量，约 1MB），
 *       因为 MIME 探测是高频基础能力。</li>
 * </ul>
 *
 * <p>用户若需解析 PDF/Word/Excel 等富文档，请添加可选依赖：
 * {@code io.github.lnyo-cly:ai4j-document-tika}。</p>
 */
public class TikaUtil {

    private static final Tika tika = new Tika();

    /**
     * 解析File文件，返回文档内容
     * @param file 要解析的文件
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     * @throws SAXException
     */
    public static String parseFile(File file) throws IOException, TikaException, SAXException {
        try (InputStream stream = file.toURI().toURL().openStream()) {
            return DocumentParsers.parse(stream, file.getName());
        }
    }

    /**
     * 解析InputStream输入流，返回文档内容
     * @param stream 要解析的输入流
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     * @throws SAXException
     */
    public static String parseInputStream(InputStream stream) throws IOException, TikaException, SAXException {
        return DocumentParsers.parse(stream, null);
    }

    /**
     * 使用Tika简单接口解析文件，返回文档内容
     * @param file 要解析的文件
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     */
    public static String parseFileWithTika(File file) throws IOException, TikaException {
        try (InputStream stream = file.toURI().toURL().openStream()) {
            return DocumentParsers.parse(stream, file.getName());
        }
    }

    /**
     * 解析InputStream输入流，使用Tika简单接口，返回文档内容
     * @param stream 要解析的输入流
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     */
    public static String parseInputStreamWithTika(InputStream stream) throws IOException, TikaException {
        return DocumentParsers.parse(stream, null);
    }

    /**
     * 检测File文件的MIME类型
     * @param file 要检测的文件
     * @return MIME类型
     * @throws IOException
     */
    public static String detectMimeType(File file) throws IOException {
        return tika.detect(file);
    }

    /**
     * 检测InputStream输入流的MIME类型
     * @param stream 要检测的输入流
     * @return MIME类型
     * @throws IOException
     */
    public static String detectMimeType(InputStream stream) throws IOException {
        return tika.detect(stream);
    }
}

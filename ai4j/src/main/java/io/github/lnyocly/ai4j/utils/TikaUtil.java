package io.github.lnyocly.ai4j.utils;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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
            return parseInputStream(stream);
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
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        parser.parse(stream, handler, metadata, context);
        return handler.toString();
    }

    /**
     * 使用Tika简单接口解析文件，返回文档内容
     * @param file 要解析的文件
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     */
    public static String parseFileWithTika(File file) throws IOException, TikaException {
        return tika.parseToString(file);
    }

    /**
     * 解析InputStream输入流，使用Tika简单接口，返回文档内容
     * @param stream 要解析的输入流
     * @return 解析后的文档内容
     * @throws IOException
     * @throws TikaException
     */
    public static String parseInputStreamWithTika(InputStream stream) throws IOException, TikaException {
        return tika.parseToString(stream);
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

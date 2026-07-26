package io.github.lnyocly.ai4j.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ServiceLoader;

/**
 * 文档解析器的 SPI 入口与友好降级。
 *
 * <p>通过 {@link ServiceLoader} 查找 classpath 上的 {@link DocumentParser} 实现。
 * 当不存在任何实现时（例如用户未引入 {@code ai4j-document-tika}），
 * {@link #parse(InputStream, String)} 抛出带提示信息的 {@link IllegalStateException}，
 * 而不是令底层 NPE/ClassNotFoundException 透传到用户面前。</p>
 *
 * @Author cly
 * @Description DocumentParser 的 ServiceLoader 入口
 * @Date 2026/07/27
 */
public final class DocumentParsers {

    private static final String HINT_ARTIFACT = "ai4j-document-tika";

    private DocumentParsers() {
    }

    /**
     * 加载 classpath 上首个 {@link DocumentParser} 实现；找不到时抛友好错误。
     *
     * @return 首个可用的 DocumentParser 实现
     */
    public static DocumentParser provider() {
        ServiceLoader<DocumentParser> loader = ServiceLoader.load(DocumentParser.class);
        for (DocumentParser impl : loader) {
            return impl;
        }
        throw noParserError();
    }

    /**
     * 解析输入流中的文档内容；无实现时抛友好错误。
     *
     * @param in       输入流
     * @param filename 文件名提示，可为 {@code null}
     * @return 解析后的文档纯文本
     * @throws IOException           当解析失败时
     * @throws IllegalStateException 当 classpath 上无 DocumentParser 实现时
     */
    public static String parse(InputStream in, String filename) throws IOException {
        return provider().parse(in, filename);
    }

    private static IllegalStateException noParserError() {
        return new IllegalStateException(
                "No DocumentParser implementation found on classpath. "
                        + "To enable document parsing (PDF, Word, Excel, etc.), "
                        + "add the optional dependency '" + HINT_ARTIFACT + "'.");
    }
}

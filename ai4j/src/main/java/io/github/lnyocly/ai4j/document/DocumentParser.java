package io.github.lnyocly.ai4j.document;

import java.io.IOException;
import java.io.InputStream;

/**
 * SPI 接口：文档内容解析器。
 *
 * <p>核心 ai4j 模块只定义此接口，具体实现由独立 artifact 提供
 * （例如 {@code ai4j-document-tika}），通过 {@link java.util.ServiceLoader}
 * 在 classpath 上发现。当 classpath 上没有任何实现时，
 * {@link DocumentParsers#parse(InputStream, String)} 会抛出友好错误，
 * 指引用户添加可选 artifact。</p>
 *
 * @Author cly
 * @Description 文档解析 SPI 接口
 * @Date 2026/07/27
 */
public interface DocumentParser {

    /**
     * 解析输入流中的文档内容，返回纯文本。
     *
     * @param in       输入流（实现方负责关闭或确保已消费完毕）
     * @param filename 文件名提示，可为 {@code null}；用于辅助格式探测
     * @return 解析后的文档纯文本
     * @throws IOException 当读取失败时
     */
    String parse(InputStream in, String filename) throws IOException;
}

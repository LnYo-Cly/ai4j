package io.github.lnyocly.ai4j.tool;

import com.example.ai4j.explicit.ExplicitRegisterEchoFunction;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;
import org.reflections.util.ClasspathHelper;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 钉住 ToolUtil 工具发现的三条语义：
 *
 * <ol>
 *   <li>扫描 URL 必须覆盖全部 classpath 根，而不只是 SDK 包所在的那个元素。
 *       业务项目通过依赖引用 SDK jar 启动时，自己的 classes 目录里没有
 *       {@code io/github/lnyocly/ai4j}，只扫 SDK 包会漏掉所有用户自定义工具；</li>
 *   <li>业务工具类即使定义在 SDK 包名之外，只要与 SDK 处在同一个 classpath 根就能被发现；</li>
 *   <li>classpath 形态特殊（嵌套 jar 等）导致扫描失效时，可以用
 *       {@link ToolUtil#registerFunctionClasses(Class[])} 显式登记。</li>
 * </ol>
 */
public class ExternalScanPackageTest {

    private static final String WEATHER_TOOL = "external_package_weather";
    private static final String ECHO_TOOL = "explicit_register_echo";

    @Test
    public void scanUrlsIncludeEveryClasspathRoot() throws Exception {
        Method scanUrls = ToolUtil.class.getDeclaredMethod("scanUrls");
        scanUrls.setAccessible(true);
        @SuppressWarnings("unchecked")
        Collection<URL> urls = (Collection<URL>) scanUrls.invoke(null);
        assertTrue("业务项目自己的 classpath 根必须在扫描范围内",
                urls.containsAll(ClasspathHelper.forPackage("")));
    }

    @Test
    public void discoversToolDeclaredOutsideSdkPackageInSameClasspathRoot() {
        List<Tool> tools = ToolUtil.getAllFunctionTools(Collections.singletonList(WEATHER_TOOL));
        assertEquals(1, tools.size());

        Tool.Function function = tools.get(0).getFunction();
        assertEquals(WEATHER_TOOL, function.getName());
        assertNotNull(function.getParameters());
        assertTrue(function.getParameters().getProperties().containsKey("city"));
        assertEquals(Collections.singletonList("city"), function.getParameters().getRequired());

        String output = ToolUtil.invoke(WEATHER_TOOL, "{\"city\":\"shanghai\"}");
        assertTrue(output, output.contains("shanghai"));
    }

    @Test
    public void registersToolClassExplicitlyWithoutClasspathScan() {
        ToolUtil.registerFunctionClasses(ExplicitRegisterEchoFunction.class);

        List<Tool> tools = ToolUtil.getAllFunctionTools(Collections.singletonList(ECHO_TOOL));
        assertEquals(1, tools.size());
        assertEquals(ECHO_TOOL, tools.get(0).getFunction().getName());

        String output = ToolUtil.invoke(ECHO_TOOL, "{\"message\":\"ping\"}");
        assertTrue(output, output.contains("ping"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsClassWithoutFunctionCallAnnotation() {
        ToolUtil.registerFunctionClasses(String.class);
    }
}

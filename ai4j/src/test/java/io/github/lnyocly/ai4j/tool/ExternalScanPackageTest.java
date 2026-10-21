package io.github.lnyocly.ai4j.tool;

import com.example.ai4j.explicit.ExplicitRegisterEchoFunction;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 钉住 ToolUtil 工具发现的两个真实语义：
 *
 * <ol>
 *   <li>正常目录型 classpath 下，扫描 URL 会被解析到 classpath 根，
 *       因此 {@code @FunctionCall} 定义在 SDK 包名之外也能被发现；</li>
 *   <li>classpath 形态特殊（如嵌套 jar）导致扫描失效时，可以用
 *       {@link ToolUtil#registerFunctionClasses(Class[])} 显式登记。</li>
 * </ol>
 */
public class ExternalScanPackageTest {

    private static final String WEATHER_TOOL = "external_package_weather";
    private static final String ECHO_TOOL = "explicit_register_echo";

    @Test
    public void discoversFunctionCallDeclaredOutsideSdkPackage() {
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

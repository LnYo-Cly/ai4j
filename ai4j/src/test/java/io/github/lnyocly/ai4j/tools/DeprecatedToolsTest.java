package io.github.lnyocly.ai4j.tools;

import org.junit.Assert;
import org.junit.Test;

public class DeprecatedToolsTest {

    @Test
    public void legacyToolFunctionsAreMarkedDeprecated() {
        Assert.assertNotNull(QueryWeatherFunction.class.getAnnotation(Deprecated.class));
        Assert.assertNotNull(QueryTrainInfoFunction.class.getAnnotation(Deprecated.class));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void queryTrainInfoGuardsNullRequestAndType() {
        QueryTrainInfoFunction fn = new QueryTrainInfoFunction();
        Assert.assertEquals("天气情况较差，不允许发车", fn.apply(null));

        QueryTrainInfoFunction.Request empty = new QueryTrainInfoFunction.Request();
        Assert.assertEquals("天气情况较差，不允许发车", fn.apply(empty));

        QueryTrainInfoFunction.Request cool = new QueryTrainInfoFunction.Request();
        cool.setType(35);
        Assert.assertEquals("天气情况较差，不允许发车", fn.apply(cool));

        QueryTrainInfoFunction.Request hot = new QueryTrainInfoFunction.Request();
        hot.setType(36);
        Assert.assertEquals("天气情况正常，允许发车", fn.apply(hot));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void queryWeatherReturnsFailureMessageWithoutHardcodedKey() {
        QueryWeatherFunction fn = new QueryWeatherFunction();
        Assert.assertEquals("获取天气失败 当前天气未知", fn.apply(null));

        QueryWeatherFunction.Request missingType = new QueryWeatherFunction.Request();
        missingType.setLocation("beijing");
        Assert.assertEquals("获取天气失败 当前天气未知", fn.apply(missingType));

        QueryWeatherFunction.Request blankLocation = new QueryWeatherFunction.Request();
        blankLocation.setType(QueryWeatherFunction.Type.now);
        blankLocation.setLocation("   ");
        Assert.assertEquals("获取天气失败 当前天气未知", fn.apply(blankLocation));

        String previous = System.getProperty("ai4j.seniverse.api-key");
        System.setProperty("ai4j.seniverse.api-key", "");
        try {
            QueryWeatherFunction.Request valid = new QueryWeatherFunction.Request();
            valid.setType(QueryWeatherFunction.Type.now);
            valid.setLocation("beijing");
            Assert.assertEquals("获取天气失败 当前天气未知", fn.apply(valid));
        } finally {
            if (previous == null) {
                System.clearProperty("ai4j.seniverse.api-key");
            } else {
                System.setProperty("ai4j.seniverse.api-key", previous);
            }
        }
    }
}

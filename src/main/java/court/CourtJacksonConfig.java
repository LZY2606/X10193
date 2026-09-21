package court;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 裁决模型大量使用“全参构造 + final 字段”的不可变对象。为了让结果快照能可靠序列化/反序列化
 * （旧方案不漂移依赖于把完整 Decision 存成 JSON 再读回），开启字段可见性并注册参数名模块。
 */
@Configuration
public class CourtJacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        // 模型含大量派生 getter（isUndef/isCommon…），它们会被序列化进快照；
        // 读回时这些不是构造参数，统一忽略未知属性即可（事实字段全部保留）。
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }
}

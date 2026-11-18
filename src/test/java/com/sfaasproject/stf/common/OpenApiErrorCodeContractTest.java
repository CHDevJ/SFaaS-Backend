package com.sfaasproject.stf.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 오류코드 계약(API_SPEC 11.6). 서버 코드에서 내는 오류코드가 openapi ErrorDetail.code enum 과 API_SPEC 7장 표에
 * 모두 있는지, 그리고 openapi 에 중복 키가 없는지 확인한다(중복 키는 YAML 도구 대부분이 조용히 덮어쓴다).
 */
class OpenApiErrorCodeContractTest {

    /** 예전 v5 기능 전용 코드. 기본 실행(legacy-v5-enabled=false)에서는 해당 API 가 없다. */
    static final Set<String> LEGACY_V5 = Set.of("ALARM_ALREADY_ACKNOWLEDGED", "ASSIGNEE_NOT_USER",
            "DUTY_GAP_NOT_ALLOWED", "DUTY_OVERLAP", "DUTY_REPLACEMENT_REQUIRED", "DUTY_SCHEDULE_NOT_FOUND",
            "FALSE_POSITIVE_NOT_ALLOWED", "PART_NOT_FOUND");

    /** 오류코드를 만드는 호출. 여러 줄에 걸친 호출도 잡는다. */
    static final List<Pattern> CODE_SITES = List.of(
            Pattern.compile("DomainException\\(\\s*(?:HttpStatus\\.[A-Z_]+|[a-z]\\w*)\\s*,\\s*\"([A-Z][A-Z0-9_]+)\""),
            Pattern.compile("\\b(?:error|missing|payload|body|conflict|invalid|notFound|fail|reject|domain)\\(\\s*"
                    + "(?:HttpStatus\\.[A-Z_]+\\s*,\\s*)?\"([A-Z][A-Z0-9_]+)\""));

    @Test
    void everyServerErrorCodeIsInTheContract() throws IOException {
        Set<String> enumCodes = new TreeSet<>(errorCodeEnum());
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (Pattern site : CODE_SITES) {
                    Matcher m = site.matcher(source);
                    while (m.find()) {
                        used.add(m.group(1));
                    }
                }
            }
        }
        used.removeAll(LEGACY_V5);
        assertThat(used).as("오류코드 수집이 동작해야 한다").contains("VALIDATION_ERROR", "SETTING_OUT_OF_RANGE");
        assertThat(enumCodes).as("서버가 내는 오류코드는 ErrorDetail.code enum 에 있어야 한다").containsAll(used);
    }

    @Test
    void errorCodeEnumMatchesTheSpecTable() throws IOException {
        String spec = Files.readString(Path.of("docs/API_SPEC.md"));
        String table = spec.substring(spec.indexOf("## 7. 오류코드"), spec.indexOf("## 8."));
        Set<String> tableCodes = new TreeSet<>();
        Matcher m = Pattern.compile("(?m)^\\| ([A-Z][A-Z0-9_]+) \\|").matcher(table);
        while (m.find()) {
            tableCodes.add(m.group(1));
        }
        assertThat(tableCodes).isEqualTo(new TreeSet<>(errorCodeEnum()));
    }

    @Test
    void openApiHasNoDuplicateKeys() throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        options.setAllowDuplicateKeys(false);
        // 중복 키가 있으면 DuplicateKeyException 으로 실패한다.
        Object loaded = new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of("docs/openapi.yaml")));
        assertThat(loaded).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static List<String> errorCodeEnum() throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        Map<String, Object> spec = new Yaml(new SafeConstructor(options))
                .load(Files.readString(Path.of("docs/openapi.yaml")));
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) spec.get("components"))
                .get("schemas");
        Map<String, Object> detail = (Map<String, Object>) schemas.get("ErrorDetail");
        Map<String, Object> code = (Map<String, Object>) ((Map<String, Object>) detail.get("properties")).get("code");
        return (List<String>) code.get("enum");
    }
}

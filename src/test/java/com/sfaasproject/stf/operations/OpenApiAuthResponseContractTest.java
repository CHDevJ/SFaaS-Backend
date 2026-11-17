package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class OpenApiAuthResponseContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void generatedResponseTypesMustIncludeTheAccessToken() throws Exception {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        Map<String, Object> spec = new Yaml(new SafeConstructor(options))
                .load(Files.readString(Path.of("docs/openapi.yaml")));
        var schemas = (Map<String, Map<String, Object>>)((Map<String, Object>)spec.get("components")).get("schemas");
        var login = (Map<String,Object>)((List<?>)schemas.get("LoginData").get("allOf")).get(1);
        for (var response : List.of(login, schemas.get("TokenData"))) {
            var token = (Map<String,Object>)((Map<String,Object>)response.get("properties")).get("accessToken");
            assertNotEquals(Boolean.TRUE, token.get("writeOnly"));
            assertEquals(Boolean.TRUE, token.get("readOnly"));
            assertTrue(((List<?>)response.get("required")).contains("accessToken"));
        }
        var password = (Map<String,Object>)((Map<String,Object>)schemas.get("LoginRequest").get("properties")).get("password");
        assertEquals(Boolean.TRUE, password.get("writeOnly"));
    }
}

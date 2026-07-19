package com.son.soccerStreaming.news.client;

import com.son.soccerStreaming.news.config.NewsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.global.externalapi.ExternalApiExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTitleTranslationClientTest {

    @Test
    void sendsStrictStructuredOutputAndMapsArticleIds() {
        NewsProperties properties = new NewsProperties();
        properties.getTranslation().setApiKey("openai-test-key");
        properties.getTranslation().setModel("test-model");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiExecutor executor = mock(ExternalApiExecutor.class);
        when(executor.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        });
        OpenAiTitleTranslationClient client = new OpenAiTitleTranslationClient(
                builder.build(), properties, new ObjectMapper(), executor);

        server.expect(requestTo("https://openai.test/v1/responses"))
                .andExpect(header("Authorization", "Bearer openai-test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess("""
                        {"output":[{"content":[{"type":"output_text","text":"{\\"translations\\":[{\\"articleId\\":7,\\"translatedTitle\\":\\"번역된 제목\\"}]}"}]}]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.translate(List.of(
                new OpenAiTitleTranslationClient.TranslationInput(7L, "Original title")));

        assertThat(result).containsExactlyEntriesOf(java.util.Map.of(7L, "번역된 제목"));
        server.verify();
    }
}

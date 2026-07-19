package com.son.soccerStreaming.news.client;

import com.son.soccerStreaming.news.config.NewsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.son.soccerStreaming.global.externalapi.ExternalApiExecutor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SerpApiNewsClientTest {

    private NewsProperties properties;
    private SerpApiNewsClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new NewsProperties();
        properties.getSerpApi().setApiKey("test-key");
        properties.getSerpApi().setSearchSites(java.util.List.of(
                "bbc.com/sport/football",
                "skysports.com/football",
                "theguardian.com/football",
                "nytimes.com/athletic",
                "goal.com",
                "telegraph.co.uk/football"
        ));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://serpapi.test");
        server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiExecutor executor = mock(ExternalApiExecutor.class);
        when(executor.execute(any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        });
        client = new SerpApiNewsClient(builder.build(), properties, executor);
    }

    @Test
    void buildsQuotedTeamAndTrustedSiteQuery() {
        assertThat(client.buildQuery("Manchester City"))
                .isEqualTo("\"Manchester City\" football when:7d "
                        + "(site:bbc.com/sport/football OR site:skysports.com/football OR "
                        + "site:theguardian.com/football OR site:nytimes.com/athletic OR "
                        + "site:goal.com OR site:telegraph.co.uk/football)");
    }

    @Test
    void normalizesRegularGroupedAndHighlightResultsAndBlocksUntrustedUrls() {
        server.expect(request -> {
                    String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
                    assertThat(decoded).contains("engine=google_news");
                    assertThat(decoded).contains("q=\"Arsenal\" football when:7d");
                })
                .andRespond(withSuccess("""
                        {"news_results":[
                          {"title":"BBC title","link":"https://www.bbc.com/sport/football/articles/1","iso_date":"2026-07-14T01:00:00Z","source":{"name":"BBC Sport"}},
                          {"stories":[
                            {"title":"Sky title","link":"https://www.skysports.com/football/news/2","iso_date":"2026-07-14T02:00:00Z","source":{"name":"Sky Sports"}},
                            {"title":"Blocked","link":"https://example.com/football/3","iso_date":"2026-07-14T03:00:00Z"}
                          ]},
                          {"highlight":{"title":"Guardian title","link":"https://www.theguardian.com/football/2026/jul/14/story","iso_date":"2026-07-14T04:00:00Z","source":{"name":"The Guardian"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.searchTeamNews("Arsenal");

        assertThat(result).extracting(SerpApiNewsClient.SearchArticle::title)
                .containsExactly("BBC title", "Sky title", "Guardian title");
        server.verify();
    }

    @Test
    void requiresBothTrustedHostAndExpectedSectionPath() {
        assertThat(client.isAllowedUrl("https://bbc.com/sport/football/articles/1")).isTrue();
        assertThat(client.isAllowedUrl("https://evil-bbc.com/sport/football/articles/1")).isFalse();
        assertThat(client.isAllowedUrl("https://bbc.com/news/articles/1")).isFalse();
        assertThat(client.isAllowedUrl("https://goal.com/en/news/sunderland-transfer/1")).isTrue();
        assertThat(client.isAllowedUrl("https://telegraph.co.uk/football/2026/07/14/sunderland-news/")).isTrue();
        assertThat(client.isAllowedUrl("https://telegraph.co.uk/news/2026/07/14/story/")).isFalse();
        assertThat(client.isAllowedUrl("javascript:alert(1)")).isFalse();
    }
}

package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.news.client.SerpApiNewsClient;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import com.son.soccerStreaming.global.externalapi.ExternalApiInvocationContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectionService {

    private final TeamRepository teamRepository;
    private final SerpApiNewsClient serpApiNewsClient;
    private final NewsPersistenceService newsPersistenceService;

    public CollectionResult collectAllTeams() {
        List<Team> teams = teamRepository.findAllByOrderByNameAsc();
        int succeededTeams = 0;
        int failedTeams = 0;
        int savedArticles = 0;

        for (Team team : teams) {
            try {
                List<SerpApiNewsClient.SearchArticle> articles = serpApiNewsClient.searchTeamNews(team.getName());
                savedArticles += newsPersistenceService.saveTeamArticles(team.getTeamId(), articles, Instant.now());
                succeededTeams++;
            } catch (Exception e) {
                failedTeams++;
                // SerpApi authenticates with a query parameter, so never log the exception URL.
                log.warn("Team news collection failed. teamId={}, teamName={}, errorType={}",
                        team.getTeamId(), team.getName(), e.getClass().getSimpleName());
            }
        }

        return new CollectionResult(teams.size(), succeededTeams, failedTeams, savedArticles);
    }

    public int collectTeam(Long teamId) {
        return collectTeam(teamId, ExternalApiInvocationContext.system());
    }

    public int collectTeam(Long teamId, ExternalApiInvocationContext context) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        List<SerpApiNewsClient.SearchArticle> articles = serpApiNewsClient.searchTeamNews(team.getName(), context);
        return newsPersistenceService.saveTeamArticles(team.getTeamId(), articles, Instant.now());
    }

    public record CollectionResult(int totalTeams, int succeededTeams, int failedTeams, int savedArticles) {
    }
}

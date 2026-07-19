package com.son.soccerStreaming.news.service;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.news.dto.TeamNewsResponseDto;
import com.son.soccerStreaming.news.dto.TeamNewsListResponseDto;
import com.son.soccerStreaming.news.repository.TeamNewsArticleRepository;
import com.son.soccerStreaming.news.repository.TeamNewsCollectionStateRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamNewsService {

    private static final int TEAM_NEWS_LIMIT = 20;

    private final TeamRepository teamRepository;
    private final TeamNewsArticleRepository teamNewsArticleRepository;
    private final TeamNewsCollectionStateRepository collectionStateRepository;

    public TeamNewsListResponseDto getTeamNews(Long teamId) {
        teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        List<TeamNewsResponseDto> articles = teamNewsArticleRepository
                .findLatestByTeamId(teamId, PageRequest.of(0, TEAM_NEWS_LIMIT)).stream()
                .map(relation -> {
                    var article = relation.getArticle();
                    return new TeamNewsResponseDto(
                            article.getId(),
                            article.getOriginalTitle(),
                            article.getTranslatedTitle(),
                            article.getPublisherName(),
                            article.getOriginalUrl(),
                            article.getPublishedAt()
                    );
                })
                .toList();
        return new TeamNewsListResponseDto(
                collectionStateRepository.findByTeamTeamId(teamId)
                        .map(state -> state.getLastCollectedAt())
                        .orElse(null),
                articles
        );
    }
}

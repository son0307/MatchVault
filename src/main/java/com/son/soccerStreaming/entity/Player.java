package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE player SET is_deleted = true WHERE id = ?")
public class Player {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String playerId;

    @Column(nullable = false)
    private String name;

    private int backNumber;

    // 상세 정보
    private int age;
    private int height;
    private int weight;
    private String mainPosition;
    private String subPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private boolean isDeleted = false;
}

package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE team SET is_deleted = true WHERE id = ?")
public class Team {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String teamId;

    @Column(nullable = false)
    private String name;

    private String stadium;

    @Column(nullable = false)
    private boolean isDeleted;
}

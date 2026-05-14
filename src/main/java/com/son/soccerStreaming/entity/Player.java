package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long playerId;

    @Column(nullable = false)
    private String name;
    private String firstname;
    private String lastname;

    // 생년월일 및 출신 정보
    private Integer age;
    private LocalDate birthDate;
    private String birthPlace;
    private String birthCountry;
    private String nationality;

    // 신체 정보
    @Column(length = 20)
    private String height;

    @Column(length = 20)
    private String weight;

    // 포지션 및 기본 등번호
    private String position;
    private Integer number;

    // 이미지 정보
    private String photoUrl;

    // 선수 정보 업데이트(Sync)를 위한 편의 메서드
    public void updateProfile(String name, String firstname, String lastname, Integer age,
                              LocalDate birthDate, String birthPlace, String birthCountry,
                              String nationality, String height, String weight,
                              String position, Integer number, String photoUrl) {
        this.name = name;
        this.firstname = firstname;
        this.lastname = lastname;
        this.age = age;
        this.birthDate = birthDate;
        this.birthPlace = birthPlace;
        this.birthCountry = birthCountry;
        this.nationality = nationality;
        this.height = height;
        this.weight = weight;
        this.position = position;
        this.number = number;
        this.photoUrl = photoUrl;
    }
}

package com.mercury.star_be.timer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TimerRankingResponseDto {

    private Long groupMemberId;
    private String image;
    private String nickname;
    private BigDecimal totalTime;
    private Long ranking;
}

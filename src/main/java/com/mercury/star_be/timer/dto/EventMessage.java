package com.mercury.star_be.timer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EventMessage {

    private Long groupMemberId;
    private Long userId;
    private TimerEvent timerEvent;

}

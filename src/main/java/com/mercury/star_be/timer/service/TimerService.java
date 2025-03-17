package com.mercury.star_be.timer.service;

import com.mercury.star_be.timer.dto.TimerDto;

import java.util.Set;

public interface TimerService {
    Set<TimerDto> getFocusRoomTimerDataByGroupIdAndMyUID(Long groupId);

    TimerDto stopTimerByGroupMemberId(Long groupMemberId);

    TimerDto startMyTimer(long groupId, long UserId);

    TimerDto endTimerByGroupIdAndUserId(Long groupId, Long userId);

//    TimerDto getMyRecentTimerByGroupMemberId(long groupId, long userId);
    TimerDto getMyRecentTimerByGroupMemberId(long groupMemberId);

    TimerDto enterFocusRoom(Long groupId);
}

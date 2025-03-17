package com.mercury.star_be.timer.repository;

import com.mercury.star_be.timer.entity.Timer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimerRepository extends JpaRepository<Timer, Long> {

    // 오늘의 타이머 정보를 가져옴
    @Query("SELECT t FROM Timer t WHERE t.groupMember.id = :groupMemberId AND t.studyDate = CURRENT_DATE")
    Timer findByGroupMemberIdAndToday(@Param("groupMemberId") Long groupMemberId);

    // 가장 최근의 타이머 정보를 가져옴
    @Query(value = "SELECT * FROM timer WHERE group_member_id = :groupMemberId ORDER BY study_date DESC LIMIT 1",
        nativeQuery = true)
    Timer findRecentOneByGroupMemberId(@Param("groupMemberId") Long groupMemberId);

    @Query("SELECT t FROM Timer t " +
            "JOIN t.groupMember gm " +
            "WHERE gm.id IN :groupMemberIds " +
            "AND t.studyDate = CURRENT_DATE")
    List<Timer> findTimersWithGroupMemberByGroupMemberIds(@Param("groupMemberIds") List<Long> userIds);
}

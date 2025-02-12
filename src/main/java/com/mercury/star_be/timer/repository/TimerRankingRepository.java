package com.mercury.star_be.timer.repository;

import com.mercury.star_be.timer.dto.TimerRankingResponseDto;
import com.mercury.star_be.timer.entity.Timer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TimerRankingRepository extends JpaRepository<Timer, Long> {

   @Query(value = """
           SELECT
              sub.user_id AS userId,
              u.image as image,
              gm.nickname AS nickname,
              sub.userTotalTime AS totalTime,
              DENSE_RANK() OVER (ORDER BY sub.userTotalTime DESC) AS ranking
            FROM (
                SELECT
                    t.user_id,
                    SUM(
                        CASE
                            WHEN t.status = 'START' and t.study_date = CURRENT_DATE
                            THEN (t.total_time + TIMESTAMPDIFF(SECOND, t.start_time, CURRENT_TIMESTAMP))
                            ELSE t.total_time
                        END
                    ) AS userTotalTime
                FROM timer t
                WHERE t.study_group_id = :groupId
                  AND t.study_date >= :startDate
                GROUP BY t.user_id
            ) sub
            JOIN group_member gm ON gm.member_id = sub.user_id AND gm.group_id = :groupId
            join users u on u.id = gm.member_id
            ORDER BY ranking asc
   """,nativeQuery = true)
   List<TimerRankingResponseDto> findRankingByGroupIdAndStartDate(@Param("groupId") Long groupId,
                                                                  @Param("startDate") LocalDate startDate);
}

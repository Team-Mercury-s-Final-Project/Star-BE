package com.mercury.star_be.studygroup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.mercury.star_be.studygroup.entity.StudyGroup;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long>, StudyGroupCustomRepository {

	@Modifying
	@Query("update StudyGroup s set s.memberCount = s.memberCount + 1 "
		 + "where s.id = :groupId and s.memberCount < s.maxCapacity")
	int increaseMemberCount(Long groupId);
}

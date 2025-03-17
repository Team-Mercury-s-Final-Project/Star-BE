package com.mercury.star_be.fixture;

import com.mercury.star_be.studygroup.entity.StudyGroup;

import java.time.LocalDateTime;

public class StudyGroupFixture {

    public static StudyGroup createStudyGroup(String name, int maxCapacity) {
        return StudyGroup.builder()
            .name(name)
            .maxCapacity(maxCapacity)
            .isPublic(true)
            .hasPassword(false)
            .memberCount(1)
            .build();
    }

    public static StudyGroup createStudyGroup(
            String name,
            String description,
            String image,
            int maxCapacity,
            Boolean isPublic,
            Boolean hasPassword,
            String password,
            int memberCount,
            LocalDateTime createAt
    ) {
        return StudyGroup.builder()
                .name(name)
                .description(description)
                .image(image)
                .maxCapacity(maxCapacity)
                .isPublic(isPublic)
                .hasPassword(hasPassword)
                .password(password)
                .memberCount(memberCount)
                .createdAt(createAt)
                .build();
    }
}

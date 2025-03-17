package com.mercury.star_be.studygroup.repository;

import com.mercury.star_be.studygroup.dto.response.MyStudyGroupListResponse;
import com.mercury.star_be.studygroup.entity.QStudyGroup;
import com.mercury.star_be.studygroup.entity.StudyGroup;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.mercury.star_be.studygroup.entity.QGroupMember.groupMember;
import static com.mercury.star_be.studygroup.entity.QStudyGroup.studyGroup;

public class StudyGroupCustomRepositoryImpl implements StudyGroupCustomRepository {
    private final JPAQueryFactory jpaQueryFactory;

    public StudyGroupCustomRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
        this.jpaQueryFactory = jpaQueryFactory;
    }


    @Override
    public Page<StudyGroup> findAllPublicByCreationDate(String keyword, String sort, String direction, Pageable pageable) {
        QStudyGroup studyGroup = QStudyGroup.studyGroup;
        // 기본 정렬: createdAt 최신순
        // 동적 정렬 설정
        OrderSpecifier<?> orderSpecifier;
        if ("memberCount".equals(sort)) {
            orderSpecifier = "asc".equalsIgnoreCase(direction)
                    ? studyGroup.memberCount.asc()
                    : studyGroup.memberCount.desc();
        } else {
            orderSpecifier = "asc".equalsIgnoreCase(direction)
                    ? studyGroup.createdAt.asc()
                    : studyGroup.createdAt.desc(); // 기본 정렬
        }
        // 조건 빌더
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(studyGroup.isPublic.eq(true)); // 공개 그룹만

        if (keyword != null && !keyword.isEmpty()) {
            builder.and(
                    studyGroup.name.containsIgnoreCase(keyword)
                            .or(studyGroup.description.containsIgnoreCase(keyword))
            );
        }
        List<StudyGroup> results = jpaQueryFactory
                .selectFrom(studyGroup)
                .where(builder)
                .orderBy(orderSpecifier)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();


        // 총 개수 가져오기
        long total = jpaQueryFactory
                .selectFrom(studyGroup)
                .where(builder)
                .fetchCount();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public List<MyStudyGroupListResponse> findMyStudyGroupList(Long memberId) {
        return jpaQueryFactory
                .select(Projections.constructor(MyStudyGroupListResponse.class,
                        studyGroup.id,
                        studyGroup.image,
                        studyGroup.name))
                .from(groupMember)
                .join(groupMember.group, studyGroup)
                .where(groupMember.member.id.eq(memberId))
                .orderBy(groupMember.joinedAt.desc()) // joinedAt 내림차순 정렬 추가
                .fetch();
    }
}

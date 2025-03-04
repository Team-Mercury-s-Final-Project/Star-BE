package com.mercury.star_be.timer.service;

import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.code.StudyGroupErrorCode;
import com.mercury.star_be.studygroup.entity.ConnectionStatus;
import com.mercury.star_be.studygroup.entity.GroupMember;
import com.mercury.star_be.studygroup.entity.StudyGroup;
import com.mercury.star_be.studygroup.repository.GroupMemberRepository;
import com.mercury.star_be.studygroup.repository.StudyGroupRepository;
import com.mercury.star_be.studygroup.service.StudyGroupSseService;
import com.mercury.star_be.timer.dto.TimerDto;
import com.mercury.star_be.timer.dto.TimerEvent;
import com.mercury.star_be.timer.entity.Timer;
import com.mercury.star_be.timer.enums.TimerStatus;
import com.mercury.star_be.timer.repository.TimerRepository;
import com.mercury.star_be.user.dto.response.UserResponse;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimerServiceImpl implements TimerService {

    private final TimerRepository timerRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudyGroupSseService studyGroupSseService;

    /**
     * 집중방 id로 집중방에 접속한 사용자들의 타이머 정보 가져오기 (타이머가 없는 경우도 포함)
     * Redis로만 조회 - connect 시 redis에 저장된 데이터를 가져옴
     * @param groupId
     * @return Set<TimerDto> 집중방에 접속한 사용자들의 타이머 정보, Redis에서 중복인 경우 제외 (set)
     */
    // 집중방에 실시간으로 연결 된 사용자 정보를 가져옴 - Redis로 id 조회, db로 데이터를 가져옴
    @Transactional
    public Set<TimerDto> getFocusRoomTimerDataByGroupIdAndMyUID(Long groupId) {
        System.out.println("연결된 사용자들 불러옴");
        UserResponse userResponse = getLoginUserInfo();
        long userId = userResponse.getId();
        String nickname = userResponse.getNickname();

        if (userResponse == null) {
            log.error("사용자 정보 없음");
            return null;
        }
        // 응답값 초기화
        Set<TimerDto> timerData = new HashSet<>(); // TimerDto 중복 예방 set
        Map<Long,String> focusRoomMemberInfo = getFocusRoomMemberIdsAndNameFromRedisByGroupId(groupId);

        // 집중방 접속 인원 없음. 하지만 본인은 접속한 상태.
        if (focusRoomMemberInfo.isEmpty()) {
            System.out.println("접속 인원 없음");
            // 본인의 가장 최신 타이머 조회
            TimerDto entryEvent = getMyTimerByGroupIdAndUserId(groupId,userId);
            // 타이머가 없는 경우, 새로 입장한 사용자임. Entry 이벤트 객체 생성
            if (entryEvent == null) {
                System.out.println("타이머 없음 새로 입장~");
                entryEvent = TimerDto.getEntryEventDtoWithNoTimer();
                entryEvent.setUserId(userId);
                entryEvent.setNickname(nickname);
            }
            timerData.add(entryEvent);
            return timerData;
        }

        // 집중방 접속 인원 있음.
        List<Long> memberIds = new ArrayList<>(focusRoomMemberInfo.keySet());
        memberIds.add(userId);
        // Timer groupId & userIds로 조회, User도 같이 조회 -유저 그룹 닉네임으로 안받아옴
//        List<TimerDto> memberTimers = timerRepository.QfindTimersWithUsersBygidAnduid(groupId, memberIds);
        List<Timer> memberTimers = timerRepository.findTimersWithUsersBygidAnduid(groupId, memberIds);

        // 타이머가 있는 접속 유저들
        Map<Long, Timer> currentMembersTimers = memberTimers.stream()
                .collect(Collectors.toMap(Timer::getUserId, timer -> timer));
//        Map<Long, TimerDto> currentMembersTimers = memberTimers.stream()
//                .collect(Collectors.toMap(TimerDto::getUserId, timer -> timer));

        // 없는 유저들 분리
        for (Map.Entry<Long,String> entry : focusRoomMemberInfo.entrySet()) {
            Long memberId = entry.getKey();
            System.out.println("memberId: " + memberId);
            // 타이머가 없는 경우 -유저 그룹 닉네임으로 안받아옴 front에서 따로 하면 가능.
            if (!currentMembersTimers.containsKey(memberId)) {
                TimerDto entryEvent = TimerDto.getEntryEventDtoWithNoTimer();
                entryEvent.setUserId(memberId);
                entryEvent.setNickname(entry.getValue());
                timerData.add(entryEvent);
                timerData.forEach(System.out::println);
                System.out.println("타이머 없음");
            }
            // 타이머가 있는 유저
            else {
                System.out.println("타이머 있음");
                var timer = currentMembersTimers.get(memberId);
                System.out.println(" 널 체크 : "+ (timer == null));
                // Entity -> Dto
                TimerDto timerDto = new TimerDto(timer);
                timerDto.setEvent(TimerEvent.ENTRY);
                timerDto.setNickname(entry.getValue());
                // start인 타이머를 현재 시각 기준으로 계산했을 때의 시간
                timerDto.setCurrentTimeSoFar(timer);
                timerData.add(timerDto);
            }
        }
        timerData.forEach(System.out::println);
        return timerData;
    }

    /**
     * 집중방 id로 Redis에 저장된 참여중인 유저id & nickname 가져오기
     * @param groupId
     * @return Map<Long,String> userId, nickname
     */
    private Map<Long,String> getFocusRoomMemberIdsAndNameFromRedisByGroupId(Long groupId) {
        // userId, nickname
        Map<Long,String> result = new HashMap<>();

        // Redis에서 데이터 id 가져오기
        String focusRoomKey = "focus:" + groupId;
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        Set<Object> memberIdAndNicknameSet = setOps.members(focusRoomKey);
        if (memberIdAndNicknameSet == null) {
            return result;
        }
        // cache Object -> String -> Map<Long,String> 변환 "userId:nickname"
        memberIdAndNicknameSet.forEach(idAndNickname -> {
                    String[] split = idAndNickname.toString().split(":");
                    result.put(Long.parseLong(split[0]),split[1]);
                }
        );
        return result;
    }

    /**
     * 타이머 일시 정지.
     * @param groupId
     * @param userId
     * @return TimerDto 일시 정지된 타이머 정보 반환 혹은 오늘자 새 타이머 반환. (타이머가 없는 경우 null 반환)
     */
    @Transactional
    public TimerDto stopTimerByGroupIdAndUserId(Long groupId, Long userId) {
        // 최신 타이머 조회
        Timer timer = timerRepository.findRecentOneByStudyGroupIdAndUserId(groupId,userId);

        // 날짜 비교
        if (timer != null && timer.getStatus() == TimerStatus.START) {
            // 오늘 시작한 타이머인 경우
            boolean isToday = timer.getStudyDate().equals(LocalDate.now());
            if (isToday) {
                // SSE: 타이머 일시 정지 시 휴식중 상태 send
                studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.RESTING);

                // 타이머 중지
                timer.stop();
                Timer savedTimer = timerRepository.save(timer);

                return savedTimer.toEventTimerDto(TimerEvent.STOP);
            }
            // 타이머가 오늘 시작한 것이 아닌 경우 예) 오후 11시 시작 -> 12시 넘어서 종료
            else {
                // 어제의 타이머 일시정지 없이 종료처리
                timer.end();
                Timer savedFormerTimer = timerRepository.save(timer);

                // 자정 이후의 새로운 타이머 생성
                Timer todayNewTimer = Timer.builder()
                    .user(timer.getUser())
                    .studyDate(savedFormerTimer.getStudyDate().plusDays(1))
                    .studyGroup(timer.getStudyGroup())
                    .status(TimerStatus.STOP)
                    .build();
                todayNewTimer.setExceedTimeSoFarAfterMidnight();
                Timer savedTodayTimer = timerRepository.save(todayNewTimer);

                return savedTodayTimer.toEventTimerDto(TimerEvent.STOP);
            }
        }
        // Timer 조회 실패. null 일 경우
        else {
            log.info("Timer not found");
            return null;
        }
    }

    /**
     * 집중방 id와 사용자 id로 오늘의 타이머 정보 가져오기
     * @param groupId
     * @param UserId
     * @return TimerDto 오늘의 타이머 시작 정보 반환
     */
    @Override
    @Transactional
    public TimerDto startMyTimer(long groupId, long UserId) {
        // SSE: 타이머 시작 시 공부중 상태 send
        studyGroupSseService.sendMemberStatusToGroup(groupId, UserId, ConnectionStatus.STUDYING);

        // [오늘] 시작한 타이머가 있는 경우 (타이머 재개 & n번 째 공부 시작)
        Timer timer = timerRepository.findByStudyGroupIdAndUserIdAndToday(groupId, UserId);
        if (timer != null) {
            System.out.println(" 오늘 공부 재시작! ");
            // 타이머 시작
            timer.start();
            Timer savedTimer = timerRepository.save(timer);

            return savedTimer.toEventTimerDto(TimerEvent.START);
        }

        System.out.println(" 오늘의 첫 공부 시작! ");
        // 타이머가 없는 경우 (새로운 타이머 시작)
        // 연관관계 가져오기.
        User user= userRepository.findById(UserId).get();
        StudyGroup studyGroup = studyGroupRepository.findById(groupId).get();

        // Entity 생성
        Timer newTimer = Timer.builder()
                .user(user)
                .studyDate(LocalDate.now())
                .studyGroup(studyGroup)
                .build();

        newTimer.start();

        // 저장
        Timer savedTimer = timerRepository.save(newTimer);

        // Dto로 변환
        return savedTimer.toEventTimerDto(TimerEvent.START);
    }

    /**
     * 집중방에서 내 타이머 종료 시, 그룹 id와 사용자 id로 타이머 종료.
     * 2일 넘어가는 타이머 중지 시, 아직 예외 처리 없음
     * @param groupId
     * @param userId
     * @return TimerDto 종료된 타이머 정보 반환
     */
    @Override
    @Transactional
    public TimerDto endTimerByGroupIdAndUserId(Long groupId, Long userId) {
        // 최신 타이머 조회
        Timer timer = timerRepository.findRecentOneByStudyGroupIdAndUserId(groupId,userId);
        if (timer == null) {
            log.info("Timer not found");
            return null;
        }else {
            // SSE: 타이머 종료 시 접속중 상태 send
            studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.ONLINE);

            // 타이머 종료
            timer.end();
            Timer savedTimer = timerRepository.save(timer);

            // 정지 된 타이머 3일 뒤에 종료 시, 그 다음 날 타이머 생성 저장 종료
            if (timer.getStatus().equals(TimerStatus.START) && timer.getStudyDate().isBefore(LocalDate.now())) {
                log.info("Timer exceeds midnight");
                Timer newDayOfTimer = Timer.builder()
                        .studyGroup(timer.getStudyGroup())
                        .user(timer.getUser())
                        .studyDate(timer.getStudyDate().plusDays(1))
                        .build();
                newDayOfTimer.setExceedTimeSoFarAfterMidnight();
                newDayOfTimer.end();
                return timerRepository.save(newDayOfTimer).toEventTimerDto(TimerEvent.END);
            }
            return savedTimer.toEventTimerDto(TimerEvent.END);
        }
    }

    /**
     * 집중방에서 사용 할 타이머 데이터를 그룹방 id와 사용자 id로 타이머 정보 가져오기
     * 추후 필요한 타이머 데이터를 추가할 수 있음 ( 총 공부시간, 랭킹 등)
     * 최신 타이머 조회 - 오래 전 타이머도 불러옴. 다만 오늘 날짜가 아닌 timer start 시,
     * 이전 타이머는 저장되고 오늘자 타이머를 불러옴
     * @param groupId
     * @param userId
     * @return TimerDto 입장용 event dto
     */
    @Override
    @Transactional
    public TimerDto getMyTimerByGroupIdAndUserId(long groupId, long userId) {
        // 최신 타이머 조회
        Timer timer = timerRepository.findRecentOneByStudyGroupIdAndUserId(groupId,userId);
        System.out.println("최신 타이머 가져옴 TimerServiceImpl");
        log.info("Timer: {}", timer);
        return timer == null ? null : timer.toEventTimerDto(TimerEvent.ENTRY);
    }

    @Override
    @Transactional
    public TimerDto enterFocusRoom(Long groupId) {
        // 사용자 정보 가져오기
        UserResponse userResponse = getLoginUserInfo();
        Long userId = userResponse.getId();
        log.info("User: {}", userResponse);
        // 그룹 멤버 정보 가져오기
        GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId,userId)
                .orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));

        if (userResponse == null || groupMember == null) {
            return null;
        }
        String redisKey = "focus:" + groupId;
        String nickname = groupMember.getNickname();

        // Redis에 유저 입장 정보 저장
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        setOps.add(redisKey, userId.toString()+":"+nickname);

        // TTL 설정
        redisTemplate.expire(redisKey, Duration.ofDays(1));

        System.out.println("User " + userId + " joined group 임플" + groupId);

        // 내 오늘자 타이머 정보 가져오기
        // 가장 최신 timer 객체 불러오기
        TimerDto entryEvent = getMyTimerByGroupIdAndUserId(groupId, userId);
        // 타이머가 없는 경우, 새로 입장한 사용자임. Entry 이벤트 객체 생성
        if (entryEvent == null) {
            System.out.println("타이머 없음 새로 입장~");
            entryEvent = new TimerDto();
            entryEvent.setEvent(TimerEvent.ENTRY);
            entryEvent.setUserId(userId);
            entryEvent.setNickname(nickname);
            entryEvent.setTimeSoFar(0);
            entryEvent.setStatus("REST");
        }
        entryEvent.setNickname(nickname);

        return entryEvent;

    }

    /**
     * 현재 로그인한 사용자의 id를 가져오기
     * @return Long userId
     */
    private UserResponse getLoginUserInfo() {
        System.out.println("로그인 정보 가져옴");
        UserResponse principal = (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Principal2: {}", principal);
        return (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

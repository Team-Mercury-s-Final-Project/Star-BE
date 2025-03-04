package com.mercury.star_be.global.error.code;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyGroupErrorCode implements ErrorCode {

	// 스터디 그룹 관련 에러
	STUDY_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 스터디 그룹입니다."),
	STUDY_GROUP_IS_FULL(HttpStatus.CONFLICT, "스터디 그룹이 가득 찬 상태입니다."),
	USER_ALREADY_EXIST_IN_GROUP(HttpStatus.CONFLICT, "이미 유저가 가입한 그룹입니다."),
	USER_NOT_EXIST_IN_GROUP(HttpStatus.CONFLICT, "유저가 가입되지 않은 그룹입니다."),
	USER_NOT_HOST(HttpStatus.UNAUTHORIZED, "그룹장 권한이 없습니다."),
	STUDY_GROUP_IS_EMPTY(HttpStatus.CONFLICT, "스터디 그룹에 유저가 없습니다."),
	INVALID_GROUP_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다"),
	INVALID_MAX_CAPACITY(HttpStatus.BAD_REQUEST, "최대 인원이 현재 인원보다 작을 수 없습니다."),

	// SSE 관련 에러
	SSE_EMITTER_IO_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 SSE 메시지를 보내는 중 오류가 발생했습니다.")
	;

	private final HttpStatus httpStatus;
	private final String message;
}

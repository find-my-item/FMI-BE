# 토큰 예외 처리 리팩터링 계획

## 배경

현재 refresh 실패는 예외 처리 체계를 사용하지 않고 `AuthController.refresh()`에서 직접 응답으로 변환한다.

- refresh token 쿠키가 없으면 Controller가 오류 코드와 메시지를 문자열로 작성해 401 응답을 반환한다.
- `TokenIssuer.refresh()`는 실패 원인을 `RefreshFailure` enum으로 반환한다.
- Controller의 `refreshFailureMessage()`가 실패 원인을 메시지로 변환하고 다시 401 응답을 만든다.

이 구조에서는 `ExceptionAdvice`와 `ErrorStatus`가 refresh 실패 응답에 사용되지 않는다. Controller가 토큰 실패 종류와 응답 형식을 모두 알아야 하며, 오류 코드와 메시지도 코드 여러 위치에 나뉜다.

logout은 오류 응답을 직접 만들지는 않지만 Controller에서 refresh token 유무를 확인해 `revoke()` 호출 여부를 분기한다. 유효하지 않은 토큰은 `TokenIssuer`에서 조용히 무시하고 Controller는 항상 성공 응답과 만료 쿠키를 반환한다.

## 목표

- refresh 실패를 `RefreshResult`가 아닌 예외로 표현한다.
- `RefreshFailure`와 Controller의 `refreshFailureMessage()`를 제거한다.
- Controller에서 토큰 실패 응답을 직접 만들지 않는다.
- 토큰 오류 코드와 메시지는 `ErrorStatus`에서 관리한다.
- `ExceptionAdvice`가 토큰 예외를 기존 `ApiResponse` 형식으로 변환한다.
- 토큰 유스케이스는 `TokenService`가 제공하고 실제 발급은 내부 `TokenIssuer`가 담당한다.
- 발급 결과는 `data`의 `IssuedTokens` 값 객체로 관리한다.
- refresh와 logout Controller에는 정상 응답 조립만 남긴다.

## 비목표

- JWT 서명 알고리즘과 claim 구성을 변경하지 않는다.
- access token과 refresh token의 유효 기간을 변경하지 않는다.
- refresh token 저장 및 회전 정책을 변경하지 않는다.
- 쿠키 생성과 조회 구조를 변경하지 않는다.
- 이번 작업에서 JWT 라이브러리의 모든 예외를 세분화하지 않는다.

## 책임 변경

### `TokenService`

Controller에 `issue()`, `refresh()`, `revoke()` 토큰 유스케이스를 제공한다. `issue()`는 내부 발급기가 생성한 refresh token의 hash를 저장한다. `refresh()`는 성공하면 `IssuedTokens`를 반환하고, 실패하면 원인에 맞는 애플리케이션 예외를 던진다.

예외로 전환할 현재 실패 조건은 다음과 같다.

- 유효하지 않거나 만료된 refresh token
- JTI가 없는 refresh token
- 저장된 refresh token hash와 일치하지 않는 토큰
- refresh token의 사용자 정보를 찾을 수 없는 경우

각 실패에서는 Redis의 기존 토큰을 폐기하거나 새 토큰을 저장하지 않는다.

### `TokenIssuer`와 `IssuedTokens`

`TokenIssuer`는 `service.internal`에 두고 access token, refresh token과 JTI를 생성하는 발급 책임만 담당한다. `TokenService.issue()`와 정상적인 `refresh()`가 실제 발급을 `TokenIssuer`에 위임하며, refresh token hash 저장은 공개 유스케이스를 관리하는 `TokenService`가 담당한다. hash 계산만을 위한 별도 객체는 두지 않는다.

발급 결과인 `IssuedTokens`는 서비스 구현 클래스와 분리해 `data`의 값 객체로 관리한다. Controller는 `IssuedTokens`로 쿠키를 생성하지만 HTTP 응답 본문에는 포함하지 않는다.

### `AuthController`

`refresh()`는 `AuthCookieResolver`에서 refresh token을 `Optional`로 조회하고 `TokenService.refresh()`를 호출한 뒤 정상 응답을 만든다. 다음 로직은 제거한다.

- 쿠키 누락 시 직접 생성하는 401 `ResponseEntity`
- `RefreshResult.isSuccess()` 분기
- `refreshFailureMessage()`
- 문자열로 직접 작성한 refresh 오류 코드와 메시지

refresh token 쿠키가 없거나 값이 비어 있으면 Controller가 `Optional.filter()`와 `orElseThrow()`를 사용해 정의된 애플리케이션 예외를 던진다.

logout은 기존 계약대로 항상 성공하고 쿠키를 만료시킨다. Controller는 `Optional.filter()`와 `ifPresent()`로 값이 있는 refresh token만 `TokenService.revoke()`에 전달하고, 토큰 유효성 판단은 `TokenService`가 담당한다.

### `ErrorStatus`와 `ExceptionAdvice`

refresh token 누락과 유효하지 않은 refresh token에 사용할 오류 상태를 `ErrorStatus`에 정의한다. `GeneralException` 또는 합의한 인증 예외가 해당 상태를 가지며, `ExceptionAdvice`가 최종 `ApiResponse`와 HTTP 상태를 만든다.

내부 실패 원인인 JTI 누락과 hash 불일치를 클라이언트 오류 메시지에 그대로 노출하지 않는다. 외부에는 합의한 refresh token 오류로 통일하고, 필요한 진단 정보는 서버 로그나 내부 예외 원인으로만 남긴다.

## 예상 흐름

```text
AuthController.refresh()
  -> AuthCookieResolver에서 refresh cookie를 Optional로 조회
  -> 누락 또는 빈 값이면 토큰 예외 발생
  -> TokenService.refresh()
       -> 실패이면 토큰 예외 발생
       -> 성공이면 내부 TokenIssuer에 새 토큰 발급 위임
       -> 성공이면 IssuedTokens 반환
  -> access/refresh 쿠키 생성
  -> 정상 응답 반환

토큰 예외
  -> ExceptionAdvice
  -> ErrorStatus 기준 ApiResponse 반환
```

## 구현 순서

1. refresh token 누락과 각 `RefreshFailure`의 현재 응답 및 Redis 미변경 동작을 테스트로 고정한다.
2. refresh token 오류에 사용할 `ErrorStatus`와 외부 메시지를 확정한다.
3. `TokenService.refresh()`가 실패 시 예외를 던지고 성공 시 `IssuedTokens`를 반환하도록 변경한다.
4. `RefreshResult`와 `RefreshFailure`를 제거한다.
5. `IssuedTokens`를 `data`의 값 객체로 분리하고, 발급만 담당하는 `TokenIssuer`를 `service.internal`로 이동한다.
6. `AuthController.refresh()`의 직접 오류 응답과 `refreshFailureMessage()`를 제거한다.
7. logout의 누락·무효 토큰 계약에 따라 Controller 분기를 제거하거나 예외 흐름을 적용한다.
8. `ExceptionAdvice`를 통한 상태 코드, 오류 코드, 응답 본문을 Controller 테스트로 검증한다.
9. `spotlessApply`, 관련 테스트, 전체 테스트, `spotlessCheck` 순서로 검증한다.

## 테스트 계획

- refresh token 쿠키가 없으면 `ExceptionAdvice`를 통해 정의된 401 응답을 반환한다.
- 유효하지 않거나 만료된 refresh token은 정의된 토큰 예외로 변환된다.
- JTI 누락과 hash 불일치 시 내부 원인을 외부 응답에 노출하지 않는다.
- refresh token 사용자를 찾을 수 없으면 정의된 인증 오류를 반환한다.
- refresh 실패 시 기존 토큰 폐기와 새 토큰 저장이 발생하지 않는다.
- 유효한 refresh token은 기존 JTI를 폐기하고 새 토큰과 쿠키를 발급한다.
- logout은 확정한 누락·무효 토큰 계약을 유지하면서 access/refresh 쿠키를 만료시킨다.
- Controller에 refresh 실패 메시지 분기와 직접 작성한 오류 코드가 남아 있지 않다.

## 확정 사항

1. refresh token 누락과 무효 토큰은 메시지로 구분하되 기존 `AUTH401-INVALID_REFRESH` 코드를 유지한다.
2. JTI 누락, hash 불일치, 사용자 없음은 외부에 유효하지 않은 refresh token으로 통일한다.
3. 별도 토큰 예외와 `ExceptionAdvice` 처리기를 추가하지 않고 기존 `GeneralException` 처리 흐름을 사용한다.
4. logout은 누락·무효 refresh token을 계속 무시하고 성공한다.

## 완료 기준

- `RefreshResult`, `RefreshFailure`, `refreshFailureMessage()`가 제거된다.
- `IssuedTokens`가 `data`의 독립 값 객체로 관리된다.
- `TokenIssuer`에는 토큰 발급 책임만 남고 Controller는 `TokenService`에만 의존한다.
- refresh 실패 코드와 메시지를 Controller가 직접 작성하지 않는다.
- refresh token 실패가 정의된 예외와 `ErrorStatus`로 표현된다.
- `ExceptionAdvice`가 토큰 오류 응답을 일관된 `ApiResponse`로 변환한다.
- refresh 실패 시 Redis 상태가 변경되지 않는다.
- 정상 refresh와 확정된 logout 동작이 유지된다.
- 관련 테스트와 전체 테스트, `spotlessCheck`가 통과한다.

# 티켓팅 서비스 상세 API 명세

> `API 목록.md`의 API별 요청·응답 형식과 오류 응답을 정의합니다.
> 기능별 동작 규칙은 `기능정의서.md`를 따르며, 해당 항목 번호를 표기합니다.
> API는 구현 시점에 이 문서에 추가합니다.

## 1. 공통 규칙

- Base path: `/api/v1`
- 요청·응답 본문은 JSON을 사용합니다.

### 1.1. 오류 응답

오류 응답은 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) ProblemDetail 형식(`application/problem+json`)을 사용합니다.

| 필드 | 설명 |
|---|---|
| `type` | 항상 `about:blank` |
| `title` | HTTP 상태 이름 (예: `Conflict`) |
| `status` | HTTP 상태 코드 |
| `detail` | 오류 설명 |
| `instance` | 요청 경로 |
| `code` | 오류 코드 (1.2). 비즈니스 오류일 때만 포함됩니다. |

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 사용 중인 이메일입니다.",
  "instance": "/api/v1/members",
  "code": "DUPLICATE_EMAIL"
}
```

### 1.2. 오류 코드

| code | status | detail |
|---|---|---|
| `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일입니다. |

## 2. 회원

### 2.1. 이메일 중복 확인

`GET /api/v1/members/email-availability` · 인증 불필요 · 기능정의서 4.1

#### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `email` | string | O | 확인할 이메일 (형식: 4.1.1) |

#### 응답: `200 OK`

| 필드 | 타입 | 설명 |
|---|---|---|
| `available` | boolean | 가입 가능하면 `true`, 이미 사용 중이면 `false` |

```json
{ "available": true }
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `email`이 없거나 비어 있거나 형식이 올바르지 않음 |

### 2.2. 회원 가입

`POST /api/v1/members` · 인증 불필요 · 기능정의서 4.2

#### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 이메일 (형식: 4.1.1) |
| `password` | string | O | 비밀번호 |

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

#### 응답: `201 Created`

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 회원 식별자 |
| `email` | string | 정규화된 이메일 (4.1.2) |
| `role` | string | 회원 권한 (`USER`) |

```json
{
  "id": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `email` 또는 `password`가 없거나 비어 있음, `email` 형식이 올바르지 않음 |
| `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일 |

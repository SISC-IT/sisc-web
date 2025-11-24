# 출석 체크인 시스템 - Swagger 테스트 가이드

## 📋 문제 상황

Swagger에서 세션과 라운드 생성은 성공하지만, 체크인 시도 시 **"출석 시간 초과 (시간 초과됨)"** 오류가 발생합니다.

**원인**: 라운드의 `roundDate`(라운드 진행 날짜)가 **현재 날짜**와 **정확히 일치**해야만 체크인이 가능합니다.

---

## ⚙️ 시간 검증 로직

### 출석 체크인이 가능한 조건

```
1. 날짜 조건: 현재 날짜 == 라운드 날짜
2. 시간 조건: 라운드 시작시간 <= 현재시간 < 라운드 종료시간
3. 종료시간 = 시작시간 + 허용분(allowedMinutes)
```

**실제 코드 (AttendanceRound.isCheckInAvailable())**
```java
public boolean isCheckInAvailable() {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();

    if (!today.equals(roundDate)) {  // ← 반드시 현재 날짜와 일치해야 함!
        return false;
    }

    return !now.isBefore(startTime) && now.isBefore(getEndTime());
}
```

---

## ✅ Swagger에서 올바르게 테스트하기

### Step 1: 세션 생성
```
POST /api/sessions
Content-Type: application/json

{
  "title": "테스트 세션",
  "startsAt": "2025-11-24T17:00:00Z",
  "windowSeconds": 1800
}
```

**응답 예시:**
```json
{
  "attendanceSessionId": "9f45e8d3-dbf2-4a5e-a729-c58bdc8c7966",
  "code": "856329",
  "title": "테스트 세션",
  "startsAt": "2025-11-24T17:00:00Z"
}
```

---

### Step 2: 라운드 생성 (⚠️ 핵심 부분)

**"시간 초과" 오류를 피하려면:**
1. `roundDate`: **오늘 날짜**를 입력하세요 (예: `2025-11-24`)
2. `startTime`: **이미 지난 시간**을 입력하세요
   - 현재 시간보다 5분 이상 전이어야 합니다
   - 예: 현재가 17:49라면, startTime을 17:44 이전으로 설정

```
POST /api/sessions/{sessionId}/rounds
Content-Type: application/json

{
  "roundDate": "2025-11-24",                    ← 반드시 TODAY
  "startTime": "17:44:00",                      ← 과거 시간
  "allowedMinutes": 30
}
```

#### ❌ 잘못된 예시 1: 내일 날짜
```json
{
  "roundDate": "2025-11-25",                    ← ❌ 오류! 미래 날짜
  "startTime": "10:00:00",
  "allowedMinutes": 30
}
```
**결과**: `출석 시간 초과` (날짜 불일치)

#### ❌ 잘못된 예시 2: 너무 오래된 시작 시간
```json
{
  "roundDate": "2025-11-24",
  "startTime": "10:00:00",                      ← ❌ 오류! 이미 30분 이상 지남
  "allowedMinutes": 30
}
```
**결과**: `출석 시간 초과` (시간 범위 초과)

#### ✅ 올바른 예시
```json
{
  "roundDate": "2025-11-24",                    ← ✅ 오늘 날짜
  "startTime": "17:44:00",                      ← ✅ 최근 과거 시간
  "allowedMinutes": 30                          ← 지금으로부터 ~30분 이내
}
```

**현재 시간이 17:49:33일 때:**
- ✅ 체크인 가능: 17:44:00 ~ 18:14:00 범위 내
- ❌ 체크인 불가: 18:14:00 이후

---

### Step 3: 사용자 생성
```
POST /api/users
Content-Type: application/json

{
  "username": "user_001",
  "email": "test@example.com",
  "password": "password123"
}
```

---

### Step 4: 출석 체크인

```
POST /api/rounds/{roundId}/check-in
Content-Type: application/json

{
  "latitude": 37.4979,                          ← 위치 정보
  "longitude": 127.0276,
  "userName": "테스트 사용자"                    ← 선택사항 (익명 사용자)
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "attendanceId": "7001af87-c68b-4dd7-8dbc-104dff79a0d6",
  "attendanceStatus": "LATE",                   ← PRESENT 또는 LATE
  "pointsRewarded": 100,
  "checkInTime": "2025-11-24T17:49:33.940306"
}
```

**실패 응답 (400 Bad Request):**
```json
{
  "success": false,
  "failureReason": "출석 시간 초과"
}
```

---

## 📊 출석 상태 판별 로직

라운드 시작 후 몇 분이 지났는지에 따라 자동으로 판별됩니다:

```
시작시간 <= 체크인시간 < (시작시간 + 5분)     → PRESENT (출석)
시작시간 + 5분 <= 체크인시간 < 종료시간       → LATE (지각)
체크인시간 >= 종료시간                        → 체크인 불가
```

**예시:**
- 라운드 시작: 17:44:00
- 지각 기준: 17:49:00 (시작 + 5분)
- 라운드 종료: 18:14:00 (시작 + 30분)

| 체크인 시간 | 상태 | 포인트 |
|-----------|------|--------|
| 17:44:30  | PRESENT | 100 |
| 17:48:30  | PRESENT | 100 |
| 17:49:00  | LATE | 100 |
| 17:50:00  | LATE | 100 |
| 18:15:00  | ❌ 불가능 | - |

---

## 🎯 최적의 테스트 설정

**현재 시간이 17:49:33일 때 추천 설정:**

```json
{
  "roundDate": "2025-11-24",
  "startTime": "17:44:00",
  "allowedMinutes": 30
}
```

**결과:**
- 라운드 시간: 17:44:00 ~ 18:14:00
- PRESENT 범위: 17:44:00 ~ 17:48:59
- LATE 범위: 17:49:00 ~ 18:13:59
- 현재 (17:49:33)에서 체크인하면 → **LATE** 판정

---

## 🔍 문제 해결 체크리스트

| 문제 | 원인 | 해결방법 |
|------|------|--------|
| "출석 시간 초과" | roundDate가 오늘이 아님 | roundDate를 현재 날짜로 변경 |
| "출석 시간 초과" | startTime이 너무 오래됨 | startTime을 최근 시간으로 변경 (예: 17:44) |
| "출석 시간 초과" | allowedMinutes가 너무 짧음 | allowedMinutes를 30 이상으로 설정 |
| "중복 출석 오류" | 같은 사용자가 이미 체크인함 | 다른 사용자로 테스트하거나 새 라운드 생성 |

---

## 📝 테스트 체크리스트

```
[ ] 1. 세션 생성 (POST /api/sessions)
[ ] 2. 라운드 생성 (POST /api/sessions/{sessionId}/rounds)
       - roundDate: 현재 날짜 (2025-11-24)
       - startTime: 최근 과거 시간 (17:44 또는 그 이전)
       - allowedMinutes: 30
[ ] 3. 사용자 생성 (POST /api/users)
[ ] 4. 출석 체크인 (POST /api/rounds/{roundId}/check-in)
       - 성공 여부 확인
       - 출석 상태 (PRESENT 또는 LATE) 확인
       - 포인트 부여 확인 (100점)
[ ] 5. 같은 라운드에서 다시 체크인 시도
       - "중복 출석 오류" 메시지 확인 (정상)
```

---

## 💡 핵심 요점

1. **라운드 생성 시 반드시 오늘 날짜 사용**
   - `roundDate`는 YYYY-MM-DD 형식 (예: "2025-11-24")

2. **시작 시간은 과거 시간 사용**
   - startTime은 ISO 8601 형식 (예: "17:44:00" 또는 "17:44:33.762391")
   - 현재 시간보다 5분 이상 전이어야 함

3. **허용 시간 충분히 설정**
   - allowedMinutes는 최소 5분 이상 (보통 20~30분 권장)

4. **체크인 시도는 라운드 생성 직후**
   - 라운드 생성 후 5~30분 이내에 체크인해야 함

---

## 🔗 관련 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/sessions` | 세션 생성 |
| GET | `/api/sessions` | 공개 세션 조회 |
| POST | `/api/sessions/{sessionId}/rounds` | 라운드 생성 |
| GET | `/api/sessions/{sessionId}/rounds` | 라운드 조회 |
| POST | `/api/rounds/{roundId}/check-in` | 출석 체크인 |
| GET | `/api/attendances` | 출석 기록 조회 |

---

## 📞 문제가 여전히 발생하면?

이 가이드대로 따랐는데도 "출석 시간 초과" 오류가 발생하면:

1. **라운드의 `roundDate` 확인**
   ```bash
   GET /api/sessions/{sessionId}/rounds/{roundId}
   ```
   응답의 `roundDate`가 현재 날짜(2025-11-24)와 정확히 일치하는지 확인

2. **라운드의 `startTime` 확인**
   - 응답의 `startTime`이 현재 시간보다 과거인지 확인
   - 현재 시간과 startTime의 차이가 allowedMinutes보다 작은지 확인

3. **현재 시스템 시간 확인**
   ```bash
   GET /api/health
   ```
   또는 서버 로그에서 현재 시간 확인


package org.sejongisc.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.auth.dto.SignupRequest;
import org.sejongisc.backend.common.auth.entity.UserOauthAccount;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User extends BasePostgresEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    // 로그인 시 이메일이 아닌 학번 입력
    @Column(name = "student_id", unique = true, nullable = false)
    private String studentId; // 학번: 엑셀 매칭 및 계정 식별의 핵심 키

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    // --- 엑셀 장부 기반 추가 데이터 ---
    private String college;      // 단과대학
    private String department;   // 학과
    private Integer generation;  // 기수 (처음 활동한 연도 기준)
    private String teamName;     // 활동팀 (예: 매크로팀, 리서치팀)

    @Enumerated(EnumType.STRING)
    private Gender gender;       // 성별

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade; // 신입/준/정회원

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "position_name")     // 엑셀의 '직위' 컬럼 데이터 그대로 저장
    private String positionName;

    //OAuth 전용 계정 대비 nullable 허용 가능, 확장성 문제로 citext 설정 보류
    @Column(unique = true, nullable = true)
    private String email;               // 추후 비밀번호 찾기용 및 공지 발송용, citext 형식이 아니기 때문에 대소문자 구별 불가능 주의!!

    @Enumerated(EnumType.STRING)        // 새 장부 업로드 시: 기존에 ACTIVE한 모든 인원을 INACTIVE로 일괄 업데이트
    @Column(nullable = false)           // 새 엑셀에 있는 studentId을 대조하여, 명단에 있는 사람만 다시 ACTIVE로 바꾸고
    @Builder.Default                    // generation(기수)과 positionName(직위)을 최신화
    private UserStatus status = UserStatus.ACTIVE; // 활동 상태 (ACTIVE, INACTIVE, GRADUATED, OUT 등)

    @Column(columnDefinition = "integer default 0",nullable = false)
    private Integer point;

    // 포인트 총량 업데이트를 위한 낙관적 락 버전 필드
    @Version
    private Long version;

    // User : OAuthAccounts = 1 : N(여러 OAuth를 연결 가능)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<UserOauthAccount> oauthAccounts = new ArrayList<>();

    // 권한 확인용 편의 메서드
    public boolean isManagerPosition() {
        if (this.positionName == null) return false;
        // 직위에 '팀장', '대표', '부대표' 등의 키워드가 있으면 운영진 권한 부여 후보
        return this.positionName.contains("팀장") ||
            this.positionName.contains("대표") ||
            this.positionName.contains("회장");
    }

    // 기본값 지정
    @PrePersist
    public void prePersist() {
        if (this.role == null) {
            this.role = Role.PENDING_MEMBER;
        }
        if (this.point == null) {
            this.point = 0;
        }
    }
    public void updatePoint(int amount) {
        this.point += amount;
    }

    public static User createUserWithSignupAndPending(SignupRequest request, String encodedPw) {
        return User.builder()
            .role(Role.TEAM_MEMBER)     // TODO : 운영진 승인 로직 추가 후 PENDING_MEMBER로 변경 필요
            .studentId(request.getStudentId())
            .name(request.getName())
            .passwordHash(encodedPw)
            .phoneNumber(request.getPhoneNumber())
            .email(request.getEmail())
            .gender(request.getGender())
            .college(request.getCollege())          // 단과대
            .department(request.getDepartment())    // 학과
            .generation(request.getGeneration())    //
            .teamName(request.getTeamName())        // 소속 팀명
            .isNewMember(true)                      // 신규 가입자
            .point(0)
            .status(UserStatus.ACTIVE)              // 기본 활동 상태
            .build();
    }

    public void applyExcelData(
        String name,
        String phone,
        String teamName,
        Integer generation,
        String college,
        String department,
        Grade grade,
        String position,
        Role role,
        Gender gender
    ) {
        this.name = name;
        this.phoneNumber = phone;
        this.teamName = teamName;
        this.generation = generation;
        this.college = college;
        this.department = department;
        this.status = UserStatus.ACTIVE;
        this.grade = grade;
        this.positionName = position;
        this.role = role;
        this.gender = gender;
    }

}

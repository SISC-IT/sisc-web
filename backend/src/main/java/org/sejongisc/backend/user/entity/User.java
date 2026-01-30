package org.sejongisc.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
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

    //OAuth 전용 계정 대비 nullable 허용 가능
    @Column(columnDefinition = "citext", unique = true, nullable = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "student_id", unique = true, nullable = false)
    private String studentId; // 학번: 엑셀 매칭 및 계정 식별의 핵심 키

    @Column(name = "phone_number")
    private String phoneNumber;

    // --- 엑셀 장부 기반 추가 데이터 ---
    private String college;      // 단과대학
    private String department;   // 학과
    private Integer generation;  // 기수 (처음 활동한 연도 기준)
    private String teamName;     // 활동팀 (예: 매크로팀, 리서치팀)

    @Enumerated(EnumType.STRING)
    private Gender gender;       // 성별

    @Column(nullable = false)
    private boolean isNewMember; // 신규 여부 (포인트나 이벤트 대상자 선정용)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE; // 활동 상태 (ACTIVE, INACTIVE, GRADUATED, OUT 등)
    // 새 장부 업로드 시: 기존에 ACTIVE한 모든 인원을 INACTIVE로 일괄 업데이트
    // 새 엑셀에 있는 studentId을 대조하여, 명단에 있는 사람만 다시 ACTIVE로 바꾸고
    // generation(기수)과 positionName(직위)을 최신화

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

    @Column(name = "position_name") // 엑셀의 '직위' 컬럼 데이터 그대로 저장
    private String positionName;

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
            this.role = Role.TEAM_MEMBER;
        }
        if (this.point == null) {
            this.point = 0;
        }
    }
    public void updatePoint(int amount) {
        this.point += amount;
    }
}

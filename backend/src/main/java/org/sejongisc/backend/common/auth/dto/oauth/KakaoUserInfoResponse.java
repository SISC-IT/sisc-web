package org.sejongisc.backend.common.auth.dto.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
        name = "KakaoUserInfoResponse",
        description = "카카오 OAuth 로그인 후 사용자 정보 응답 객체"
)
public class KakaoUserInfoResponse {

    @Schema(description = "카카오 회원 고유 번호", example = "2834928349")
    @JsonProperty("id")
    public Long id;

    @Schema(description = "카카오 계정이 서비스에 연결되어 있는지 여부", example = "true")
    @JsonProperty("has_signed_up")
    public Boolean hasSignedUp;

    @Schema(description = "서비스에 연결 완료된 시각 (UTC)", example = "2024-11-02T04:11:00Z")
    @JsonProperty("connected_at")
    public Date connectedAt;

    @Schema(description = "카카오싱크 간편가입을 통해 로그인한 시각 (UTC)", example = "2024-11-02T04:12:00Z")
    @JsonProperty("synched_at")
    public Date synchedAt;

    @Schema(description = "사용자 프로퍼티 (커스텀 속성 key-value)", example = "{\"nickname\": \"홍길동\"}")
    @JsonProperty("properties")
    public HashMap<String, String> properties;

    @Schema(description = "카카오 계정 관련 정보 객체")
    @JsonProperty("kakao_account")
    public KakaoAccount kakaoAccount;

    /* ============================ 내부 클래스: KakaoAccount ============================ */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "카카오 계정 정보")
    public static class KakaoAccount {

        @Schema(description = "프로필 정보 제공 동의 여부", example = "true")
        @JsonProperty("profile_needs_agreement")
        public Boolean isProfileAgree;

        @Schema(description = "프로필 정보 객체")
        @JsonProperty("profile")
        public Profile profile;

        @Schema(description = "이름 제공 동의 여부", example = "false")
        @JsonProperty("name_needs_agreement")
        public Boolean isNameAgree;

        @Schema(description = "카카오 계정 이름", example = "홍길동")
        @JsonProperty("name")
        public String name;

        @Schema(description = "이메일 제공 동의 여부", example = "true")
        @JsonProperty("email_needs_agreement")
        public Boolean isEmailAgree;

        @Schema(description = "이메일 유효 여부", example = "true")
        @JsonProperty("is_email_valid")
        public Boolean isEmailValid;

        @Schema(description = "이메일 인증 여부", example = "true")
        @JsonProperty("is_email_verified")
        public Boolean isEmailVerified;

        @Schema(description = "대표 이메일", example = "honggildong@kakao.com")
        @JsonProperty("email")
        public String email;

        @Schema(description = "연령대 제공 동의 여부", example = "false")
        @JsonProperty("age_range_needs_agreement")
        public Boolean isAgeAgree;

        @Schema(description = "연령대", example = "20~29")
        @JsonProperty("age_range")
        public String ageRange;

        @Schema(description = "출생 연도 제공 동의 여부", example = "false")
        @JsonProperty("birthyear_needs_agreement")
        public Boolean isBirthYearAgree;

        @Schema(description = "출생 연도 (YYYY)", example = "1998")
        @JsonProperty("birthyear")
        public String birthYear;

        @Schema(description = "생일 제공 동의 여부", example = "false")
        @JsonProperty("birthday_needs_agreement")
        public Boolean isBirthDayAgree;

        @Schema(description = "생일 (MMDD)", example = "0815")
        @JsonProperty("birthday")
        public String birthDay;

        @Schema(description = "생일 타입 (SOLAR: 양력, LUNAR: 음력)", example = "SOLAR")
        @JsonProperty("birthday_type")
        public String birthDayType;

        @Schema(description = "성별 제공 동의 여부", example = "true")
        @JsonProperty("gender_needs_agreement")
        public Boolean isGenderAgree;

        @Schema(description = "성별 (male 또는 female)", example = "male")
        @JsonProperty("gender")
        public String gender;

        @Schema(description = "전화번호 제공 동의 여부", example = "true")
        @JsonProperty("phone_number_needs_agreement")
        public Boolean isPhoneNumberAgree;

        @Schema(description = "전화번호 (+82 형식)", example = "+82 10-1234-5678")
        @JsonProperty("phone_number")
        public String phoneNumber;

        @Schema(description = "CI 제공 동의 여부", example = "false")
        @JsonProperty("ci_needs_agreement")
        public Boolean isCIAgree;

        @Schema(description = "CI (연계 정보)", example = "EXAMPLECI1234567890")
        @JsonProperty("ci")
        public String ci;

        @Schema(description = "CI 인증 시각 (UTC)", example = "2024-11-02T04:15:00Z")
        @JsonProperty("ci_authenticated_at")
        public Date ciCreatedAt;

        /* ============================ 내부 클래스: Profile ============================ */
        @Getter
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        @Schema(description = "사용자 프로필 정보")
        public static class Profile {

            @Schema(description = "사용자 닉네임", example = "길동이")
            @JsonProperty("nickname")
            public String nickName;

            @Schema(description = "프로필 미리보기 이미지 URL", example = "http://k.kakaocdn.net/dn/abc123/img_110x110.jpg")
            @JsonProperty("thumbnail_image_url")
            public String thumbnailImageUrl;

            @Schema(description = "프로필 이미지 URL", example = "http://k.kakaocdn.net/dn/xyz123/img_640x640.jpg")
            @JsonProperty("profile_image_url")
            public String profileImageUrl;

            @Schema(description = "기본 프로필 이미지 여부", example = "false")
            @JsonProperty("is_default_image")
            public boolean isDefaultImage;

            @Schema(description = "기본 닉네임 여부", example = "false")
            @JsonProperty("is_default_nickname")
            public Boolean isDefaultNickName;
        }
    }
}

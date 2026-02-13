package org.sejongisc.backend.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.admin.dto.ExcelSyncResponse;
import org.sejongisc.backend.admin.dto.UserExcelRow;
import org.sejongisc.backend.common.annotation.OptimisticRetry;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.point.service.AccountService;
import org.sejongisc.backend.point.service.PointLedgerService;
import org.sejongisc.backend.user.entity.*;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserSyncService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final PointLedgerService pointLedgerService;

    /**
     * 엑셀로부터 추출된 사용자 데이터를 DB와 동기화
     */
    @Transactional
    @OptimisticRetry
    public ExcelSyncResponse syncMemberData(List<UserExcelRow> excelRows) {
        int createdCount = 0;
        int updatedCount = 0;

        // 기존 활동 인원 일괄 비활성화
        userRepository.findAllByStatus(UserStatus.ACTIVE)
            .forEach(user -> user.setStatus(UserStatus.INACTIVE));

        for (UserExcelRow rowData : excelRows) {
            Optional<User> existingUser = userRepository.findByStudentId(rowData.studentId());
            boolean isNew = existingUser.isEmpty();

            // 기존 사용자 조회, 없으면 신규 사용자 생성
            User user = existingUser.orElseGet(() -> User.builder()
                .studentId(rowData.studentId())
                .passwordHash(passwordEncoder.encode(rowData.phone()))
                .build());

            // 엑셀 데이터 매핑 및 ACTIVE 상태 복구
            updateUserFromRow(user, rowData);
            User savedUser = userRepository.save(user);

            if (isNew) {
                createdCount++;
                // 계정 생성 및 가입 보상 포인트 지급
                Account userAccount = accountService.createUserAccount(savedUser.getUserId());
                pointLedgerService.processTransaction(
                    TransactionReason.SIGNUP_REWARD,
                    savedUser.getUserId(),
                    AccountEntry.credit(accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE), 100L),
                    AccountEntry.debit(userAccount, 100L)
                );
                log.info("신규 사용자 자동 가입 및 포인트 지급 완료: {}", user.getStudentId());
            } else {
                updatedCount++;
            }
        }

        log.info("엑셀 사용자 데이터 동기화 완료: 신규 등록={}, 갱신={}", createdCount, updatedCount);
        return new ExcelSyncResponse(createdCount, updatedCount);
    }

    /**
     * 엑셀 행 데이터를 유저 엔티티 필드에 매핑
     */
    private void updateUserFromRow(User user, UserExcelRow rowData) {
        Integer generation = parseGeneration(rowData.generation());
        Grade grade = Grade.fromString(rowData.grade());
        Role role = Role.fromPosition(rowData.position());
        Gender gender = Gender.fromString(rowData.gender());

        user.applyExcelData(
            rowData.name(),
            rowData.phone(),
            rowData.teamName(),
            generation,
            rowData.college(),
            rowData.department(),
            grade,
            rowData.position(),
            role,
            gender
        );
    }

    /**
     * 기수 문자열에서 숫자만 추출하여 파싱
     */
    private int parseGeneration(String genStr) {
        String clean = genStr.replaceAll("[^0-9]", "");
        return clean.isEmpty() ? 0 : Integer.parseInt(clean);
    }
}
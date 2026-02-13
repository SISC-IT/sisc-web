package org.sejongisc.backend.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sejongisc.backend.admin.dto.AdminUserRequest;
import org.sejongisc.backend.admin.dto.AdminUserResponse;
import org.sejongisc.backend.admin.dto.ExcelSyncResponse;
import org.sejongisc.backend.admin.dto.UserExcelRow;
import org.sejongisc.backend.admin.repository.AdminUserRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.AccountType;
import org.sejongisc.backend.user.entity.*;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final AdminUserRepository adminUserRepository;
    private final AdminUserSyncService adminUserSyncService;
    private final UserService userService;
    private final DataFormatter formatter = new DataFormatter();

    /**
     * 엑셀 파일을 읽어 동기화 프로세스 시작
     */
    public ExcelSyncResponse syncUsersFromExcel(MultipartFile file) {
        // 엑셀 파일 검증
        validateFile(file);
        List<UserExcelRow> excelRows = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 학번 없으면 빈 행으로 간주
                String studentId = getCellValue(row, 4);
                if (studentId.isEmpty()) continue;

                // 필수값 검증 및 UserExcelRow 리스트에 추가
                excelRows.add(buildExcelRow(row, studentId, i));
            }

            // 추가할 내용이 없는 빈 파일의 경우 예외
            if (excelRows.isEmpty()) {
                throw new CustomException(ErrorCode.EMPTY_FILE);
            }
            log.info("엑셀 파일 파싱 완료: 파일명={}, 총 건수={}", file.getOriginalFilename(), excelRows.size());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("엑셀 동기화 중 오류 발생: ", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return adminUserSyncService.syncMemberData(excelRows);
    }

    /**
     * 관리자 필터 조건(키워드, 기수, 권한, 상태)에 따른 사용자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAllUsers(AdminUserRequest request) {
        return adminUserRepository.findAllByAdminFilter(
            request.keyword(), request.generation(), request.role(), request.status(), AccountType.USER
        );
    }

    /**
     * 특정 사용자의 활동 상태 변경
     */
    @Transactional
    public void updateUserStatus(UUID userId, UserStatus status) {
        userService.updateUserStatus(userId, status);
    }

    /**
     * 특정 사용자의 시스템 권한(Role) 변경
     */
    @Transactional
    public void updateUserRole(UUID userId, Role role) {
        userService.updateUserRole(userId, role);
    }

    /**
     * 특정 사용자를 선배(SENIOR) 등급으로 변경
     */
    @Transactional
    public void promoteToSenior(UUID userId) {
        userService.promoteToSenior(userId);
    }

    /**
     * 사용자 계정 삭제 (soft delete)
     */
    @Transactional
    public void deleteUser(UUID userId) {
        userService.deleteUserSoftDelete(userId);
        log.info("관리자에 의한 강제 탈퇴 완료: userId={}", userId);
    }

    /**
     * 엑셀 파일의 셀을 문자열로 변환
     */
    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) return "";

        return formatter.formatCellValue(cell).trim();
    }

    /**
     * 엑셀 파일 검증
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CustomException(ErrorCode.EMPTY_FILE);

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.endsWith(".xlsx")) throw new CustomException(ErrorCode.INVALID_FILE_FORMAT);
    }

    /**
     * 엑셀의 특정 행을 읽어 UserExcelRow 생성
     */
    private UserExcelRow buildExcelRow(Row row, String studentId, int rowIndex) {
        String name = getCellValue(row, 3);
        String phone = getCellValue(row, 5);
        String team = getCellValue(row, 1);
        String grade = getCellValue(row, 8);

        // 학번은 있지만 필수 데이터가 누락된 경우
        if (name.isEmpty() || phone.isEmpty() || team.isEmpty() || grade.isEmpty()) {
            log.error("엑셀 데이터 누락: 행 번호={}, 학번={}", rowIndex + 1, studentId);
            throw new CustomException(ErrorCode.INVALID_EXCEL_STRUCTURE);
        }

        return UserExcelRow.builder()
            .studentId(studentId)
            .name(name)
            .phone(phone.replaceAll("[^0-9]", ""))
            .teamName(team)
            .generation(getCellValue(row, 2))
            .college(getCellValue(row, 6))
            .department(getCellValue(row, 7))
            .grade(grade)
            .position(getCellValue(row, 9))
            .gender(getCellValue(row, 10))
            .build();
    }
}
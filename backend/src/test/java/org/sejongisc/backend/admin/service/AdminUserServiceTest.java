package org.sejongisc.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.admin.dto.ExcelSyncResponse;
import org.sejongisc.backend.admin.dto.UserExcelRow;
import org.sejongisc.backend.admin.repository.AdminUserRepository;
import org.sejongisc.backend.common.config.UploadProperties;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @Mock
  private AdminUserRepository adminUserRepository;

  @Mock
  private AdminUserSyncService adminUserSyncService;

  @Mock
  private UserService userService;

  private AdminUserService adminUserService;
  private UploadProperties uploadProperties;

  @BeforeEach
  void setUp() {
    uploadProperties = new UploadProperties();
    uploadProperties.setAdminExcelMaxSize(DataSize.ofMegabytes(5));
    adminUserService = new AdminUserService(
        adminUserRepository,
        adminUserSyncService,
        userService,
        uploadProperties
    );
  }

  @Test
  @DisplayName("정상 xlsx 파싱 후 동기화 서비스 전달")
  void syncUsersFromExcel_success() throws IOException {
    when(adminUserSyncService.syncMemberData(anyList())).thenReturn(new ExcelSyncResponse(1, 0));
    MockMultipartFile file = xlsxFile("members.xlsx", workbookBytes(1));

    ExcelSyncResponse response = adminUserService.syncUsersFromExcel(file);

    assertThat(response.createdCount()).isEqualTo(1);
    ArgumentCaptor<List<UserExcelRow>> captor = ArgumentCaptor.forClass(List.class);
    verify(adminUserSyncService).syncMemberData(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().getFirst().studentId()).isEqualTo("20260001");
  }

  @Test
  @DisplayName("엑셀 xlsx 외 확장자 거절")
  void syncUsersFromExcel_nonXlsx_throwException() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "members.xls",
        "application/vnd.ms-excel",
        "not-xlsx".getBytes(StandardCharsets.UTF_8)
    );

    assertCustomException(
        () -> adminUserService.syncUsersFromExcel(file),
        ErrorCode.INVALID_FILE_FORMAT
    );
    verify(adminUserSyncService, never()).syncMemberData(anyList());
  }

  @Test
  @DisplayName("압축 시그니처 없는 xlsx 엑셀 파싱 전 거절")
  void syncUsersFromExcel_invalidSignature_throwException() {
    MockMultipartFile file = xlsxFile(
        "members.xlsx",
        "not a zip package".getBytes(StandardCharsets.UTF_8)
    );

    assertCustomException(
        () -> adminUserService.syncUsersFromExcel(file),
        ErrorCode.INVALID_FILE_FORMAT
    );
    verify(adminUserSyncService, never()).syncMemberData(anyList());
  }

  @Test
  @DisplayName("5MB 초과 xlsx 거절")
  void syncUsersFromExcel_oversized_throwException() {
    MockMultipartFile file = xlsxFile(
        "members.xlsx",
        new byte[(int) uploadProperties.getAdminExcelMaxSize().toBytes() + 1]
    );

    assertCustomException(
        () -> adminUserService.syncUsersFromExcel(file),
        ErrorCode.INVALID_FILE_FORMAT
    );
    verify(adminUserSyncService, never()).syncMemberData(anyList());
  }

  @Test
  @DisplayName("유효 데이터 행 1000개 초과 거절")
  void syncUsersFromExcel_tooManyRows_throwException() throws IOException {
    MockMultipartFile file = xlsxFile("members.xlsx", workbookBytes(1001));

    assertCustomException(
        () -> adminUserService.syncUsersFromExcel(file),
        ErrorCode.INVALID_EXCEL_STRUCTURE
    );
    verify(adminUserSyncService, never()).syncMemberData(anyList());
  }

  @Test
  @DisplayName("무효 행 포함 실제 행 수 1000개 초과 선거절")
  void syncUsersFromExcel_tooManyPhysicalRows_throwException() throws IOException {
    MockMultipartFile file = xlsxFile("members.xlsx", workbookBytesWithInvalidStudentIds(1001));

    assertCustomException(
        () -> adminUserService.syncUsersFromExcel(file),
        ErrorCode.INVALID_EXCEL_STRUCTURE
    );
    verify(adminUserSyncService, never()).syncMemberData(anyList());
  }

  private void assertCustomException(Runnable action, ErrorCode errorCode) {
    CustomException exception = assertThrows(CustomException.class, action::run);
    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
  }

  private MockMultipartFile xlsxFile(String filename, byte[] bytes) {
    return new MockMultipartFile(
        "file",
        filename,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes
    );
  }

  private byte[] workbookBytes(int dataRows) throws IOException {
    // 정상 학번 행 생성
    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("members");
      sheet.createRow(0);
      for (int i = 1; i <= dataRows; i++) {
        Row row = sheet.createRow(i);
        row.createCell(1).setCellValue("퀀트팀");
        row.createCell(2).setCellValue("40기");
        row.createCell(3).setCellValue("테스터" + i);
        row.createCell(4).setCellValue(String.valueOf(20260000 + i));
        row.createCell(5).setCellValue("010-1234-5678");
        row.createCell(6).setCellValue("경영대학");
        row.createCell(7).setCellValue("경영학과");
        row.createCell(8).setCellValue("SENIOR");
        row.createCell(9).setCellValue("팀원");
        row.createCell(10).setCellValue("남");
      }
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] workbookBytesWithInvalidStudentIds(int dataRows) throws IOException {
    // 스킵 대상 무효 학번 행도 전체 행 수 제한에 포함
    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("members");
      sheet.createRow(0);
      for (int i = 1; i <= dataRows; i++) {
        Row row = sheet.createRow(i);
        row.createCell(4).setCellValue("invalid-id-" + i);
      }
      workbook.write(output);
      return output.toByteArray();
    }
  }
}

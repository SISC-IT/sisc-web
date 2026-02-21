package org.sejongisc.backend.admin.dto;

import lombok.Builder;

/**
 * 엑셀 파일의 한 행 데이터를 담는 Row
 */
@Builder
public record UserExcelRow(
    String studentId,
    String name,
    String phone,
    String teamName,
    String generation,
    String college,
    String department,
    String grade,
    String position,
    String gender
) {
}

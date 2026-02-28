package org.sejongisc.backend.admin.dto;

/**
 * 엑셀 동기화 결과 응답
 */
public record ExcelSyncResponse(
    int createdCount,
    int updatedCount
) {
}
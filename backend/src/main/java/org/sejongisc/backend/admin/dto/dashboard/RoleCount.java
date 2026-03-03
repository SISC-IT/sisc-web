package org.sejongisc.backend.admin.dto.dashboard;

import org.sejongisc.backend.user.entity.Role;

/**
 * 프로젝션 인터페이스
 * DB에서 Role별 집계 결과를 담기 위함
 */
public interface RoleCount {
    Role getRole();
    long getCount();
}
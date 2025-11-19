package org.sejongisc.backend.common.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
        throws IOException{

        log.warn("권한 없음 접근 시도 {}", accessDeniedException.getMessage());

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);  // 403

        Map<String,Object> body = new HashMap<>();
        body.put("message", "해당 리소스에 접근할 권한이 없습니다.");
        body.put("error", accessDeniedException.getMessage());

        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}

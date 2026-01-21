package org.sejongisc.backend.attendance.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.attendance.repository.AttendanceRoundRepository;
import org.sejongisc.backend.attendance.repository.AttendanceSessionRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AttendanceRoundServiceTest {

    @Mock
    private AttendanceRoundRepository attendanceRoundRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @InjectMocks
    private AttendanceRoundService attendanceRoundService;


}

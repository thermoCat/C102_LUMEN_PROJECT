package com.smartcane.backend;

import com.smartcane.backend.domain.hazard.repository.HazardRepository;
import com.smartcane.backend.global.s3.S3ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SmartCaneBackendApplicationTests {

    @MockitoBean
    private HazardRepository hazardRepository;

    @MockitoBean
    private S3ImageStorageService s3ImageStorageService;

    @Test
    void contextLoads() {
    }
}

package com.flowable.designbackup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DesignBackupApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithDefaultBasicAuthConfiguration() {
        assertThat(context.getBean("designRestClient")).isNotNull();
        assertThat(context.containsBean("backupScheduler")).isFalse();
    }
}

package com.ywz.workflow.featherflow.ops.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class WorkflowViewRepositorySqlCompatibilityTest {

    @Test
    void shouldKeepWorkflowListQueryCompatibleWithMysql57() throws Exception {
        String listSql = readPrivateSql("LIST_SQL").toLowerCase();

        assertThat(listSql).doesNotContain("with ");
        assertThat(listSql).doesNotContain("row_number");
        assertThat(listSql).doesNotContain(" over ");
    }

    private String readPrivateSql(String fieldName) throws Exception {
        Field field = WorkflowViewRepository.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}

package com.ywz.workflow.featherflow.ops.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class WorkflowViewRepositorySqlCompatibilityTest {

    @Test
    void shouldKeepWorkflowListQueryCompatibleWithMysql57() throws Exception {
        String listSql = readPrivateSql("LIST_SQL").toLowerCase();

        assertThat(listSql).doesNotContain("with ");
        assertThat(listSql).doesNotContain("row_number");
        assertThat(listSql).doesNotContain(" over ");
    }

    @Test
    void shouldKeepWorkflowPageQueryFromJoiningActivityTables() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        WorkflowViewRepository repository = new WorkflowViewRepository(jdbcTemplate);

        repository.findWorkflowPageRows(null, null, null, null, null, null, null, null, null, 10, 40, "desc");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(
            sqlCaptor.capture(),
            Mockito.<RowMapper<WorkflowViewRepository.WorkflowListRow>>any(),
            Mockito.<Object[]>any()
        );
        String sql = sqlCaptor.getValue().toLowerCase();
        assertThat(sql).doesNotContain("activity_instance");
        assertThat(sql).doesNotContain("not exists");
    }

    private String readPrivateSql(String fieldName) throws Exception {
        Field field = WorkflowViewRepository.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}

package com.sfaasproject.stf.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UniqueViolationsTest {

    @Test
    void mysqlAndH2DuplicateKeysAreUniqueViolations() {
        assertThat(UniqueViolations.isUniqueViolation(wrap(new SQLException("Duplicate entry", "23000", 1062))))
                .isTrue();
        assertThat(UniqueViolations.isUniqueViolation(wrap(new SQLException("Unique index", "23505", 23505))))
                .isTrue();
    }

    @Test
    void tooLongValuesAndOtherConstraintsAreNot() {
        assertThat(UniqueViolations.isUniqueViolation(wrap(new SQLException("Data too long", "22001", 1406))))
                .isFalse();
        assertThat(UniqueViolations.isUniqueViolation(wrap(new SQLException("FK fails", "23000", 1452))))
                .isFalse();
        assertThat(UniqueViolations.isUniqueViolation(new DataIntegrityViolationException("no cause"))).isFalse();
    }

    private static DataIntegrityViolationException wrap(SQLException sql) {
        return new DataIntegrityViolationException("wrapped", new RuntimeException("hibernate", sql));
    }
}

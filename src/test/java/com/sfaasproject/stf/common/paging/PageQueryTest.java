package com.sfaasproject.stf.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sfaasproject.stf.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageQueryTest {

    @Test
    void missing_values_use_page_0_and_size_20() {
        PageQuery query = PageQuery.of(null, null);

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void the_contract_limits_are_accepted() {
        assertThat(PageQuery.of(0, 1).size()).isEqualTo(1);
        assertThat(PageQuery.of(7, 100).size()).isEqualTo(100);
    }

    @Test
    void values_outside_the_contract_are_rejected_instead_of_being_fixed_silently() {
        for (int[] invalid : new int[][] {{-1, 20}, {0, 0}, {0, -1}, {0, 101}}) {
            assertThatThrownBy(() -> PageQuery.of(invalid[0], invalid[1]))
                    .isInstanceOfSatisfying(DomainException.class, ex -> {
                        assertThat(ex.status().value()).isEqualTo(400);
                        assertThat(ex.code()).isEqualTo("VALIDATION_ERROR");
                    });
        }
    }

    @Test
    void it_converts_to_a_pageable_with_the_given_sort() {
        var pageable = PageQuery.of(2, 10).toPageable(Sort.by("id"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by("id"));
    }
}

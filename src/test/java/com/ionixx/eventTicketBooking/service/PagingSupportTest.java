package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PagingSupportTest {

    private static final Map<String, String> PROPERTIES = Map.of(
            "id", "id",
            "seats", "seats",
            "bookedOn", "bookedOn",
            "booked_on", "bookedOn");

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("bookedOn"),
            Sort.Order.desc("id"));

    private static final Sort.Order TIE_BREAKER = Sort.Order.desc("id");

    private static Pageable page(String sort) {
        return PagingSupport.pageRequest(0, 10, sort, PROPERTIES, DEFAULT_SORT, TIE_BREAKER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ",", " , ,"})
    void missingOrEmptySortUsesTheDefault(String sort) {
        assertThat(page(sort).getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    void propertyWithoutDirectionIsAscendingAndGetsTheTieBreaker() {
        assertThat(page("seats").getSort())
                .isEqualTo(Sort.by(Sort.Order.asc("seats"), Sort.Order.desc("id")));
    }

    @Test
    void directionIsCaseInsensitiveAndSnakeCaseAliasesAreResolved() {
        assertThat(page("booked_on,DESC").getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("bookedOn"), Sort.Order.desc("id")));
    }

    @Test
    void severalSortParametersAreCombinedInOrder() {
        // "sort=seats,desc&sort=bookedOn" arrives as "seats,desc,bookedOn".
        assertThat(page("seats,desc,bookedOn").getSort())
                .isEqualTo(Sort.by(
                        Sort.Order.desc("seats"),
                        Sort.Order.asc("bookedOn"),
                        Sort.Order.desc("id")));
    }

    @Test
    void twoPropertiesWithoutDirectionsAreBothAscending() {
        assertThat(page(" seats , bookedOn , asc ").getSort())
                .isEqualTo(Sort.by(
                        Sort.Order.asc("seats"),
                        Sort.Order.asc("bookedOn"),
                        Sort.Order.desc("id")));
    }

    @Test
    void tieBreakerIsNotAddedWhenTheClientAlreadySortsByIt() {
        assertThat(page("id,asc").getSort()).isEqualTo(Sort.by(Sort.Order.asc("id")));
    }

    @Test
    void unknownPropertyIsRejectedWithTheAllowedList() {
        assertThatThrownBy(() -> page("password,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported sort property 'password'. "
                        + "Allowed: bookedOn, booked_on, id, seats");
    }

    @ParameterizedTest
    @ValueSource(strings = {"desc", "asc,seats", "seats,asc,desc"})
    void directionWithoutPropertyIsRejected(String sort) {
        assertThatThrownBy(() -> page(sort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("must follow a property name");
    }

    @Test
    void pageNumberMustNotBeNegative() {
        assertThatThrownBy(() -> PagingSupport.pageRequest(
                -1, 10, null, PROPERTIES, DEFAULT_SORT, TIE_BREAKER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page number must be zero or greater");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 101})
    void pageSizeMustBeBetweenOneAndOneHundred(int size) {
        assertThatThrownBy(() -> PagingSupport.pageRequest(
                0, size, null, PROPERTIES, DEFAULT_SORT, TIE_BREAKER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must be between 1 and 100");
    }

    @Test
    void boundaryValuesAreAccepted() {
        Pageable pageable = PagingSupport.pageRequest(
                3, PagingSupport.MAX_PAGE_SIZE, null, PROPERTIES, DEFAULT_SORT, TIE_BREAKER);

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(PagingSupport.pageRequest(
                0, 1, null, PROPERTIES, DEFAULT_SORT, TIE_BREAKER).getPageSize()).isEqualTo(1);
    }
}

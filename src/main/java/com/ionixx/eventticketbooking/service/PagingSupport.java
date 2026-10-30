package com.ionixx.eventticketbooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds validated {@link Pageable} objects for the list endpoints.
 *
 * The sort parameter uses the Spring Data style, for example
 * {@code sort=bookedOn,desc} or {@code sort=seats,desc&sort=id,asc}.
 * Spring binds several sort parameters into one comma-separated String,
 * so both forms arrive here as "seats,desc,id,asc".
 *
 * Only whitelisted properties may be used. This keeps clients from sorting
 * by internal columns (for example the owner id) and turns a typo into a
 * clear HTTP 400 instead of an HTTP 500 from the query layer.
 */
public final class PagingSupport {

    public static final int MAX_PAGE_SIZE = 100;

    private PagingSupport() {
    }

    /**
     * @param page               zero-based page number
     * @param size               page size, 1 to {@value #MAX_PAGE_SIZE}
     * @param sort               raw sort parameter, may be null
     * @param sortableProperties API name -> entity property
     * @param defaultSort        used when no sort is requested
     * @param tieBreaker         appended unless the caller already sorts by it,
     *                           so rows with equal values keep a stable order
     *                           across pages
     */
    public static Pageable pageRequest(
            int page,
            int size,
            String sort,
            Map<String, String> sortableProperties,
            Sort defaultSort,
            Sort.Order tieBreaker) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number must be zero or greater");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Sort requested = parseSort(sort, sortableProperties, defaultSort);

        return PageRequest.of(page, size, withTieBreaker(requested, tieBreaker));
    }

    static Sort parseSort(
            String sort,
            Map<String, String> sortableProperties,
            Sort defaultSort) {

        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }

        List<Sort.Order> orders = new ArrayList<>();
        String pendingProperty = null;

        for (String rawToken : sort.split(",")) {

            String token = rawToken.trim();

            if (token.isEmpty()) {
                continue;
            }

            Sort.Direction direction = parseDirection(token);

            if (direction != null) {
                if (pendingProperty == null) {
                    throw new IllegalArgumentException(
                            "Sort direction '" + token
                                    + "' must follow a property name");
                }

                orders.add(Sort.Order.by(pendingProperty).with(direction));
                pendingProperty = null;
                continue;
            }

            // A property without a direction is sorted ascending.
            if (pendingProperty != null) {
                orders.add(Sort.Order.asc(pendingProperty));
            }

            pendingProperty = resolveProperty(token, sortableProperties);
        }

        if (pendingProperty != null) {
            orders.add(Sort.Order.asc(pendingProperty));
        }

        return orders.isEmpty() ? defaultSort : Sort.by(orders);
    }

    private static Sort.Direction parseDirection(String token) {

        String lower = token.toLowerCase(Locale.ROOT);

        if ("asc".equals(lower)) {
            return Sort.Direction.ASC;
        }

        if ("desc".equals(lower)) {
            return Sort.Direction.DESC;
        }

        return null;
    }

    private static String resolveProperty(
            String token,
            Map<String, String> sortableProperties) {

        String property = sortableProperties.get(token);

        if (property == null) {
            throw new IllegalArgumentException(
                    "Unsupported sort property '" + token + "'. Allowed: "
                            + String.join(", ",
                            new TreeSet<>(sortableProperties.keySet())));
        }

        return property;
    }

    private static Sort withTieBreaker(Sort sort, Sort.Order tieBreaker) {

        if (sort.getOrderFor(tieBreaker.getProperty()) != null) {
            return sort;
        }

        return sort.and(Sort.by(tieBreaker));
    }
}

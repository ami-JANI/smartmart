package com.smartmart.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.smartmart.inventory.service.AlertService;

class AlertModelAdviceTest {

    private final AlertService alertService = mock(AlertService.class);
    private final AlertModelAdvice advice = new AlertModelAdvice(alertService);

    @Test
    void addsCountForInventoryStaffOnGetRequest() {
        when(alertService.openAlertCount()).thenReturn(5L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        Authentication auth = new TestingAuthenticationToken("staff", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_STAFF")));

        assertThat(advice.openAlertCount(request, auth)).isEqualTo(5L);
    }

    @Test
    void addsCountForManagerOnGetRequest() {
        when(alertService.openAlertCount()).thenReturn(3L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        Authentication auth = new TestingAuthenticationToken("mgr", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

        assertThat(advice.openAlertCount(request, auth)).isEqualTo(3L);
    }

    @Test
    void returnsNullOnPostRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
        Authentication auth = new TestingAuthenticationToken("staff", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_STAFF")));

        assertThat(advice.openAlertCount(request, auth)).isNull();
        verify(alertService, never()).openAlertCount();
    }

    @Test
    void returnsNullForCashier() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pos");
        Authentication auth = new TestingAuthenticationToken("cashier", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));

        assertThat(advice.openAlertCount(request, auth)).isNull();
    }

    @Test
    void returnsNullWhenUnauthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        assertThat(advice.openAlertCount(request, null)).isNull();
    }
}

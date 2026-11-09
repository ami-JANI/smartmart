package com.smartmart.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.smartmart.inventory.service.AlertService;

class AlertControllerTest {

    private final AlertService alertService = mock(AlertService.class);
    private final AlertController controller = new AlertController(alertService);

    @Test
    void listPopulatesAlertsAndActiveNav() {
        when(alertService.openAlerts()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.list(model);

        assertThat(view).isEqualTo("inventory/alerts");
        assertThat(model.getAttribute("alerts")).isEqualTo(List.of());
        assertThat(model.getAttribute("activeNav")).isEqualTo("alerts");
    }
}

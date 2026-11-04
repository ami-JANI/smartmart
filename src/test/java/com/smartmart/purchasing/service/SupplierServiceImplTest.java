package com.smartmart.purchasing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.purchasing.domain.Supplier;
import com.smartmart.purchasing.repository.SupplierRepository;

class SupplierServiceImplTest {

    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final SupplierServiceImpl service = new SupplierServiceImpl(suppliers);

    private SupplierData data(String name) {
        return new SupplierData(name, "  ", " 01700000000 ", "", " Dhaka ", true);
    }

    private Supplier stored(long id) {
        Supplier supplier = new Supplier("Acme", "Rahim", "01700000000", "a@example.com", null, true);
        ReflectionTestUtils.setField(supplier, "id", id);
        when(suppliers.findById(id)).thenReturn(Optional.of(supplier));
        return supplier;
    }

    @Test
    void createCleansTheValuesAndReturnsTheId() {
        Supplier saved = stored(5L);
        when(suppliers.saveAndFlush(any())).thenReturn(saved);

        assertThat(service.create(data(" Acme Foods "))).isEqualTo(5L);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(suppliers).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Acme Foods");
        assertThat(captor.getValue().getPhone()).isEqualTo("01700000000");
        assertThat(captor.getValue().getContactPerson()).isNull();
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getAddress()).isEqualTo("Dhaka");
        verify(suppliers).existsByName("Acme Foods");
    }

    @Test
    void createRefusesATakenName() {
        when(suppliers.existsByName("Acme")).thenReturn(true);

        assertThatThrownBy(() -> service.create(data("Acme")))
                .isInstanceOf(SupplierNameTakenException.class)
                .hasMessage("Supplier name already in use");
        verify(suppliers, never()).saveAndFlush(any());
    }

    @Test
    void nameTakenAtTheSameMomentIsRefusedToo() {
        when(suppliers.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(data("Acme"))).isInstanceOf(SupplierNameTakenException.class);
    }

    @Test
    void updateChangesTheFieldsAndAllowsItsOwnName() {
        Supplier supplier = stored(5L);

        service.update(5L, new SupplierData("Acme", null, "01800000000", null, null, false));

        assertThat(supplier.getPhone()).isEqualTo("01800000000");
        assertThat(supplier.isActive()).isFalse();
        verify(suppliers).existsByNameAndIdNot("Acme", 5L);
        verify(suppliers).saveAndFlush(supplier);
    }

    @Test
    void updateRefusesTheNameOfAnotherSupplier() {
        stored(5L);
        when(suppliers.existsByNameAndIdNot("Other", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, data("Other"))).isInstanceOf(SupplierNameTakenException.class);
    }

    @Test
    void missingSupplierIsNotFound() {
        when(suppliers.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(SupplierNotFoundException.class);
        assertThatThrownBy(() -> service.update(404L, data("Acme"))).isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void getReturnsTheSummary() {
        stored(5L);

        assertThat(service.get(5L))
                .isEqualTo(new SupplierSummary(5L, "Acme", "Rahim", "01700000000", "a@example.com", null, true));
    }
}

package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.pos.domain.Customer;
import com.smartmart.pos.repository.CustomerRepository;

class CustomerServiceImplTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerServiceImpl(customers);

    @Test
    void registersAMemberWithTheCleanedPhoneNumber() {
        when(customers.save(any(Customer.class))).thenAnswer(call -> {
            Customer saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        Long id = service.register("  Farhana Akter ", "01711-000 101");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(id).isEqualTo(9L);
        assertThat(saved.getValue().getName()).isEqualTo("Farhana Akter");
        assertThat(saved.getValue().getPhone()).isEqualTo("01711000101");
    }

    @Test
    void phoneNumberBoundaries() {
        assertThatThrownBy(() -> service.register("A", "12345"))
                .isInstanceOfSatisfying(CustomerRuleException.class, e -> assertThat(e.field()).isEqualTo("phone"));
        assertThatThrownBy(() -> service.register("A", "1".repeat(16))).isInstanceOf(CustomerRuleException.class);
        assertThatThrownBy(() -> service.register("A", "phone")).isInstanceOf(CustomerRuleException.class);
        when(customers.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        service.register("A", "123456");
        service.register("B", "+880" + "1".repeat(11));
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> service.register("  ", "01711000101"))
                .isInstanceOfSatisfying(CustomerRuleException.class, e -> {
                    assertThat(e.field()).isEqualTo("name");
                    assertThat(e).hasMessage("Name is required");
                });
    }

    @Test
    void aPhoneNumberCanBelongToOnlyOneMember() {
        when(customers.existsByPhone("01711000101")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Someone", "01711 000101"))
                .hasMessage("This phone number is already a member");
        verify(customers, never()).save(any());
    }

    @Test
    void phoneNumbersAreStoredAsDigits() {
        assertThat(CustomerService.normalisePhone(" 01711-000 101 ")).isEqualTo("01711000101");
        assertThat(CustomerService.normalisePhone("+880 1711 000101")).isEqualTo("+8801711000101");
        assertThat(CustomerService.normalisePhone(null)).isEmpty();
    }
}

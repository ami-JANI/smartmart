package com.smartmart.pos.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.SessionScope;

import com.smartmart.pos.domain.Cart;
import com.smartmart.pos.domain.CartHistory;

/** Each logged-in cashier has one cart and its undo history, kept in their session until the sale completes. */
@Configuration
class CartConfig {

    @Bean
    @SessionScope
    Cart cart() {
        return new Cart();
    }

    @Bean
    @SessionScope
    CartHistory cartHistory() {
        return new CartHistory();
    }
}

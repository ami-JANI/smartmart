package com.smartmart.pos.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.SessionScope;

import com.smartmart.pos.domain.Cart;

/** Each logged-in cashier has one cart, kept in their session until the sale completes. */
@Configuration
class CartConfig {

    @Bean
    @SessionScope
    Cart cart() {
        return new Cart();
    }
}

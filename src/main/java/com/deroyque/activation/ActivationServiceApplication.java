package com.deroyque.activation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação.
 *
 * <p>O {@code @EnableScheduling} habilita o processador em background que consome
 * o outbox (envio ao provedor externo) e o job de reconciliação de solicitações
 * presas em PROCESSING.
 */
@SpringBootApplication
@EnableScheduling
public class ActivationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivationServiceApplication.class, args);
    }
}

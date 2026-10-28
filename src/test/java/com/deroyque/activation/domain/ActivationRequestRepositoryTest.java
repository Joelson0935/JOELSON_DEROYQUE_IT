package com.deroyque.activation.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ActivationRequestRepositoryTest {

    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void naoPermiteDoisRegistrosComMesmoExternalId() {
        repository.saveAndFlush(ActivationRequest.create("EXT-DUP", "CUST-1", "P1", "cliente-a"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(ActivationRequest.create("EXT-DUP", "CUST-2", "P2", "cliente-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscaPorExternalId() {
        repository.saveAndFlush(ActivationRequest.create("EXT-FIND", "CUST-1", "P1", "cliente-a"));

        var found = repository.findByExternalId("EXT-FIND");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("CUST-1");
    }
}

package com.deroyque.activation.service;

import com.deroyque.activation.AbstractPostgresIT;
import com.deroyque.activation.api.dto.CreateActivationRequest;
import com.deroyque.activation.domain.ActivationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que várias requisições simultâneas com o mesmo externalId resultam em exatamente um
 * registro, rodando contra o PostgreSQL real (Testcontainers). Este é o cenário que a unique
 * constraint protege e que o H2 não reproduz de forma fiel.
 */
class ConcurrentDuplicateIT extends AbstractPostgresIT {

    @Autowired
    private ActivationService service;

    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void requisicoesConcorrentesGeramExatamenteUmRegistro() throws Exception {
        int threads = 16;
        var barrier = new CyclicBarrier(threads);
        var pool = Executors.newFixedThreadPool(threads);
        var command = new CreateActivationRequest("EXT-CONC", "CUST-1", "INTERNET-500");

        var created = new AtomicInteger();
        var existing = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await(); // garante largada simultânea
                var result = service.create(command, "cliente-a");
                if (result.created()) {
                    created.incrementAndGet();
                } else {
                    existing.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get(); // propaga eventuais exceções
        }
        pool.shutdown();

        // Exatamente um registro no banco.
        assertThat(repository.findByExternalId("EXT-CONC")).isPresent();
        assertThat(repository.findAll().stream()
                .filter(r -> r.getExternalId().equals("EXT-CONC")).count()).isEqualTo(1);

        // Exatamente uma criação; as demais foram tratadas como retry idempotente.
        assertThat(created.get()).isEqualTo(1);
        assertThat(existing.get()).isEqualTo(threads - 1);
    }
}

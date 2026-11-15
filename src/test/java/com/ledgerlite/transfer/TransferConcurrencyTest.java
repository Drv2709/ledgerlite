package com.ledgerlite.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerlite.common.exception.InsufficientFundsException;
import com.ledgerlite.idempotency.IdempotentResponse;
import com.ledgerlite.ledger.LedgerService;
import com.ledgerlite.support.AbstractIntegrationTest;
import com.ledgerlite.transfer.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real threads, real PostgreSQL, real row locks. All threads are released at the same instant
 * by a latch so they genuinely collide. A deadlock or a lost update would fail these tests.
 */
class TransferConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private TransferService transferService;
    @Autowired private LedgerService ledgerService;

    @Test
    @Timeout(90)
    void tenParallelTransfersCannotOverdrawAFiftyDollarAccount() throws Exception {
        TestUser payer = newUser("payer");
        TestUser payee = newUser("payee");
        JsonNode source = createAccount(payer, "Source", "USD", "50.00");
        JsonNode destination = createAccount(payee, "Destination", "USD", null);
        UUID sourceId = UUID.fromString(source.get("id").asText());
        UUID destinationId = UUID.fromString(destination.get("id").asText());
        String destinationNumber = destination.get("accountNumber").asText();

        List<Boolean> accepted = this.<Boolean>runAtOnce(10, i -> () -> {
            try {
                transferService.transfer(payer.id(), "race-" + UUID.randomUUID(),
                        new TransferRequest(sourceId, destinationNumber, new BigDecimal("10.00"), null));
                return true;
            } catch (InsufficientFundsException rejected) {
                return false;
            }
        });

        long succeeded = accepted.stream().filter(ok -> ok).count();
        assertThat(succeeded).as("transfers that succeeded").isEqualTo(5);
        assertThat(accepted.size() - succeeded).as("transfers rejected for insufficient funds").isEqualTo(5);
        assertThat(ledgerService.balanceOf(sourceId)).as("source balance").isEqualByComparingTo("0");
        assertThat(ledgerService.balanceOf(destinationId)).as("destination balance").isEqualByComparingTo("50");
    }

    @Test
    @Timeout(90)
    void transfersInOppositeDirectionsDoNotDeadlockAndConserveMoney() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode aliceAccount = createAccount(alice, "A", "USD", "100.00");
        JsonNode bobAccount = createAccount(bob, "B", "USD", "100.00");
        UUID aliceId = UUID.fromString(aliceAccount.get("id").asText());
        UUID bobId = UUID.fromString(bobAccount.get("id").asText());
        String aliceNumber = aliceAccount.get("accountNumber").asText();
        String bobNumber = bobAccount.get("accountNumber").asText();

        // Even threads send alice -> bob, odd threads send bob -> alice. Funds always suffice,
        // so ANY exception (including a Postgres deadlock abort) makes this test fail.
        List<Integer> done = this.<Integer>runAtOnce(10, i -> () -> {
            if (i % 2 == 0) {
                transferService.transfer(alice.id(), "ab-" + UUID.randomUUID(),
                        new TransferRequest(aliceId, bobNumber, new BigDecimal("10.00"), null));
            } else {
                transferService.transfer(bob.id(), "ba-" + UUID.randomUUID(),
                        new TransferRequest(bobId, aliceNumber, new BigDecimal("10.00"), null));
            }
            return 1;
        });

        assertThat(done).hasSize(10);
        BigDecimal aliceBalance = ledgerService.balanceOf(aliceId);
        BigDecimal bobBalance = ledgerService.balanceOf(bobId);
        assertThat(aliceBalance).isEqualByComparingTo("100");
        assertThat(bobBalance).isEqualByComparingTo("100");
        assertThat(aliceBalance.add(bobBalance)).as("money is conserved").isEqualByComparingTo("200");
    }

    @Test
    @Timeout(90)
    void fiveThreadsUsingOneIdempotencyKeyMoveMoneyExactlyOnce() throws Exception {
        TestUser payer = newUser("payer");
        TestUser payee = newUser("payee");
        JsonNode source = createAccount(payer, "Source", "USD", "100.00");
        JsonNode destination = createAccount(payee, "Destination", "USD", null);
        UUID sourceId = UUID.fromString(source.get("id").asText());
        UUID destinationId = UUID.fromString(destination.get("id").asText());
        String destinationNumber = destination.get("accountNumber").asText();
        String sharedKey = "same-" + UUID.randomUUID();

        List<IdempotentResponse> responses = this.<IdempotentResponse>runAtOnce(5, i -> () ->
                transferService.transfer(payer.id(), sharedKey,
                        new TransferRequest(sourceId, destinationNumber, new BigDecimal("10.00"), null)));

        assertThat(responses).hasSize(5);
        assertThat(responses).allMatch(r -> r.status() == 201);
        assertThat(responses.stream().map(IdempotentResponse::body).distinct()).as("identical bodies").hasSize(1);
        assertThat(responses.stream().filter(IdempotentResponse::replayed).count()).as("replays").isEqualTo(4);
        assertThat(ledgerService.balanceOf(sourceId)).as("debited once").isEqualByComparingTo("90");
        assertThat(ledgerService.balanceOf(destinationId)).as("credited once").isEqualByComparingTo("10");
    }

    /** Starts {@code count} tasks, releases them all at the same moment, and returns their results in order. */
    private <T> List<T> runAtOnce(int count, IntFunction<Callable<T>> taskFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> work = taskFactory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return work.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("all threads started").isTrue();
            go.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));   // rethrows any thread's exception
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

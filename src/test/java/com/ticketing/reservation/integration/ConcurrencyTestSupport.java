package com.ticketing.reservation.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class ConcurrencyTestSupport {

  private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);
  private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(10);

  private ConcurrencyTestSupport() {}

  public static <T> List<T> runConcurrently(int threadCount, Callable<T> task) throws Exception {
    return runConcurrently(Collections.nCopies(threadCount, task));
  }

  public static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
    CyclicBarrier start = new CyclicBarrier(tasks.size());

    try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  start.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                  return task.call();
                }));
      }

      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS));
      }
      return results;
    }
  }

  public static <T> T runWhileOtherTransactionHoldsLock(
      PlatformTransactionManager transactionManager, Runnable lock, Supplier<T> action)
      throws Exception {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> lockHolder =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        lock.run();
                        locked.countDown();
                        awaitRelease(release);
                      }));

      T result;
      try {
        if (!locked.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
          if (lockHolder.isDone()) {
            lockHolder.get(); // 락 획득 스레드에서 발생한 예외를 호출 스레드로 전파한다
          }
          throw new IllegalStateException("락 획득 대기 시간 초과");
        }
        result = action.get();
      } finally {
        release.countDown();
      }
      lockHolder.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
      return result;
    }
  }

  public static void runWhileOtherTransactionHoldsLock(
      PlatformTransactionManager transactionManager, Runnable lock, Runnable action)
      throws Exception {
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        lock,
        () -> {
          action.run();
          return null;
        });
  }

  private static void awaitRelease(CountDownLatch latch) {
    try {
      if (!latch.await(LOCK_HOLD_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("락 점유 제한 시간 초과");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("락 점유 스레드가 중단되었습니다", e);
    }
  }
}

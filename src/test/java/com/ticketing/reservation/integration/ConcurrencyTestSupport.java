package com.ticketing.reservation.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
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
        awaitLocked(locked, lockHolder);
        result = action.get();
      } finally {
        release.countDown();
      }
      lockHolder.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
      return result;
    }
  }

  public static <T> T runBlockedByOtherTransactionLock(
      PlatformTransactionManager transactionManager,
      JdbcTemplate jdbcTemplate,
      Runnable lock,
      Callable<T> action)
      throws Exception {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicLong lockHolderThreadId = new AtomicLong();

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> lockHolder =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        lock.run();
                        lockHolderThreadId.set(
                            Objects.requireNonNull(
                                jdbcTemplate.queryForObject(
                                    "select ps_current_thread_id()", Long.class)));
                        locked.countDown();
                        awaitRelease(release);
                      }));

      Future<T> waiter;
      try {
        awaitLocked(locked, lockHolder);
        waiter = executor.submit(action);
        awaitLockWait(jdbcTemplate, lockHolderThreadId.get(), waiter);
      } finally {
        release.countDown();
      }
      lockHolder.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
      return waiter.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private static void awaitLocked(CountDownLatch locked, Future<?> lockHolder) throws Exception {
    if (!locked.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
      if (lockHolder.isDone()) {
        lockHolder.get();
      }
      throw new IllegalStateException("락 획득 대기 시간 초과");
    }
  }

  private static void awaitLockWait(
      JdbcTemplate jdbcTemplate, long lockHolderThreadId, Future<?> waiter) throws Exception {
    long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
    while (System.nanoTime() < deadline) {
      int waiting =
          Objects.requireNonNull(
              jdbcTemplate.queryForObject(
                  "select count(*) from performance_schema.data_lock_waits"
                      + " where blocking_thread_id = ?",
                  Integer.class,
                  lockHolderThreadId));
      if (waiting > 0) {
        return;
      }
      if (waiter.isDone()) {
        waiter.get();
        throw new IllegalStateException("action이 락을 기다리지 않고 끝났습니다");
      }
      Thread.sleep(10);
    }
    throw new IllegalStateException("action의 락 대기 확인 시간 초과");
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

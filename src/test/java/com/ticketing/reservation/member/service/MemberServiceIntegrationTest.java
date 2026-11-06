package com.ticketing.reservation.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.TestcontainersConfiguration;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.domain.MemberRole;
import com.ticketing.reservation.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberServiceIntegrationTest {

  @Autowired private MemberService memberService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @AfterEach
  void tearDown() {
    memberRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("회원 가입하면 정규화된 이메일과 해시된 비밀번호를 저장한다")
  void savesNormalizedEmailAndEncodedPassword() {
    // given
    String email = "  User@Example.COM ";

    // when
    Member member = memberService.signUp(email, "password");

    // then
    Member saved = memberRepository.findById(member.getId()).orElseThrow();
    assertThat(saved.getEmail()).isEqualTo("user@example.com");
    assertThat(passwordEncoder.matches("password", saved.getPassword())).isTrue();
    assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
  }

  @Test
  @DisplayName("정규화된 이메일이 같으면 가입할 수 없다")
  void rejectsSignUpWhenNormalizedEmailIsDuplicate() {
    // given
    memberService.signUp("user@example.com", "password");

    // when
    Throwable thrown = catchThrowable(() -> memberService.signUp(" USER@example.com ", "password"));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    assertThat(memberRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("정규화된 이메일이 같으면 사용할 수 없는 이메일로 판단한다")
  void checksAvailabilityUsingNormalizedEmail() {
    // given
    memberService.signUp("user@example.com", "password");

    // when
    boolean sameNormalizedEmailAvailable = memberService.isEmailAvailable(" User@Example.com ");
    boolean otherEmailAvailable = memberService.isEmailAvailable("other@example.com");

    // then
    assertThat(sameNormalizedEmailAvailable).isFalse();
    assertThat(otherEmailAvailable).isTrue();
  }

  @Test
  @DisplayName("같은 이메일로 동시에 가입하면 회원은 하나만 생성된다")
  void createsOnlyOneMemberForConcurrentSignUpsWithSameEmail() throws Exception {
    // given
    int threadCount = 10;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    // 가입에 성공하면 true, 중복으로 거절되면 false. 그 외 예외는 result.get()에서 테스트 실패로 드러난다.
    List<Future<Boolean>> results = new ArrayList<>();

    // when
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    memberService.signUp("user@example.com", "password");
                    return true;
                  } catch (BusinessException e) {
                    if (e.getErrorCode() != ErrorCode.DUPLICATE_EMAIL) {
                      throw e;
                    }
                    return false;
                  }
                }));
      }
      ready.await();
      start.countDown();
    }

    // then
    int successCount = 0;
    int duplicateCount = 0;
    for (Future<Boolean> result : results) {
      if (Boolean.TRUE.equals(result.get())) {
        successCount++;
      } else {
        duplicateCount++;
      }
    }

    assertThat(successCount).isEqualTo(1);
    assertThat(duplicateCount).isEqualTo(threadCount - 1);
    assertThat(memberRepository.count()).isEqualTo(1);
  }
}

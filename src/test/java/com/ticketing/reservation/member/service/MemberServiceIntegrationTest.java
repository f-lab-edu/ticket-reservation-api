package com.ticketing.reservation.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
  void 회원_가입하면_정규화된_이메일과_해시된_비밀번호를_저장한다() {
    Member member = memberService.signUp("  User@Example.COM ", "password");

    Member saved = memberRepository.findById(member.getId()).orElseThrow();
    assertThat(saved.getEmail()).isEqualTo("user@example.com");
    assertThat(passwordEncoder.matches("password", saved.getPassword())).isTrue();
    assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
  }

  @Test
  void 정규화된_이메일이_같으면_가입할_수_없다() {
    memberService.signUp("user@example.com", "password");

    assertThatExceptionOfType(BusinessException.class)
        .isThrownBy(() -> memberService.signUp(" USER@example.com ", "password"))
        .extracting(BusinessException::getErrorCode)
        .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    assertThat(memberRepository.count()).isEqualTo(1);
  }

  @Test
  void 이메일_중복_확인은_정규화된_이메일로_판단한다() {
    memberService.signUp("user@example.com", "password");

    assertThat(memberService.isEmailAvailable(" User@Example.com ")).isFalse();
    assertThat(memberService.isEmailAvailable("other@example.com")).isTrue();
  }

  @Test
  void 같은_이메일로_동시에_가입해도_회원은_하나만_생성된다() throws Exception {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);

    // 가입에 성공하면 true, 중복으로 거절되면 false. 그 외 예외는 result.get()에서 테스트 실패로 드러난다.
    List<Future<Boolean>> results = new ArrayList<>();
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

    int successCount = 0;
    int duplicateCount = 0;
    for (Future<Boolean> result : results) {
      if (Boolean.TRUE.equals(result.get())) {
        successCount++;
      } else {
        duplicateCount++;
      }
    }
    executor.shutdown();

    assertThat(successCount).isEqualTo(1);
    assertThat(duplicateCount).isEqualTo(threadCount - 1);
    assertThat(memberRepository.count()).isEqualTo(1);
  }
}

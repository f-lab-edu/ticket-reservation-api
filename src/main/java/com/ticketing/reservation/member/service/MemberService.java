package com.ticketing.reservation.member.service;

import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.email.EmailNormalizer;
import com.ticketing.reservation.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final MemberRegistrar memberRegistrar;

  public MemberService(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      MemberRegistrar memberRegistrar) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.memberRegistrar = memberRegistrar;
  }

  @Transactional(readOnly = true)
  public boolean isEmailAvailable(String email) {
    return !memberRepository.existsByEmail(EmailNormalizer.normalize(email));
  }

  public Member signUp(String email, String rawPassword) {
    String normalizedEmail = EmailNormalizer.normalize(email);
    String encodedPassword = passwordEncoder.encode(rawPassword);

    return memberRegistrar.register(normalizedEmail, encodedPassword);
  }
}

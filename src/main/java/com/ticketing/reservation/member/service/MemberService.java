package com.ticketing.reservation.member.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.email.EmailNormalizer;
import com.ticketing.reservation.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;

  public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public boolean isEmailAvailable(String email) {
    return !memberRepository.existsByEmail(EmailNormalizer.normalize(email));
  }

  @Transactional
  public Member signUp(String email, String rawPassword) {
    String normalizedEmail = EmailNormalizer.normalize(email);
    if (memberRepository.existsByEmail(normalizedEmail)) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }

    Member member = Member.create(normalizedEmail, passwordEncoder.encode(rawPassword));
    try {
      return memberRepository.saveAndFlush(member);
    } catch (DataIntegrityViolationException e) {
      if (isEmailUniqueViolation(e)) {
        throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, e);
      }
      throw e;
    }
  }

  private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cause
        && cause.getConstraintName() != null
        && cause.getConstraintName().endsWith(Member.EMAIL_UNIQUE_CONSTRAINT);
  }
}

package com.ticketing.reservation.member.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberRegistrar {

  private final MemberRepository memberRepository;

  public MemberRegistrar(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Transactional
  public Member register(String normalizedEmail, String encodedPassword) {
    if (memberRepository.existsByEmail(normalizedEmail)) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }

    Member member = Member.create(normalizedEmail, encodedPassword);
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

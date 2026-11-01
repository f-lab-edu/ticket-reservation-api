package com.ticketing.reservation.member.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Member {

  public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String email;

  private String password;

  @Enumerated(EnumType.STRING)
  private MemberRole role;

  protected Member() {}

  private Member(String email, String password, MemberRole role) {
    this.email = email;
    this.password = password;
    this.role = role;
  }

  public static Member create(String normalizedEmail, String encodedPassword) {
    return new Member(normalizedEmail, encodedPassword, MemberRole.USER);
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public MemberRole getRole() {
    return role;
  }
}

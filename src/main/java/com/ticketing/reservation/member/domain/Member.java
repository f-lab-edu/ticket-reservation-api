package com.ticketing.reservation.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(name = Member.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
public class Member {

  public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
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

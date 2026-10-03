package com.kiri.server.domain.member.service;

import com.kiri.server.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberCreator {

    private final MemberRepository memberRepository;
}

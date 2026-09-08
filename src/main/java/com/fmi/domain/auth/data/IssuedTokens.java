package com.fmi.domain.auth.data;

import java.util.Date;

public record IssuedTokens(String accessToken, Date accessExpiration, String refreshToken, Date refreshExpiration) {}

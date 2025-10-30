package com.learning.authservice.service;

import com.learning.authservice.dto.AuthRequestDto;
import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.dto.SignupRequestDto;
import com.learning.authservice.dto.UserInfoDto;

public interface AuthService {
    UserInfoDto getCurrentUser();

    AuthResponseDto login(AuthRequestDto request);

    AuthResponseDto signup(SignupRequestDto request);

    void logout();
}


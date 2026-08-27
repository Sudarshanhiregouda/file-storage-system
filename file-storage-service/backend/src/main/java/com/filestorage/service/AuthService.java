package com.filestorage.service;

import com.filestorage.dto.AuthResponseDTO;
import com.filestorage.dto.LoginRequestDTO;
import com.filestorage.dto.RegisterRequestDTO;

public interface AuthService {

    AuthResponseDTO register(RegisterRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);
}
